// features/order/data/models/goong_place_model.dart

/// Represents an autocomplete prediction result from Goong Place API.
class GoongPrediction {
  final String placeId;
  final String description;
  final String mainText;
  final String secondaryText;

  const GoongPrediction({
    required this.placeId,
    required this.description,
    required this.mainText,
    required this.secondaryText,
  });

  factory GoongPrediction.fromJson(Map<String, dynamic> json) {
    final structured = json['structured_formatting'] as Map<String, dynamic>?;
    return GoongPrediction(
      placeId: json['place_id'] as String? ?? '',
      description: json['description'] as String? ?? '',
      mainText: structured?['main_text'] as String? ?? json['description'] ?? '',
      secondaryText: structured?['secondary_text'] as String? ?? '',
    );
  }
}

/// Represents detailed geographic information for a specific place.
class GoongPlaceDetail {
  final String placeId;
  final String name;
  final String formattedAddress;
  final double lat;
  final double lng;

  const GoongPlaceDetail({
    required this.placeId,
    required this.name,
    required this.formattedAddress,
    required this.lat,
    required this.lng,
  });

  factory GoongPlaceDetail.fromJson(Map<String, dynamic> json) {
    final result = json['result'] as Map<String, dynamic>? ?? json;
    final geometry = result['geometry'] as Map<String, dynamic>?;
    final location = geometry?['location'] as Map<String, dynamic>?;

    return GoongPlaceDetail(
      placeId: result['place_id'] as String? ?? '',
      name: result['name'] as String? ?? '',
      formattedAddress: result['formatted_address'] as String? ?? '',
      lat: (location?['lat'] as num?)?.toDouble() ?? 0.0,
      lng: (location?['lng'] as num?)?.toDouble() ?? 0.0,
    );
  }
}
