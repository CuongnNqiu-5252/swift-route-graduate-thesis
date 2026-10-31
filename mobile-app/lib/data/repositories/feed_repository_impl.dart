import 'package:dio/dio.dart';
import 'package:swiftroute_app/data/models/post.dart';

class FeedRequest {
  late final num page, size;
}

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

abstract class FeedRepositoryInterface {
  Future<FeedPage> getFeed(FeedRequest re);
}

class FeedRepository implements FeedRepositoryInterface {
  late final Dio _dio;
  FeedRepository({required Dio dio}) : _dio = dio;
  @override
  Future<FeedPage> getFeed(FeedRequest re) async {
    try {
      final res = await _dio.get(
        "/feed",
        queryParameters: {'page': re.page, 'size': re.size},
      );
      final feed = res.data as Map<String, dynamic>;
      return FeedPage(
        items: (feed['content'] as List<Post>)
            .map<Post>((item) => Post.fromJson(item as Map<String, dynamic>))
            .toList(),
        page: feed['page'] as int,
        totalPages: feed['size'],
        isLast: feed['hasMore'],
      );
    } on DioException catch (e) {
      throw Exception(e);
    }
  }
}
