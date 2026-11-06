// core/widgets/slide_to_confirm_button.dart

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';

/// Tactile Slide-to-Confirm control engineered for outdoor one-handed field use.
/// Prevents accidental pocket/handlebar taps on irreversible physical transitions
/// (such as confirming depot package pickup or warehouse return handover).
class SlideToConfirmButton extends StatefulWidget {
  const SlideToConfirmButton({
    super.key,
    required this.label,
    required this.onConfirmed,
    this.subtitle,
    this.backgroundColor = AppColors.primary,
    this.icon = Icons.double_arrow_rounded,
    this.isLoading = false,
    this.enabled = true,
  });

  final String label;
  final String? subtitle;
  final Future<void> Function() onConfirmed;
  final Color backgroundColor;
  final IconData icon;
  final bool isLoading;
  final bool enabled;

  @override
  State<SlideToConfirmButton> createState() => _SlideToConfirmButtonState();
}

class _SlideToConfirmButtonState extends State<SlideToConfirmButton> {
  double _dragFraction = 0.0;
  bool _passedHalfwayHaptic = false;
  bool _submitting = false;

  bool get _busy => widget.isLoading || _submitting;

  Future<void> _triggerConfirm() async {
    if (_busy || !widget.enabled) return;
    HapticFeedback.heavyImpact();
    setState(() {
      _dragFraction = 1.0;
      _submitting = true;
    });
    try {
      await widget.onConfirmed();
    } finally {
      if (mounted) {
        setState(() {
          _submitting = false;
          _dragFraction = 0.0;
          _passedHalfwayHaptic = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const double height = 56;
    const double pad = 4;
    const double thumbSize = height - pad * 2;
    final effectiveBg = widget.enabled
        ? widget.backgroundColor
        : AppColors.surfaceVariant;

    return Semantics(
      button: true,
      enabled: widget.enabled && !_busy,
      label: widget.label,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final maxSlide = (constraints.maxWidth - thumbSize - pad * 2).clamp(
            1.0,
            double.infinity,
          );
          final thumbLeft = pad + (_dragFraction * maxSlide);

          return Container(
            height: height,
            decoration: BoxDecoration(
              color: effectiveBg,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Stack(
              alignment: Alignment.center,
              children: [
                // Center instruction label (fades slightly as thumb advances)
                Padding(
                  padding: const EdgeInsets.only(
                    left: thumbSize + 12,
                    right: 16,
                  ),
                  child: Opacity(
                    opacity: _busy
                        ? 1.0
                        : (1.0 - _dragFraction * 0.85).clamp(0.15, 1.0),
                    child: _busy
                        ? const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.4,
                                  color: Colors.white,
                                ),
                              ),
                              SizedBox(width: 10),
                              Text(
                                'Đang xác nhận…',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          )
                        : Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                widget.label,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: widget.enabled
                                      ? Colors.white
                                      : AppColors.textHint,
                                  fontSize: 15,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              if (widget.subtitle != null)
                                Text(
                                  widget.subtitle!,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: Colors.white.withValues(alpha: 0.84),
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                            ],
                          ),
                  ),
                ),

                // Draggable thumb
                if (!_busy)
                  AnimatedPositioned(
                    duration: _dragFraction == 0
                        ? const Duration(milliseconds: 220)
                        : Duration.zero,
                    curve: Curves.easeOutCubic,
                    left: thumbLeft,
                    top: pad,
                    child: GestureDetector(
                      onHorizontalDragUpdate: !widget.enabled
                          ? null
                          : (details) {
                              final next =
                                  (_dragFraction + details.delta.dx / maxSlide)
                                      .clamp(0.0, 1.0);
                              if (next >= 0.5 && !_passedHalfwayHaptic) {
                                _passedHalfwayHaptic = true;
                                HapticFeedback.selectionClick();
                              } else if (next < 0.5 && _passedHalfwayHaptic) {
                                _passedHalfwayHaptic = false;
                              }
                              setState(() => _dragFraction = next);
                            },
                      onHorizontalDragEnd: !widget.enabled
                          ? null
                          : (_) {
                              if (_dragFraction >= 0.8) {
                                _triggerConfirm();
                              } else {
                                setState(() {
                                  _dragFraction = 0.0;
                                  _passedHalfwayHaptic = false;
                                });
                              }
                            },
                      child: Container(
                        width: thumbSize,
                        height: thumbSize,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(11),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x260F172A),
                              blurRadius: 8,
                              offset: Offset(0, 2),
                            ),
                          ],
                        ),
                        child: Icon(
                          widget.icon,
                          color: widget.enabled
                              ? widget.backgroundColor
                              : AppColors.textHint,
                          size: 24,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
