import 'package:flutter/widgets.dart';

class NearbyTenant {
  final String name;
  final double rating;
  final double distance;
  final double time;
  final String country;

  NearbyTenant({
    required this.name,
    required this.rating,
    required this.distance,
    required this.time,
    required this.country,
  });
}

class CardWidget extends StatelessWidget {
  const CardWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return Container();
  }
}
