import 'package:flutter/material.dart';

class GradientFAB extends StatefulWidget {
  const GradientFAB({super.key, required this.onPressed});

  final VoidCallback onPressed;

  @override
  State<GradientFAB> createState() => _GradientFABState();
}

class _GradientFABState extends State<GradientFAB> {
  bool pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => pressed = true),
      onTapUp: (_) => setState(() => pressed = false),
      onTapCancel: () => setState(() => pressed = false),
      onTap: widget.onPressed,
      child: AnimatedScale(
        duration: const Duration(milliseconds: 100),
        scale: pressed ? 0.9 : 1,
        child: Container(
          padding: const EdgeInsets.all(3),
          decoration: const BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [Color(0xFF6A5AE0), Color(0xFF00D4FF)],
            ),
          ),
          child: const CircleAvatar(
            radius: 28,
            backgroundColor: Color(0xFF2A2A4A),
            child: Icon(Icons.add, color: Colors.white),
          ),
        ),
      ),
    );
  }
}
