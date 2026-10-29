import 'package:flutter/material.dart';

class CustomButton extends StatelessWidget {
  const CustomButton({super.key, required this.text, required this.icon});
  final String text;
  final Widget icon;
  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: () {},
      icon: icon,
      label: Text(text, style: const TextStyle(color: Colors.white70, fontSize: 16)),
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(double.infinity, 50),
        backgroundColor: const Color(0xFF16213E),
        side: const BorderSide(color: Color(0xFF2A2A4A)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}
