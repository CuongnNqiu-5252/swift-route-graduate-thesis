import 'package:flutter/material.dart';

class SearchBarApp extends StatefulWidget {
  const SearchBarApp({super.key});

  @override
  State<SearchBarApp> createState() => _SearchBarAppState();
}

class _SearchBarAppState extends State<SearchBarApp> {
  @override
  Widget build(BuildContext context) {
    return SearchAnchor(
      builder: (context, controller) {
        return SearchBar(
          controller: controller,
          elevation: WidgetStatePropertyAll(4),
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          hintText: 'Enter destination',
          padding: const WidgetStatePropertyAll<EdgeInsets>(
            EdgeInsets.symmetric(horizontal: 16.0),
          ),
          trailing: [IconButton(onPressed: () {}, icon: const Icon(Icons.add))],
          leading: const Icon(Icons.search),
          onTap: () {
            controller.openView();
          },
          onChanged: (_) {
            controller.openView();
          },
        );
      },
      suggestionsBuilder: (context, controller) {
        return [Container(color: Colors.amber)];
      },
    );
  }
}
