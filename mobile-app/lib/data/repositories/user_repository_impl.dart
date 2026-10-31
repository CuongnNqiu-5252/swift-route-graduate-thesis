import 'package:dio/dio.dart';
import 'package:swiftroute_app/core/network/dio.dart';
import 'package:swiftroute_app/data/models/auth_model.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

abstract class UserRepositoryInterface {
  Future<List<UserModel>> getUsers();
}

class UserRepository implements UserRepositoryInterface {
  final Dio _dio;

  UserRepository({required Dio dio}) : _dio = dio;

  @override
  Future<List<UserModel>> getUsers() async {
    try {
      final response = await _dio.get('/users');
      return (response.data as List)
          .map((json) => UserModel.fromJson(json as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw Exception(e.response?.data['message'] ?? 'Failed to get users');
    }
  }
}

final userRepositoryProvider = Provider((ref) {
  return UserRepository(dio: ref.read(dioProvider));
});
