class PlatformDeviceDetails {
  final String deviceOs;
  final String osVersion;
  final String deviceModel;

  const PlatformDeviceDetails({
    required this.deviceOs,
    required this.osVersion,
    required this.deviceModel,
  });
}

PlatformDeviceDetails readPlatformDeviceDetails() {
  return const PlatformDeviceDetails(
    deviceOs: 'WEB',
    osVersion: 'Browser',
    deviceModel: 'Flutter Web Client',
  );
}
