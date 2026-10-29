package com.thupo.managerShop.supplychain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderCreateRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderResponse;
import com.thupo.managerShop.supplychain.dto.SupplierCreateRequest;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import com.thupo.managerShop.supplychain.service.PurchaseOrderService;
import com.thupo.managerShop.supplychain.service.SupplierService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PurchaseOrderController.class, SupplierController.class})
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PurchaseOrderService purchaseOrderService;

    @MockBean
    private SupplierService supplierService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC bắt buộc: STAFF gọi POST /api/v1/purchase-orders/1/receive trả 403 kèm requiredPermissions MANAGE_INVENTORY")
    @WithMockUser(roles = "STAFF")
    void staffCallingReceiveReturns403WithRequiredPermissions() throws Exception {
        PurchaseOrderReceiveRequest request = new PurchaseOrderReceiveRequest(
                "IDEM-1", "Ghi chú nhận", List.of(new PurchaseOrderReceiveItemRequest(10L, 5)));

        mockMvc.perform(post("/api/v1/purchase-orders/1/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_INVENTORY"));
    }

    @Test
    @DisplayName("RBAC: ADMIN có quyền gọi POST /api/v1/purchase-orders/1/receive thành công 200 OK")
    @WithMockUser(roles = "ADMIN")
    void adminCallingReceiveReturns200Ok() throws Exception {
        PurchaseOrderReceiveRequest request = new PurchaseOrderReceiveRequest(
                "IDEM-1", "Ghi chú nhận", List.of(new PurchaseOrderReceiveItemRequest(10L, 5)));

        PurchaseOrderResponse mockResp = new PurchaseOrderResponse(
                1L, "PO-000001", 1L, "Công ty May", 1L, "Kho Tổng",
                "REF-1", "Ghi chú", "Net 30", "VND", List.of(),
                PurchaseOrderStatus.PARTIALLY_RECEIVED,
                BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.valueOf(500000),
                Instant.now(), List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(purchaseOrderService.receivePurchaseOrder(eq(1L), any(PurchaseOrderReceiveRequest.class), any()))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/purchase-orders/1/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.poNumber").value("PO-000001"))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RECEIVED"));
    }

    @Test
    @DisplayName("Nghiệp vụ đọc: STAFF xem danh sách đơn hàng GET /api/v1/purchase-orders thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void staffCallingGetPurchaseOrdersReturns200Ok() throws Exception {
        ApiResponse<List<PurchaseOrderResponse>> apiResponse = ApiResponse.of(
                List.of(),
                new PageMeta(0, 20, 0L, 0)
        );

        when(purchaseOrderService.getPurchaseOrders(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/purchase-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("Tương thích OpenAPI / HAR alias: GET /api/v1/purchase-orders/inventory-purchase-orders-list thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void getPurchaseOrdersAliasReturns200Ok() throws Exception {
        ApiResponse<List<PurchaseOrderResponse>> apiResponse = ApiResponse.of(
                List.of(),
                new PageMeta(0, 20, 0L, 0)
        );

        when(purchaseOrderService.getPurchaseOrders(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/purchase-orders/inventory-purchase-orders-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("RBAC: STAFF gọi POST /api/v1/suppliers trả 403 kèm requiredPermissions MANAGE_INVENTORY")
    @WithMockUser(roles = "STAFF")
    void staffCallingPostSuppliersReturns403WithRequiredPermissions() throws Exception {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "Nhà cung cấp mới", "test@sup.vn", "090000000", "HN", "Anh B");

        mockMvc.perform(post("/api/v1/suppliers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_INVENTORY"));
    }

    @Test
    @DisplayName("ADMIN tạo đơn đặt hàng mới POST /api/v1/purchase-orders thành công 201 Created")
    @WithMockUser(roles = "ADMIN")
    void adminCallingCreatePurchaseOrderReturns201Created() throws Exception {
        PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest(
                1L, 1L, "REF-100", "Ghi chú", "Net 30", "VND", List.of(), BigDecimal.ZERO,
                List.of(new PurchaseOrderItemRequest(101L, 10, BigDecimal.valueOf(50000)))
        );

        PurchaseOrderResponse mockResp = new PurchaseOrderResponse(
                5L, "PO-000005", 1L, "Công ty May", 1L, "Kho Tổng",
                "REF-100", "Ghi chú", "Net 30", "VND", List.of(),
                PurchaseOrderStatus.DRAFT,
                BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.valueOf(500000),
                null, List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(purchaseOrderService.createPurchaseOrder(any(PurchaseOrderCreateRequest.class)))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.poNumber").value("PO-000005"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Nghiệp vụ 409: Nhận vượt quá số lượng đặt trả về RFC 7807 Conflict ProblemDetails")
    @WithMockUser(roles = "ADMIN")
    void receiveConflictReturns409ProblemDetails() throws Exception {
        PurchaseOrderReceiveRequest request = new PurchaseOrderReceiveRequest(
                "IDEM-1", "Lỗi nhận lố", List.of(new PurchaseOrderReceiveItemRequest(10L, 999)));

        when(purchaseOrderService.receivePurchaseOrder(eq(1L), any(PurchaseOrderReceiveRequest.class), any()))
                .thenThrow(new ConflictException("Số lượng nhận (999) vượt quá số lượng đặt (10)"));

        mockMvc.perform(post("/api/v1/purchase-orders/1/receive")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Số lượng nhận (999) vượt quá số lượng đặt (10)"));
    }
}
