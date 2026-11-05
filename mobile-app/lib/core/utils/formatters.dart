// core/utils/formatters.dart

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

/// Operational Vietnamese formatters and phone call launcher for drivers.
abstract final class AppFormatters {
  static final NumberFormat _vndFormat = NumberFormat.currency(
    locale: 'vi_VN',
    symbol: '₫',
    decimalDigits: 0,
  );

  static final DateFormat _timeFormat = DateFormat('HH:mm');
  static final DateFormat _dateTimeFormat = DateFormat('HH:mm · dd/MM');
  static final DateFormat _dateFormat = DateFormat('dd/MM/yyyy');

  static String currency(num amount) {
    return _vndFormat.format(amount.round());
  }

  static String time(DateTime dt) {
    return _timeFormat.format(dt.toLocal());
  }

  static String timeWindow(DateTime start, DateTime end) {
    return '${time(start)} – ${time(end)}';
  }

  static String dateTime(DateTime dt) {
    return _dateTimeFormat.format(dt.toLocal());
  }

  static String date(DateTime dt) {
    return _dateFormat.format(dt.toLocal());
  }

  static String shortCode(String? externalCode, String id) {
    if (externalCode != null && externalCode.trim().isNotEmpty) {
      return externalCode.trim();
    }
    if (id.isEmpty) return '#---';
    final clean = id.replaceAll('-', '').toUpperCase();
    return '#${clean.length > 6 ? clean.substring(0, 6) : clean}';
  }

  /// Launches the native phone dialer with 1 tap.
  static Future<void> callPhone(BuildContext context, String? rawPhone) async {
    final phone = rawPhone?.replaceAll(RegExp(r'[^\d+]'), '') ?? '';
    if (phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đơn này chưa có số điện thoại liên hệ.')),
      );
      return;
    }
    final uri = Uri(scheme: 'tel', path: phone);
    try {
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không mở được trình gọi điện cho số $phone')),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Số liên hệ: $phone')));
      }
    }
  }
}
