import 'device_platform_stub.dart'
    if (dart.library.io) 'device_platform_io.dart';

class DeviceMetadataSnapshot {
  final String installationId;
  final String deviceOs;
  final String osVersion;
  final String appVersion;
  final String deviceModel;

  const DeviceMetadataSnapshot({
    required this.installationId,
    required this.deviceOs,
    required this.osVersion,
    required this.appVersion,
    required this.deviceModel,
  });

  Map<String, dynamic> toActivationJson(String normalizedActivationKey) => {
    'activation_key': normalizedActivationKey,
    'device_id': installationId,
    'device_os': deviceOs,
    'os_version': osVersion,
    'app_version': appVersion,
    'device_model': deviceModel,
  };

  Map<String, dynamic> toHeartbeatJson() => {
    'device_id': installationId,
    'device_os': deviceOs,
    'os_version': osVersion,
    'app_version': appVersion,
    'device_model': deviceModel,
  };
}

class DeviceMetadataCollector {
  static const String currentAppVersion = String.fromEnvironment(
    'APP_VERSION',
    defaultValue: '1.0.0',
  );

  static DeviceMetadataSnapshot collect(String installationId) {
    final platform = readPlatformDeviceDetails();
    return DeviceMetadataSnapshot(
      installationId: installationId,
      deviceOs: platform.deviceOs,
      osVersion: platform.osVersion,
      appVersion: currentAppVersion,
      deviceModel: platform.deviceModel,
    );
  }
}
