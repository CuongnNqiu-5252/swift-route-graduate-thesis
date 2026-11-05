// core/theme/app_colors.dart

import 'package:flutter/material.dart';

/// Centralized color tokens for the SwiftRoute Driver Field-Ops Material 3 system.
/// High-contrast Cobalt Blue + Safety Amber palette engineered for outdoor legibility.
abstract final class AppColors {
  // ── Brand & Primary Actions (Crisp Cobalt Blue) ─────────────────────────
  static const Color primary = Color(
    0xFF1D4ED8,
  ); // Cobalt 700 — high outdoor contrast
  static const Color primaryDark = Color(0xFF1E3A8A); // Cobalt 900
  static const Color primarySoft = Color(0xFFEFF6FF); // Blue 50
  static const Color primaryBorder = Color(0xFFBFDBFE); // Blue 200

  // ── Safety Amber / COD & Pickup Highlights ──────────────────────────────
  static const Color accent = Color(
    0xFFD97706,
  ); // Amber 600 — unmistakable COD / Pickup cue
  static const Color accentLight = Color(0xFFF59E0B); // Amber 500
  static const Color warning = Color(
    0xFFB45309,
  ); // Amber 700 (AA text on light amber)
  static const Color warningSoft = Color(0xFFFFFBEB); // Amber 50
  static const Color warningBorder = Color(0xFFFDE68A); // Amber 200

  // ── Surfaces & Slate Neutrals (Aligned with Web Admin) ──────────────────
  static const Color background = Color(0xFFF8FAFC); // Slate 50 canvas
  static const Color card = Color(0xFFFFFFFF); // Crisp white surface
  static const Color surface = Color(
    0xFFF1F5F9,
  ); // Slate 100 input / subtle fill
  static const Color surfaceVariant = Color(0xFFE2E8F0); // Slate 200
  static const Color divider = Color(0xFFE2E8F0); // Slate 200 1px hairline

  // ── Typography Hierarchy (WCAG AAA / AA outdoor contrast) ───────────────
  static const Color textPrimary = Color(0xFF0F172A); // Slate 900
  static const Color textSecondary = Color(0xFF475569); // Slate 600
  static const Color textHint = Color(
    0xFF64748B,
  ); // Slate 500 (never washed-out gray)

  // ── Semantic Operational States ─────────────────────────────────────────
  static const Color success = Color(0xFF15803D); // Green 700
  static const Color successBright = Color(0xFF16A34A); // Green 600
  static const Color successSoft = Color(0xFFF0FDF4); // Green 50
  static const Color successBorder = Color(0xFFBBF7D0); // Green 200

  static const Color error = Color(0xFFDC2626); // Red 600
  static const Color errorDark = Color(0xFFB91C1C); // Red 700
  static const Color errorSoft = Color(0xFFFEF2F2); // Red 50
  static const Color errorBorder = Color(0xFFFECACA); // Red 200

  // ── Hero / Splash Overlays ──────────────────────────────────────────────
  static const Color heroGradientStart = Color(0x001E3A8A);
  static const Color heroGradientEnd = Color(0xDD1E3A8A);
}
