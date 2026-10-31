// core/theme/app_colors.dart

import 'package:flutter/material.dart';

/// Centralized color tokens for the SwiftRoute app.
/// All screens and widgets should reference these instead of hardcoding hex values.
abstract final class AppColors {
  // ── Brand ──────────────────────────────────────────────
  static const Color primary = Color(0xFF2F3C7E); // Deep Indigo Blue
  static const Color accent = Color(0xFFFF7A45); // Vibrant Orange
  static const Color accentLight = Color(0xFFFF9A6C); // Orange hover / lighter

  // ── Light palette (new design) ─────────────────────────
  static const Color background = Color(0xFFFFFFFF);
  static const Color surface = Color(0xFFF3F4F6); // Input / card fill
  static const Color surfaceVariant = Color(0xFFE9EBF0);
  static const Color textPrimary = Color(0xFF1F2937);
  static const Color textSecondary = Color(0xFF6B7280);
  static const Color textHint = Color(0xFF9CA3AF);
  static const Color divider = Color(0xFFE5E7EB);
  static const Color success = Color(0xFF22C55E);
  static const Color error = Color(0xFFEF4444);

  // ── Hero illustration overlay ───────────────────────────
  static const Color heroGradientStart = Color(0x002F3C7E); // transparent
  static const Color heroGradientEnd = Color(0xCC2F3C7E); // ~80% indigo

  // ── Legacy dark palette (existing dark-mode screens) ────
  /// Keep these to avoid breaking existing screens while the new
  /// light design is being rolled out incrementally.
  static const Color darkBackground = Color(0xFF0D0D1A);
  static const Color darkSurface = Color(0xFF1A1A2E);
  static const Color darkCard = Color(0xFF16213E);
  static const Color darkBorder = Color(0xFF2A2A4A);
  static const Color darkTextMuted = Color(0xFF8E8EA0);
  static const Color purple = Color(0xFF7C4DFF);
  static const Color cyan = Color(0xFF00E5FF);
}
