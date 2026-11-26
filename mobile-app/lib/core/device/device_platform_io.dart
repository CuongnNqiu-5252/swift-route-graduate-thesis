import 'dart:io';

import 'device_platform_stub.dart';

PlatformDeviceDetails readPlatformDeviceDetails() {
  final osName = switch (Platform.operatingSystem.toLowerCase()) {
    'android' => 'ANDROID',
    'ios' => 'IOS',
    'windows' => 'WINDOWS',
    'macos' => 'MACOS',
    'linux' => 'LINUX',
    _ => Platform.operatingSystem.toUpperCase(),
  };

  final rawOsVer = Platform.operatingSystemVersion.trim();
  final shortOsVer = rawOsVer.length > 48 ? rawOsVer.substring(0, 48) : rawOsVer;

  final host = Platform.localHostname.trim();
  final fallbackModel = switch (osName) {
    'ANDROID' => 'Android Device',
    'IOS' => 'iPhone',
    _ => '$osName Device',
  };
  final model = host.isNotEmpty && host.toLowerCase() != 'localhost'
      ? (host.length > 100 ? host.substring(0, 100) : host)
      : fallbackModel;

  return PlatformDeviceDetails(
    deviceOs: osName,
    osVersion: shortOsVer,
    deviceModel: model,
  );
}
