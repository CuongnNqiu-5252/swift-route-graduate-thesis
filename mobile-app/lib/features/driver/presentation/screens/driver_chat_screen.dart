import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:swiftroute_app/core/network/dio.dart';

class _ChatContact {
  const _ChatContact(this.id, this.name);

  final String id;
  final String name;
}

class _ChatMessage {
  _ChatMessage(Map<String, dynamic> data)
    : id = data['id'] as int,
      sender = data['sender_id'] as String,
      recipient = data['recipient_id'] as String,
      clientId = data['client_message_id'] as String,
      body = data['body'] as String?,
      hasImage = data['has_image'] as bool,
      createdAt = DateTime.parse(data['created_at'] as String).toLocal();

  final int id;
  final String sender;
  final String recipient;
  final String clientId;
  final String? body;
  final bool hasImage;
  final DateTime createdAt;
}

class _Draft {
  const _Draft(this.id, this.peer, this.body, this.image);

  final String id;
  final String peer;
  final String body;
  final XFile? image;
}

String _messageId() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final hex = bytes
      .map((byte) => byte.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

class DriverChatScreen extends ConsumerStatefulWidget {
  const DriverChatScreen({super.key});

  @override
  ConsumerState<DriverChatScreen> createState() => _DriverChatScreenState();
}

class _DriverChatScreenState extends ConsumerState<DriverChatScreen>
    with WidgetsBindingObserver {
  final _body = TextEditingController();
  final _search = TextEditingController();
  final _scroll = ScrollController();
  final _contacts = <_ChatContact>[];
  final _conversations = <String, Map<String, dynamic>>{};
  final _messages = <_ChatMessage>[];
  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _events;
  Timer? _reconnect;
  Timer? _ack;
  int _connectionVersion = 0;
  int _inboxVersion = 0;
  int _historyVersion = 0;
  int _retrySeconds = 1;
  String? _userId;
  _ChatContact? _peer;
  XFile? _image;
  _Draft? _draft;
  bool _foreground = true;
  bool _connected = false;
  bool _sending = false;
  bool _loadingInbox = true;
  bool _loadingHistory = false;
  bool _older = false;
  int? _markingRead;
  String? _error;

  Dio get _dio => ref.read(dioProvider);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_refreshInbox());
    unawaited(_connect());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _disconnect();
    _ack?.cancel();
    _body.dispose();
    _search.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_foreground) {
      _foreground = true;
      unawaited(_refreshInbox());
      unawaited(_connect());
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      _foreground = false;
      _disconnect();
      if (mounted) setState(() => _connected = false);
    }
  }

  void _disconnect() {
    ++_connectionVersion;
    _reconnect?.cancel();
    unawaited(_events?.cancel());
    unawaited(_channel?.sink.close());
    _channel = null;
  }

  Future<void> _connect() async {
    final version = ++_connectionVersion;
    try {
      final response = await _dio.post('/chat/socket-tickets');
      if (!mounted || !_foreground || version != _connectionVersion) return;
      final ticket = response.data['data']['ticket'] as String;
      final api = Uri.parse(_dio.options.baseUrl);
      final basePath = api.path.replaceFirst(RegExp(r'/api/?$'), '');
      final url = api.replace(
        scheme: api.scheme == 'https' ? 'wss' : 'ws',
        path: '$basePath/ws/chat',
      );
      final channel = WebSocketChannel.connect(url);
      _channel = channel;
      _events = channel.stream.listen(
        (dynamic data) {
          if (version == _connectionVersion && mounted) {
            _onEvent(jsonDecode(data as String) as Map<String, dynamic>);
          }
        },
        onDone: () => _scheduleReconnect(version),
        onError: (Object _) => _scheduleReconnect(version),
      );
      await channel.ready;
      if (version == _connectionVersion && mounted) {
        channel.sink.add(
          jsonEncode({'type': 'authenticate', 'ticket': ticket}),
        );
      }
    } catch (_) {
      _scheduleReconnect(version);
    }
  }

  void _scheduleReconnect(int version) {
    if (!mounted || !_foreground || version != _connectionVersion) return;
    _disconnect();
    setState(() => _connected = false);
    _reconnect = Timer(
      Duration(seconds: _retrySeconds),
      () => unawaited(_connect()),
    );
    _retrySeconds = min(_retrySeconds * 2, 15);
  }

  void _onEvent(Map<String, dynamic> event) {
    switch (event['type']) {
      case 'ready':
        setState(() {
          _userId = event['user_id'] as String;
          _connected = true;
          _retrySeconds = 1;
        });
        unawaited(_refreshInbox());
        if (_peer != null) unawaited(_loadHistory());
      case 'message.saved':
        _receive(_ChatMessage(event['message'] as Map<String, dynamic>));
      case 'message.error':
        if (_sending && _draft?.id == event['client_message_id']) {
          _sendFailed('Không gửi được tin nhắn. Bạn có thể thử lại.');
        }
      case 'inbox.updated':
        unawaited(_refreshInbox());
    }
  }

  void _receive(_ChatMessage message) {
    final active = _peer?.id;
    final isCurrent = active == message.sender || active == message.recipient;
    final follow =
        !_scroll.hasClients ||
        _scroll.position.maxScrollExtent - _scroll.offset < 80;
    setState(() {
      if (message.sender == _userId &&
          _draft?.id == message.clientId &&
          _draft?.image == null) {
        _ack?.cancel();
        _draft = null;
        _sending = false;
        _error = null;
        _body.clear();
      }
      if (isCurrent && !_messages.any((entry) => entry.id == message.id)) {
        _messages.add(message);
        _messages.sort((a, b) => a.id.compareTo(b.id));
      }
    });
    if (isCurrent && follow) _scrollToBottom();
    unawaited(_refreshInbox());
  }

  Future<void> _refreshInbox() async {
    final version = ++_inboxVersion;
    try {
      final response = await _dio.get('/chat/inbox');
      if (!mounted || _inboxVersion != version) return;
      final data = response.data['data'] as Map<String, dynamic>;
      final contacts = [
        for (final row in data['contacts'] as List)
          _ChatContact(row['id'] as String, (row['name'] as String?) ?? ''),
      ];
      final conversations = {
        for (final row
            in (data['conversations'] as List).cast<Map<String, dynamic>>())
          row['peer_id'] as String: row,
      };
      contacts.sort((a, b) {
        final lastA = conversations[a.id]?['last_id'] as int? ?? 0;
        final lastB = conversations[b.id]?['last_id'] as int? ?? 0;
        return lastA == lastB
            ? a.name.compareTo(b.name)
            : lastB.compareTo(lastA);
      });
      setState(() {
        _userId = data['user_id'] as String;
        _contacts
          ..clear()
          ..addAll(contacts);
        _conversations
          ..clear()
          ..addAll(conversations);
        _loadingInbox = false;
        if (_peer != null &&
            !contacts.any((person) => person.id == _peer?.id)) {
          ++_historyVersion;
          _peer = null;
          _messages.clear();
          _error = 'Người nhận hiện không còn trong danh bạ chat.';
        }
      });
      unawaited(_markRead());
    } catch (_) {
      if (!mounted || _inboxVersion != version) return;
      setState(() {
        _loadingInbox = false;
        _error = 'Không tải được danh sách tin nhắn.';
      });
    }
  }

  Future<void> _loadHistory({int? before}) async {
    final peer = _peer?.id;
    if (peer == null) return;
    final version = ++_historyVersion;
    setState(() => _loadingHistory = true);
    try {
      final response = await _dio.get(
        '/chat/messages/$peer',
        queryParameters: {'before': ?before},
      );
      if (!mounted || version != _historyVersion) return;
      final page = (response.data['data'] as List)
          .cast<Map<String, dynamic>>()
          .map(_ChatMessage.new)
          .toList();
      final previousHeight = _scroll.hasClients
          ? _scroll.position.maxScrollExtent
          : 0.0;
      final previousOffset = _scroll.hasClients ? _scroll.offset : 0.0;
      final combined = {
        for (final entry in _messages)
          if (before != null || page.isEmpty || entry.id > page.first.id)
            entry.id: entry,
        for (final entry in page) entry.id: entry,
      };
      setState(() {
        _messages
          ..clear()
          ..addAll(combined.values)
          ..sort((a, b) => a.id.compareTo(b.id));
        _older = page.length == 50;
        _loadingHistory = false;
      });
      if (before == null) {
        _scrollToBottom();
      } else {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted || version != _historyVersion || !_scroll.hasClients) {
            return;
          }
          _scroll.jumpTo(
            previousOffset + _scroll.position.maxScrollExtent - previousHeight,
          );
        });
      }
      unawaited(_markRead());
    } catch (_) {
      if (!mounted || version != _historyVersion) return;
      setState(() {
        _loadingHistory = false;
        _error = 'Không tải được cuộc trò chuyện.';
      });
    }
  }

  Future<void> _markRead() async {
    final peer = _peer?.id;
    if (!_foreground ||
        peer == null ||
        (_conversations[peer]?['unread'] ?? 0) == 0) {
      return;
    }
    for (final message in _messages.reversed) {
      if (message.sender != peer) continue;
      if (_markingRead == message.id) return;
      _markingRead = message.id;
      try {
        await _dio.put('/chat/messages/$peer/read/${message.id}');
        if (mounted) unawaited(_refreshInbox());
      } catch (_) {
        // A subsequent inbox refresh can retry the read cursor.
      } finally {
        if (_markingRead == message.id) _markingRead = null;
      }
      return;
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scroll.hasClients) {
        _scroll.jumpTo(_scroll.position.maxScrollExtent);
      }
    });
  }

  void _open(_ChatContact? contact) {
    if (_sending) return;
    ++_historyVersion;
    _body.clear();
    setState(() {
      _peer = contact;
      _messages.clear();
      _image = null;
      _draft = null;
      _error = null;
      _older = false;
    });
    if (contact != null) unawaited(_loadHistory());
  }

  Future<void> _pickImage() async {
    try {
      final peer = _peer?.id;
      final file = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        maxWidth: 1920,
        maxHeight: 1920,
        imageQuality: 85,
      );
      if (!mounted || file == null || _peer?.id != peer) return;
      final tooLarge = await file.length() > 5 * 1024 * 1024;
      if (!mounted || _peer?.id != peer) return;
      setState(() {
        _error = tooLarge ? 'Ảnh phải nhỏ hơn hoặc bằng 5 MB.' : null;
        if (!tooLarge) _image = file;
      });
    } catch (_) {
      if (mounted) setState(() => _error = 'Không mở được thư viện ảnh.');
    }
  }

  Future<void> _send() async {
    final peer = _peer?.id;
    final body = _body.text.trim();
    final image = _image;
    if (peer == null ||
        _sending ||
        (image == null && (body.isEmpty || !_connected))) {
      return;
    }
    final previous = _draft;
    final draft =
        previous != null &&
            previous.peer == peer &&
            previous.body == body &&
            previous.image == image
        ? previous
        : _Draft(_messageId(), peer, body, image);
    setState(() {
      _draft = draft;
      _sending = true;
      _error = null;
    });
    try {
      if (image != null) {
        final form = FormData.fromMap({
          'client_message_id': draft.id,
          'file': MultipartFile.fromBytes(
            await image.readAsBytes(),
            filename: image.name,
          ),
        });
        final response = await _dio.post(
          '/chat/messages/$peer/image',
          data: form,
        );
        if (!mounted) return;
        _receive(_ChatMessage(response.data['data'] as Map<String, dynamic>));
        setState(() {
          _image = null;
          _draft = null;
          _sending = false;
        });
      } else {
        _channel!.sink.add(
          jsonEncode({
            'type': 'message.send',
            'peer_id': peer,
            'client_message_id': draft.id,
            'body': body,
          }),
        );
        _ack = Timer(const Duration(seconds: 8), () {
          _sendFailed('Chưa nhận được xác nhận. Bạn có thể gửi lại.');
        });
      }
    } on DioException catch (error) {
      final status = error.response?.statusCode;
      _sendFailed(
        status == 415 || status == 413
            ? 'Chọn ảnh JPG, PNG hoặc WebP tối đa 5 MB.'
            : 'Không gửi được tin nhắn. Bạn có thể thử lại.',
      );
    } catch (_) {
      _sendFailed('Không gửi được tin nhắn. Bạn có thể thử lại.');
    }
  }

  void _sendFailed(String error) {
    _ack?.cancel();
    if (mounted) {
      setState(() {
        _error = error;
        _sending = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final peer = _peer;
    return PopScope(
      canPop: peer == null,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && peer != null) _open(null);
      },
      child: Scaffold(
        appBar: AppBar(
          leading: BackButton(
            onPressed: peer == null ? null : () => _open(null),
          ),
          title: Text(peer?.name ?? 'Tin nhắn'),
          actions: [
            if (!_connected)
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 12),
                child: Center(child: Text('Đang kết nối…')),
              ),
          ],
        ),
        body: SafeArea(
          child: Column(
            children: [
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(
                    _error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              Expanded(child: peer == null ? _buildInbox() : _buildThread()),
              if (peer != null) _buildComposer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInbox() {
    if (_loadingInbox) return const Center(child: CircularProgressIndicator());
    final contacts = _contacts
        .where(
          (entry) => entry.name.toLowerCase().contains(
            _search.text.trim().toLowerCase(),
          ),
        )
        .toList();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            controller: _search,
            decoration: const InputDecoration(
              hintText: 'Tìm người nhận',
              prefixIcon: Icon(Icons.search),
            ),
            onChanged: (_) => setState(() {}),
          ),
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: _refreshInbox,
            child: ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              itemCount: contacts.isEmpty ? 1 : contacts.length,
              itemBuilder: (context, index) {
                if (contacts.isEmpty) {
                  return const ListTile(
                    title: Text('Không tìm thấy người nhận.'),
                  );
                }
                final contact = contacts[index];
                final last = _conversations[contact.id];
                final unread = last?['unread'] as int? ?? 0;
                return ListTile(
                  title: Text(contact.name),
                  subtitle: last == null
                      ? null
                      : Text(
                          last['has_image'] == true
                              ? 'Ảnh'
                              : (last['body'] as String? ?? ''),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                  trailing: unread > 0 ? Badge(label: Text('$unread')) : null,
                  onTap: () => _open(contact),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildThread() {
    if (_messages.isEmpty) {
      return Center(
        child: _loadingHistory
            ? const CircularProgressIndicator()
            : const Text('Chưa có tin nhắn.'),
      );
    }
    return ListView(
      controller: _scroll,
      padding: const EdgeInsets.all(12),
      children: [
        if (_older)
          TextButton(
            onPressed: _loadingHistory
                ? null
                : () => unawaited(_loadHistory(before: _messages.first.id)),
            child: const Text('Xem tin cũ hơn'),
          ),
        for (final message in _messages)
          Align(
            key: ValueKey(message.id),
            alignment: message.sender == _userId
                ? Alignment.centerRight
                : Alignment.centerLeft,
            child: Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(10),
              constraints: BoxConstraints(
                maxWidth: MediaQuery.sizeOf(context).width * .75,
              ),
              decoration: BoxDecoration(
                color: message.sender == _userId
                    ? Theme.of(context).colorScheme.primaryContainer
                    : Theme.of(context).colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (message.hasImage) _ChatImage(id: message.id),
                  if (message.body != null) Text(message.body!),
                  Text(
                    '${message.createdAt.day}/${message.createdAt.month} · '
                    '${TimeOfDay.fromDateTime(message.createdAt).format(context)}',
                    style: Theme.of(context).textTheme.labelSmall,
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildComposer() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 8),
      child: Column(
        children: [
          if (_image != null)
            ListTile(
              dense: true,
              title: Text(
                _image!.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: IconButton(
                tooltip: 'Bỏ ảnh',
                icon: const Icon(Icons.close),
                onPressed: _sending
                    ? null
                    : () => setState(() => _image = null),
              ),
            ),
          Row(
            children: [
              IconButton(
                tooltip: 'Chọn ảnh',
                icon: const Icon(Icons.add_photo_alternate_outlined),
                onPressed: _sending ? null : _pickImage,
              ),
              Expanded(
                child: TextField(
                  controller: _body,
                  enabled: !_sending && _image == null,
                  maxLength: 4000,
                  maxLines: 3,
                  minLines: 1,
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(
                    hintText: 'Nhập tin nhắn',
                    counterText: '',
                  ),
                ),
              ),
              IconButton.filled(
                tooltip: _draft == null ? 'Gửi' : 'Gửi lại',
                onPressed:
                    _sending ||
                        (_image == null &&
                            (!_connected || _body.text.trim().isEmpty))
                    ? null
                    : _send,
                icon: _sending
                    ? const SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send_rounded),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ChatImage extends ConsumerStatefulWidget {
  const _ChatImage({required this.id});

  final int id;

  @override
  ConsumerState<_ChatImage> createState() => _ChatImageState();
}

class _ChatImageState extends ConsumerState<_ChatImage> {
  late final Future<Response<List<int>>> _load = ref
      .read(dioProvider)
      .get<List<int>>(
        '/chat/images/${widget.id}',
        options: Options(responseType: ResponseType.bytes),
      );

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Response<List<int>>>(
      future: _load,
      builder: (context, snapshot) {
        if (snapshot.hasError) return const Text('Không tải được ảnh');
        final bytes = snapshot.data?.data;
        if (bytes == null) return const Text('Đang tải ảnh…');
        final image = Uint8List.fromList(bytes);
        return InkWell(
          onTap: () => showDialog<void>(
            context: context,
            builder: (_) => Dialog(
              child: InteractiveViewer(
                child: Image.memory(image, semanticLabel: 'Ảnh trong tin nhắn'),
              ),
            ),
          ),
          child: Image.memory(
            image,
            height: 180,
            fit: BoxFit.contain,
            semanticLabel: 'Ảnh trong tin nhắn',
            errorBuilder: (_, _, _) => const Text('Không mở được ảnh'),
          ),
        );
      },
    );
  }
}
