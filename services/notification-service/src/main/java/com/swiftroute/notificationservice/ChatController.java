package com.swiftroute.notificationservice;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
class ChatController {
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    record Inbox(UUID userId, List<ChatAccess.Member> contacts, List<ChatMessages.InboxItem> conversations) {}

    private final ChatAccess access;
    private final ChatMessages messages;
    private final ChatSocket socket;

    ChatController(ChatAccess access, ChatMessages messages, ChatSocket socket) {
        this.access = access;
        this.messages = messages;
        this.socket = socket;
    }

    @PostMapping("/socket-tickets")
    Map<String, Map<String, UUID>> ticket(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        var account = access.account(bearer);
        access.contacts(account);
        return Map.of("data", Map.of("ticket", socket.ticket(account)));
    }

    @GetMapping("/inbox")
    Map<String, Inbox> inbox(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        var account = access.account(bearer);
        var contacts = access.contacts(account);
        Set<UUID> peers = contacts.stream().map(ChatAccess.Member::id).collect(Collectors.toSet());
        return Map.of("data", new Inbox(account.user(), contacts,
                messages.inbox(account).stream().filter(item -> peers.contains(item.peerId())).toList()));
    }

    @GetMapping("/messages/{peer}")
    Map<String, List<ChatMessages.Message>> history(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
            @PathVariable UUID peer, @RequestParam(defaultValue = "9223372036854775807") long before) {
        var account = access.account(bearer);
        access.requirePeer(account, peer);
        return Map.of("data", messages.history(account, peer, before));
    }

    @PutMapping("/messages/{peer}/read/{id}")
    ResponseEntity<Void> read(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
            @PathVariable UUID peer, @PathVariable long id) {
        var account = access.account(bearer);
        access.requirePeer(account, peer);
        messages.markRead(account, peer, id);
        socket.read(account.tenant(), account.user(), peer);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/messages/{peer}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, ChatMessages.Message> sendImage(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer, @PathVariable UUID peer,
            @RequestParam("client_message_id") UUID clientId, @RequestParam("file") MultipartFile file) throws IOException {
        var account = access.account(bearer);
        access.requirePeer(account, peer);
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh phải nhỏ hơn hoặc bằng 5 MB");
        }
        byte[] contents = file.getBytes();
        String contentType = imageType(contents);
        var saved = messages.save(account, peer, clientId, null, new ChatMessages.Image(contentType, contents));
        socket.publish(account.tenant(), saved);
        return Map.of("data", saved);
    }

    @GetMapping("/images/{id}")
    ResponseEntity<byte[]> image(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer, @PathVariable long id) {
        var account = access.account(bearer);
        access.requirePeer(account, messages.imagePeer(account, id));
        var image = messages.image(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.contents());
    }

    private String imageType(byte[] bytes) {
        if (matches(bytes, 0, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (matches(bytes, 0, 0x89, 'P', 'N', 'G', 13, 10, 26, 10)) return "image/png";
        if (matches(bytes, 0, 'R', 'I', 'F', 'F') && matches(bytes, 8, 'W', 'E', 'B', 'P', 'V', 'P', '8')) {
            return "image/webp";
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ hỗ trợ ảnh JPG, PNG và WebP");
    }

    private boolean matches(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (Byte.toUnsignedInt(bytes[offset + index]) != signature[index]) return false;
        }
        return true;
    }
}
