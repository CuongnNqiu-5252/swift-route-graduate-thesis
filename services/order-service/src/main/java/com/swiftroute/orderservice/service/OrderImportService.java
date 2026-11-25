package com.swiftroute.orderservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import jakarta.validation.Validator;

@Service
public class OrderImportService {
    private static final String PENDING = "NEEDS_ADDRESS";
    private static final String CREATED = "CREATED";
    private static final String DUPLICATE = "DUPLICATE";

    public record Row(int line, String externalOrderCode, String recipientName, String recipientPhone,
            String deliveryAddress, Double deliveryLat, Double deliveryLng,
            OffsetDateTime timeWindowStart, OffsetDateTime timeWindowEnd, Double weightKg,
            BigDecimal codAmount, String notes) {
        CreateOrderRequest withAddress(String address, Double lat, Double lng) {
            return new CreateOrderRequest(externalOrderCode.trim(), null, null, recipientName, recipientPhone,
                    null, null, null, null, lat, lng, address, timeWindowStart, timeWindowEnd,
                    weightKg, null, null, notes, null, codAmount);
        }
    }

    public record Result(int line, String externalOrderCode, String status, String errorCode, String message) {
    }

    public record Preview(List<Row> rows, List<Result> errors) {
    }

    public record Pending(String externalOrderCode, String recipientName, String deliveryAddress) {
    }

    public record Address(String deliveryAddress, Double deliveryLat, Double deliveryLng) {
    }

    public record ImportResult() {
    }

    private final JdbcTemplate jdbc;
    private final Validator validator;
    private final OrderService orders;
    private final TransactionTemplate transactions;

    public OrderImportService(JdbcTemplate jdbc, Validator validator, OrderService orders,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.validator = validator;
        this.orders = orders;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<Result> importExcel(
            MultipartFile file,
            UUID tenant,
            UUID user,
            String role) {
        Preview preview = previewExcel(file);
        List<Result> results = new ArrayList<>(preview.errors());
        results.addAll(submit(preview.rows(), tenant, user, role));
        results.sort(Comparator.comparingInt(Result::line));
        return results;
    }

    public Preview previewExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File Excel không được để trống");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Chỉ hỗ trợ file Excel (.xlsx)");
        }

        List<Row> validRows = new ArrayList<>();
        List<Result> invalidResults = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream input = file.getInputStream();
                XSSFWorkbook workbook = new XSSFWorkbook(input)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "File Excel không có dữ liệu vận đơn");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "File Excel không có dữ liệu vận đơn");
            }

            Map<String, Integer> columns = readHeaders(sheet.getRow(0), formatter);
            int dataRowCount = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlankRow(excelRow, formatter)) {
                    continue;
                }
                dataRowCount++;
                if (dataRowCount > 200) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Mỗi file chỉ được chứa tối đa 200 dòng vận đơn.");
                }

                int line = i + 1;
                String externalCode = readCell(excelRow, columns, "externalOrderCode", formatter);

                try {
                    Row row = parseExcelRow(excelRow, columns, line, formatter);
                    validRows.add(row);
                } catch (IllegalArgumentException ex) {
                    invalidResults.add(new Result(
                            line,
                            externalCode.isBlank() ? "DÒNG-" + line : externalCode,
                            "INVALID",
                            "INVALID_EXCEL_DATA",
                            ex.getMessage()));
                }
            }

            if (dataRowCount == 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "File Excel không có dòng vận đơn nào.");
            }

        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Không thể đọc nội dung file Excel: " + ex.getMessage());
        }

        return new Preview(validRows, invalidResults);
    }

    public List<Result> submit(List<Row> rows, UUID tenant, UUID user, String role) {
        return rows.stream().map(row -> {
            try {
                String status = transactions.execute(transaction -> importRow(row, tenant, user, role));
                return new Result(row.line(), row.externalOrderCode(), status, null, null);
            } catch (AppException error) {
                return new Result(row.line(), row.externalOrderCode(), "INVALID",
                        error.getErrorCode().name(), error.getMessage());
            }
        }).toList();
    }

    private String importRow(Row row, UUID tenant, UUID user, String role) {
        if (row.externalOrderCode() == null || row.externalOrderCode().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "externalOrderCode: bắt buộc");
        }
        boolean pending = row.deliveryAddress() == null || row.deliveryAddress().isBlank()
                || !validCoordinate(row.deliveryLat(), 90) || !validCoordinate(row.deliveryLng(), 180);
        if (row.deliveryAddress() != null && row.deliveryAddress().length() > 500) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "deliveryAddress: tối đa 500 ký tự");
        }
        var request = row.withAddress(row.deliveryAddress(), row.deliveryLat(), row.deliveryLng());
        validate(request, pending);
        if (pending) {
            int inserted = jdbc.update("""
                    INSERT INTO order_import_rows (tenant_id, source_code, recipient_name, recipient_phone,
                        original_address, window_start, window_end, weight_kg, cod_amount, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, tenant, request.externalOrderCode(), row.recipientName(), row.recipientPhone(),
                    row.deliveryAddress(), row.timeWindowStart(), row.timeWindowEnd(), row.weightKg(),
                    request.codAmount(), row.notes());
            if (inserted == 0)
                return DUPLICATE;
            if (!orderExists(tenant, request.externalOrderCode()))
                return PENDING;
            jdbc.update("DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ?",
                    tenant, request.externalOrderCode());
            return DUPLICATE;
        }
        int reserved = jdbc.update("""
                INSERT INTO order_import_rows (tenant_id, source_code) VALUES (?, ?) ON CONFLICT DO NOTHING
                """, tenant, request.externalOrderCode());
        if (reserved == 0)
            return DUPLICATE;
        if (orderExists(tenant, request.externalOrderCode())) {
            jdbc.update("DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ?",
                    tenant, request.externalOrderCode());
            return DUPLICATE;
        }
        markCreated(tenant, request, user, role);
        return CREATED;
    }

    public List<Pending> pending(UUID tenant, String after) {
        return jdbc.query("""
                SELECT source_code, recipient_name, original_address FROM order_import_rows
                WHERE tenant_id = ? AND order_id IS NULL AND source_code > ? ORDER BY source_code LIMIT 51
                """, (rs, n) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3)), tenant, after);
    }

    public UUID complete(UUID tenant, UUID user, String role, String code, Address address) {
        return transactions.execute(transaction -> {
            var rows = jdbc.query("""
                    SELECT * FROM order_import_rows WHERE tenant_id = ? AND source_code = ? FOR UPDATE
                    """, (rs, n) -> new Row(0, rs.getString("source_code"), rs.getString("recipient_name"),
                    rs.getString("recipient_phone"), null, null, null,
                    rs.getObject("window_start", OffsetDateTime.class),
                    rs.getObject("window_end", OffsetDateTime.class), rs.getObject("weight_kg", Double.class),
                    rs.getBigDecimal("cod_amount"), rs.getString("notes")), tenant, code);
            if (rows.isEmpty())
                throw new AppException(ErrorCode.ORDER_NOT_FOUND);
            UUID existing = jdbc.queryForObject("""
                    SELECT order_id FROM order_import_rows WHERE tenant_id = ? AND source_code = ?
                    """, UUID.class, tenant, code);
            if (existing != null)
                return existing;
            var request = rows.getFirst().withAddress(address.deliveryAddress(), address.deliveryLat(),
                    address.deliveryLng());
            validate(request, false);
            if (orderExists(tenant, code)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã đơn nguồn đã có vận đơn");
            }
            return markCreated(tenant, request, user, role);
        });
    }

    private boolean orderExists(UUID tenant, String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM orders WHERE tenant_id = ? AND external_order_code = ?)
                """, Boolean.class, tenant, code));
    }

    public boolean cancel(UUID tenant, String code) {
        return jdbc.update("""
                DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ? AND order_id IS NULL
                """, tenant, code) == 1;
    }

    private UUID markCreated(UUID tenant, CreateOrderRequest request, UUID user, String role) {
        UUID orderId = orders.createOrder(request, user, tenant, role).orderId();
        jdbc.update("""
                UPDATE order_import_rows SET order_id = ?, recipient_name = NULL, recipient_phone = NULL,
                    original_address = NULL, window_start = NULL, window_end = NULL, weight_kg = NULL,
                    cod_amount = NULL, notes = NULL WHERE tenant_id = ? AND source_code = ?
                """, orderId, tenant, request.externalOrderCode());
        return orderId;
    }

    private static boolean validCoordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= limit;
    }

    private void validate(CreateOrderRequest request, boolean allowMissingPoint) {
        validator.validate(request).stream()
                .filter(v -> !allowMissingPoint || switch (v.getPropertyPath().toString()) {
                    case "deliveryAddress", "deliveryLat", "deliveryLng" -> false;
                    default -> true;
                })
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().findFirst()
                .ifPresent(message -> {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, message);
                });
        if (!Double.isFinite(request.weightKg())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "weightKg: không hợp lệ");
        }
        if (request.codAmount().stripTrailingZeros().scale() > 0
                || request.codAmount().compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "codAmount: yêu cầu số nguyên tối đa 12 chữ số");
        }
        if (!request.hasValidTimeWindow()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Khung giờ hẹn cần đủ hai mốc và giờ kết thúc phải sau giờ bắt đầu");
        }
    }

    private Row parseExcelRow(
            org.apache.poi.ss.usermodel.Row excelRow,
            Map<String, Integer> columns,
            int line,
            DataFormatter formatter) {
        String externalOrderCode = readCell(
                excelRow, columns, "externalOrderCode", formatter);

        String recipientName = readCell(
                excelRow, columns, "recipientName", formatter);

        String recipientPhone = readCell(
                excelRow, columns, "recipientPhone", formatter);

        String deliveryAddress = readCell(
                excelRow, columns, "deliveryAddress", formatter);

        Double deliveryLat = parseDouble(
                readCell(excelRow, columns, "deliveryLat", formatter),
                "deliveryLat");

        Double deliveryLng = parseDouble(
                readCell(excelRow, columns, "deliveryLng", formatter),
                "deliveryLng");

        OffsetDateTime timeWindowStart = parseDate(
                excelRow, columns, "timeWindowStart", formatter);

        OffsetDateTime timeWindowEnd = parseDate(
                excelRow, columns, "timeWindowEnd", formatter);

        Double weightKg = parseDouble(
                readCell(excelRow, columns, "weightKg", formatter),
                "weightKg");

        BigDecimal codAmount = parseDecimal(
                readCell(excelRow, columns, "codAmount", formatter),
                "codAmount");

        String notes = readCell(
                excelRow, columns, "notes", formatter);

        if (externalOrderCode.isBlank()) {
            throw new IllegalArgumentException(
                    "externalOrderCode không được để trống");
        }

        return new Row(
                line,
                externalOrderCode,
                recipientName,
                recipientPhone,
                deliveryAddress,
                deliveryLat,
                deliveryLng,
                timeWindowStart,
                timeWindowEnd,
                weightKg,
                codAmount,
                notes);
    }

    private String readCell(
            org.apache.poi.ss.usermodel.Row row,
            Map<String, Integer> columns,
            String columnName,
            DataFormatter formatter) {
        Integer index = columns.get(columnName);
        if (index == null) {
            index = columns.get(columnName.toLowerCase(Locale.ROOT));
        }
        if (index == null) {
            index = columns.get(toCamelCase(columnName));
        }

        if (index == null) {
            return "";
        }

        Cell cell = row.getCell(
                index,
                org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

        if (cell == null) {
            return "";
        }

        return formatter.formatCellValue(cell).trim();
    }

    private Double parseDouble(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            double result = Double.parseDouble(value);

            if (!Double.isFinite(result)) {
                throw new NumberFormatException();
            }

            return result;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    field + " phải là số hợp lệ");
        }
    }

    private BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    field + " phải là số hợp lệ");
        }
    }

    private OffsetDateTime parseDate(
            org.apache.poi.ss.usermodel.Row row,
            Map<String, Integer> columns,
            String field,
            DataFormatter formatter) {
        Integer index = columns.get(field);

        if (index == null) {
            return null;
        }

        Cell cell = row.getCell(index);

        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {

            return cell.getLocalDateTimeCellValue()
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toOffsetDateTime();
        }

        String value = readCell(row, columns, field, formatter);

        if (value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return java.time.LocalDateTime.parse(value.replace(" ", "T"))
                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .toOffsetDateTime();
            } catch (Exception ignored) {
                throw new IllegalArgumentException(
                        field + " không đúng định dạng ngày giờ (ISO-8601)");
            }
        }
    }

    private Map<String, Integer> readHeaders(
            org.apache.poi.ss.usermodel.Row header,
            DataFormatter formatter) {
        if (header == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File Excel thiếu dòng tiêu đề");
        }

        Map<String, Integer> columns = new HashMap<>();

        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isEmpty()) {
                columns.put(name, cell.getColumnIndex());
                columns.put(name.toLowerCase(Locale.ROOT), cell.getColumnIndex());
                columns.put(toCamelCase(name), cell.getColumnIndex());
            }
        }

        List<String> requiredHeaders = List.of(
                "externalOrderCode",
                "recipientName",
                "recipientPhone",
                "weightKg");

        for (String name : requiredHeaders) {
            if (!columns.containsKey(name) && !columns.containsKey(name.toLowerCase(Locale.ROOT))) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu cột bắt buộc trong file Excel: " + name);
            }
        }

        return columns;
    }

    private static String toCamelCase(String s) {
        if (s == null || s.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '-' || Character.isWhitespace(c)) {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private boolean isBlankRow(
            org.apache.poi.ss.usermodel.Row row,
            DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell)
                    .trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
