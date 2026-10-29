class Post {
  final int postId;
  final int authorId;
  final String authorUsername;
  final String content;
  final String postType;
  final int? parentPostId;
  final List<String> mediaUrls;
  final List<String> hashtags;
  final int likesCount;
  final int commentsCount;
  final int sharesCount;
  final double decayScore;
  final DateTime createdAt;
  final DateTime updatedAt;

  Post({
    required this.postId,
    required this.authorId,
    required this.authorUsername,
    required this.content,
    required this.postType,
    required this.parentPostId,
    required this.mediaUrls,
    required this.hashtags,
    required this.likesCount,
    required this.commentsCount,
    required this.sharesCount,
    required this.decayScore,
    required this.createdAt,
    required this.updatedAt,
  });

  factory Post.fromJson(Map<String, dynamic> json) {
  return Post(
    postId: json['postId'] as int,
    authorId: json['authorId'] as int,
    authorUsername: json['authorUsername'] as String,
    content: json['content'] as String,
    postType: json['postType'] as String,
    parentPostId: json['parentPostId'] as int?,
    mediaUrls: List<String>.from(json['mediaUrls'] ?? []),
    hashtags: List<String>.from(json['hashtags'] ?? []),
    likesCount: json['likesCount'] as int,
    commentsCount: json['commentsCount'] as int,
    sharesCount: json['sharesCount'] as int,
    decayScore: (json['decayScore'] as num).toDouble(),
    createdAt: DateTime.parse(json['createdAt'] as String),
    updatedAt: DateTime.parse(json['updatedAt'] as String),
  );
}
}
