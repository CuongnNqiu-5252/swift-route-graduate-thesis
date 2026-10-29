import 'package:dio/dio.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/data/models/post.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:developer' as developer;
import 'dart:typed_data';

class FeedPage {
  final List<Post> items;
  final int page;
  final int totalPages;
  final bool isLast;

  FeedPage({
    required this.items,
    required this.page,
    required this.totalPages,
    required this.isLast,
  });
}

abstract class PostRepositoryInterface {
  Future<void> createPost(PostRequest request);
  Future<FeedPage> getPosts(FeedRequest re);
  Future<void> likePost(String postId);
  Future<List<String>> uploadImages(
    List<Uint8List> files, {
    void Function(double progress)? onProgress,
  });
}
class FeedRequest{
  late final num page, size;
}
class PostRequest {
  final String content;
  final List<String> hashtags;
  final List<String> mediaUrls;
  PostRequest({
    required this.content,
    required this.hashtags,
    required this.mediaUrls,
  });

  Map<String, dynamic> toJson() => {
    'content': content,
    'hashtags': hashtags,
    'mediaUrls': mediaUrls,
  };
}

class PostRepository implements PostRepositoryInterface {
  final Dio _dio;

  PostRepository({required Dio dio}) : _dio = dio;

  @override
  Future<void> likePost(String postId) async {
    try {
      await _dio.post('/posts/$postId/like');
    } on DioException catch (e) {
      throw Exception(e.response?.data['message'] ?? 'Failed to like post');
    }
  }

  @override
  Future<Post> createPost(PostRequest request) async {
    try {
      final response = await _dio.post('/posts', data: request.toJson());
      final feed = response.data;
      developer.log('response',name:feed['content']);
      return Post.fromJson(feed);
    } on DioException catch (e) {
      throw Exception(e.response?.data['message'] ?? 'Failed to create post');
    }
  }

  @override
  Future<FeedPage> getPosts(FeedRequest re) async {
    try {
      final res = await _dio.get("/feed",
          queryParameters: {
            'page': re.page,
            'size':re.size,
          }
        );
        print(res);
        final feed = res.data as Map<String, dynamic>;
        return FeedPage(items: (feed['content'] as List<Post>).map<Post>((item) => Post.fromJson(item as Map<String, dynamic>)).toList() , 
        page: feed['page'] as int, 
        totalPages: feed['size'], 
        isLast: feed['hasMore']
        );
    } on DioException catch (e) {
      throw Exception(e.response?.data['message'] ?? 'Failed to get posts');
    }
  }

  @override
  Future<List<String>> uploadImages(
    List<Uint8List> files, {
    void Function(double progress)? onProgress,
  }) async {
    try {
      final formData = FormData();
      for (int i = 0; i < files.length; i++) {
        formData.files.add(
          MapEntry(
            'files',
            MultipartFile.fromBytes(
              files[i],
              filename: 'image_${DateTime.now().millisecondsSinceEpoch}_$i.jpg',
            ),
          ),
        );
      }

      final res = await _dio.post(
        '/media/upload',
        data: formData,
        onSendProgress: (sent, total) {
          if (total > 0 && onProgress != null) {
            onProgress(sent / total);
          }
        },
      );

      final data = res.data;
      if (data is Map<String, dynamic> && data['data'] != null) {
        final list = data['data'] as List;
        return list.map((e) => e.toString()).toList();
      } else if (data is List) {
        return data.map((e) => e.toString()).toList();
      }
      return [];
    } on DioException catch (e) {
      throw Exception(e.response?.data?['message'] ?? 'Failed to upload images');
    }
  }
}


final postRepositoryProvider = Provider((ref) {
  return PostRepository(dio: ref.read(dioProvider));
});
