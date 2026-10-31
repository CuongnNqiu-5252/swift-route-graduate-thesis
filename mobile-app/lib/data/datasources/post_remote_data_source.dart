import 'package:dio/dio.dart';

class PostRemoteDataSource {
  final Dio dio;

  PostRemoteDataSource(this.dio);


  Future<Map<String, dynamic>> register(
    String email,
    String password,
    String name,
  ) async {
    final res = await dio.post(
      '/users/register',
      data: {'email': email, 'password': password, 'username': name},
    );
    return res.data;
  }
}
