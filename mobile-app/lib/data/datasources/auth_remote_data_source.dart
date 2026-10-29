import 'package:dio/dio.dart';

class AuthRemoteDataSource {
  final Dio dio;

  AuthRemoteDataSource(this.dio);

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await dio.post(
      '/auth/login',
      data: {'identifier': email, 'password': password},
    );
    return res.data;
  }

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
