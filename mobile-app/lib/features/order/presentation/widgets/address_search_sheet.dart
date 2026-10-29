// features/order/presentation/widgets/address_search_sheet.dart

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:swiftroute_app/core/theme/app_colors.dart';
import 'package:swiftroute_app/features/order/data/datasources/goong_remote_data_source.dart';
import 'package:swiftroute_app/features/order/data/models/goong_place_model.dart';

/// Modal bottom sheet allowing users to search Vietnamese addresses via Goong Place API.
class AddressSearchSheet extends ConsumerStatefulWidget {
  final String title;

  const AddressSearchSheet({
    super.key,
    required this.title,
  });

  static Future<GoongPlaceDetail?> show(BuildContext context, {required String title}) {
    return showModalBottomSheet<GoongPlaceDetail>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => AddressSearchSheet(title: title),
    );
  }

  @override
  ConsumerState<AddressSearchSheet> createState() => _AddressSearchSheetState();
}

class _AddressSearchSheetState extends ConsumerState<AddressSearchSheet> {
  final TextEditingController _searchController = TextEditingController();
  List<GoongPrediction> _predictions = [];
  bool _isLoading = false;
  Timer? _debounceTimer;

  @override
  void initState() {
    super.initState();
    // Pre-populate with popular suggestions when empty
    _performSearch('');
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  void _onQueryChanged(String query) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 300), () {
      _performSearch(query);
    });
  }

  Future<void> _performSearch(String query) async {
    setState(() => _isLoading = true);

    try {
      final results = await ref
          .read(goongRemoteDataSourceProvider)
          .searchPlaces(query);

      if (mounted) {
        setState(() {
          _predictions = results;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _onSelectPrediction(GoongPrediction prediction) async {
    setState(() => _isLoading = true);

    final detail = await ref
        .read(goongRemoteDataSourceProvider)
        .getPlaceDetail(prediction.placeId);

    if (mounted) {
      setState(() => _isLoading = false);
      Navigator.of(context).pop(detail);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.75,
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Drag handle
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.only(top: 12, bottom: 8),
            decoration: BoxDecoration(
              color: AppColors.divider,
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.title,
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: AppColors.textSecondary),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),

          // Search Input Field
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: TextField(
              controller: _searchController,
              autofocus: true,
              onChanged: _onQueryChanged,
              decoration: InputDecoration(
                hintText: 'Nhập tên đường, số nhà, địa điểm...',
                hintStyle: const TextStyle(color: AppColors.textHint, fontSize: 14),
                prefixIcon: const Icon(Icons.search, color: AppColors.primary),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          _searchController.clear();
                          _performSearch('');
                        },
                      )
                    : null,
                filled: true,
                fillColor: AppColors.surface,
                contentPadding: const EdgeInsets.symmetric(vertical: 12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),

          const SizedBox(height: 8),

          // Results list or loading indicator
          Expanded(
            child: _isLoading && _predictions.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : _predictions.isEmpty
                    ? const Center(
                        child: Text(
                          'Không tìm thấy địa chỉ phù hợp',
                          style: TextStyle(color: AppColors.textSecondary),
                        ),
                      )
                    : ListView.separated(
                        itemCount: _predictions.length,
                        separatorBuilder: (_, __) => const Divider(
                          height: 1,
                          color: AppColors.divider,
                          indent: 56,
                        ),
                        itemBuilder: (context, index) {
                          final item = _predictions[index];
                          return ListTile(
                            leading: Container(
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: AppColors.primary.withAlpha(25),
                                shape: BoxShape.circle,
                              ),
                              child: const Icon(
                                Icons.location_on_outlined,
                                color: AppColors.primary,
                                size: 20,
                              ),
                            ),
                            title: Text(
                              item.mainText,
                              style: const TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 15,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            subtitle: Text(
                              item.secondaryText,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.textSecondary,
                              ),
                            ),
                            onTap: () => _onSelectPrediction(item),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
