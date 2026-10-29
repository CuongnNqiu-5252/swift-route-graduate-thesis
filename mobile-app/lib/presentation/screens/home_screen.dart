import 'package:flutter/material.dart';
import 'package:swiftroute_app/features/order/presentation/widgets/goong_map_widget.dart';
import 'package:swiftroute_app/widget/layout/app_header.dart';
import 'package:swiftroute_app/widget/searchbar.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const Header(),
      body: Stack(
        children: [
          const SearchBarApp(),
          const GoongMapWidget(),
          Container(
            child: Text("Hello"),
            color: Colors.amber,
            height: 200,
            width: 200,
          ),
          Row(
            children: [
              Container(
                child: Icon(Icons.restaurant),
                color: Colors.amber,
                height: 100,
                width: 100,
              ),
              Container(
                child: Icon(Icons.free_breakfast),
                color: Colors.amber,
                height: 100,
                width: 100,
              ),
              Container(
                child: Icon(Icons.medication),
                color: Colors.amber,
                height: 100,
                width: 100,
              ),
            ],
          ),
          Container(
            width: 100,
            decoration: BoxDecoration(border: Border.all()),
            child: const Text('Nearby shops', overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}
