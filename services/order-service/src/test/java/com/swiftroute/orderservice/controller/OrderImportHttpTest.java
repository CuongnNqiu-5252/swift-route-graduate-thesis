package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.swiftroute.orderservice.exception.GlobalExceptionHandler;
import com.swiftroute.orderservice.security.JwtTokenValidator;
import com.swiftroute.orderservice.security.TenantRequestInterceptor;
import com.swiftroute.orderservice.service.OrderImportService;

import io.jsonwebtoken.Claims;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class OrderImportHttpTest {
    @Test
    void createPermissionCoversTheImportWorkflowAndJsonMatchesWebAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        var imports = mock(OrderImportService.class);
        var tokens = mock(JwtTokenValidator.class);
        var creator = claims(tenant, user, "order:create");
        var reader = claims(tenant, user, "order:read");
        when(tokens.parseAndValidate("Bearer creator")).thenReturn(creator);
        when(tokens.parseAndValidate("Bearer reader")).thenReturn(reader);

        var mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        var mvc = MockMvcBuilders.standaloneSetup(new OrderImportController(imports))
                .addInterceptors(new TenantRequestInterceptor(tokens))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();

        when(imports.previewExcel(any())).thenCallRealMethod();
        var excel = sampleExcel();
        mvc.perform(multipart("/api/orders/import/file/preview").file(excel)
                .header("Authorization", "Bearer creator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].line").value(2))
                .andExpect(jsonPath("$.data.rows[0].external_order_code").value("XLS-01"))
                .andExpect(jsonPath("$.data.rows[0].recipient_phone").value("0900000000"))
                .andExpect(jsonPath("$.data.errors[0].line").value(3));
        mvc.perform(multipart("/api/orders/import/file/preview").file(excel)
                .header("Authorization", "Bearer reader"))
                .andExpect(status().isForbidden());
        verify(imports, never()).submit(anyList(), any(), any(), any());

        when(imports.submit(anyList(), eq(tenant), eq(user), eq("OPERATOR"))).thenAnswer(call -> {
            List<OrderImportService.Row> rows = call.getArgument(0);
            var row = rows.getFirst();
            assertThat(row.recipientPhone()).isEqualTo("0900000000");
            assertThat(row.codAmount()).isEqualByComparingTo(new BigDecimal("125000"));
            assertThat(row.timeWindowStart().toInstant().toString()).isEqualTo("2026-11-16T01:00:00Z");
            return List.of(new OrderImportService.Result(row.line(), row.externalOrderCode(), "NEEDS_ADDRESS", null, null));
        });
        when(imports.pending(tenant, "")).thenReturn(List.of(new OrderImportService.Pending("CSV-01", "Nguyễn Văn A", null)));
        when(imports.cancel(tenant, "CSV-02")).thenReturn(true);
        when(imports.complete(tenant, user, "OPERATOR", "CSV-01",
                new OrderImportService.Address("225 đường 3/2", 10.77, 106.67))).thenReturn(order);

        mvc.perform(post("/api/orders/import").header("Authorization", "Bearer creator")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        [{"line":2,"external_order_code":"CSV-01","recipient_name":"Nguyễn Văn A",
                          "recipient_phone":"0900000000","time_window_start":"2026-11-16T08:00:00+07:00",
                          "time_window_end":"2026-11-16T12:00:00+07:00","weight_kg":1.5,"cod_amount":"125000"}]
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].external_order_code").value("CSV-01"))
                .andExpect(jsonPath("$.data[0].status").value("NEEDS_ADDRESS"));
        mvc.perform(get("/api/orders/import/pending").header("Authorization", "Bearer creator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recipient_name").value("Nguyễn Văn A"));
        mvc.perform(post("/api/orders/import/pending/complete").header("Authorization", "Bearer creator")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"external_order_code":"CSV-01","address":{
                            "delivery_address":"225 đường 3/2","delivery_lat":10.77,"delivery_lng":106.67}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(order.toString()));
        mvc.perform(get("/api/orders/import/pending").header("Authorization", "Bearer reader"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/import/pending/cancel").header("Authorization", "Bearer creator")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"external_order_code":"CSV-02"}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(post("/api/orders/import/pending/cancel").header("Authorization", "Bearer reader")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"external_order_code":"CSV-02"}
                        """))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/import/pending").header("Authorization", "Bearer creator")
                .header("X-Tenant-Id", UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }

    private MockMultipartFile sampleExcel() throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Orders");
            var headers = sheet.createRow(0);
            var fields = List.of("externalOrderCode", "recipientName", "recipientPhone", "weightKg", "deliveryAddress");
            for (int i = 0; i < fields.size(); i++) {
                headers.createCell(i).setCellValue(fields.get(i));
            }
            var order = sheet.createRow(1);
            order.createCell(0).setCellValue("XLS-01");
            order.createCell(1).setCellValue("Nguyễn Văn A");
            order.createCell(2).setCellValue("0900000000");
            order.createCell(3).setCellValue(1.5);
            order.createCell(4).setCellValue("225 đường 3/2");
            var invalid = sheet.createRow(2);
            invalid.createCell(1).setCellValue("Thiếu mã");
            workbook.write(bytes);
            return new MockMultipartFile("file", "orders.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    private Claims claims(UUID tenant, UUID user, String permission) {
        var claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(user.toString());
        when(claims.get("system_role", String.class)).thenReturn("USER");
        when(claims.get("tenant_id", String.class)).thenReturn(tenant.toString());
        when(claims.get("tenant_role", String.class)).thenReturn("OPERATOR");
        when(claims.get("permissions")).thenReturn(List.of(permission));
        return claims;
    }
}
