package com.thupo.managerShop.supplychain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentItem;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentRequest;
import com.thupo.managerShop.supplychain.dto.InventoryLevelResponse;
import com.thupo.managerShop.supplychain.dto.InventoryMovementResponse;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.service.InventoryService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryController.class)
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC bắt buộc: STAFF gọi POST /api/v1/inventory/adjustments trả 403 kèm requiredPermissions MANAGE_INVENTORY")
    @WithMockUser(roles = "STAFF")
    void staffCallingAdjustmentsReturns403WithRequiredPermissions() throws Exception {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(
                List.of(new InventoryAdjustmentItem(1L, 1L, 10, null, "Kiểm kê định kỳ")),
                "ADJUSTMENT",
                "REF-001"
        );

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_INVENTORY"));
    }

    @Test
    @DisplayName("RBAC: ADMIN có quyền điều chỉnh tồn kho gọi POST /api/v1/inventory/adjustments thành công 200 OK")
    @WithMockUser(roles = "ADMIN")
    void adminCallingAdjustmentsReturns200Ok() throws Exception {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(
                List.of(new InventoryAdjustmentItem(1L, 1L, 10, null, "Kiểm kê định kỳ")),
                "ADJUSTMENT",
                "REF-001"
        );

        InventoryLevelResponse mockResponse = new InventoryLevelResponse(
                10L, 1L, "Đen / L", "SKU-BLK-L",
                100L, "Áo thun", 1L, "Kho Tổng Hà Nội",
                60, 60, 0, 0, 0, Instant.now()
        );

        when(inventoryService.adjustBatch(any(InventoryAdjustmentRequest.class), any()))
                .thenReturn(List.of(mockResponse));

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].variantId").value(1))
                .andExpect(jsonPath("$.data[0].available").value(60));
    }

    @Test
    @DisplayName("Nghiệp vụ đọc: STAFF xem danh sách tồn kho GET /api/v1/inventory thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void staffCallingGetInventoryReturns200Ok() throws Exception {
        InventoryLevelResponse mockLevel = new InventoryLevelResponse(
                10L, 1L, "Đen / L", "SKU-BLK-L",
                100L, "Áo thun", 1L, "Kho Tổng Hà Nội",
                50, 50, 0, 0, 0, Instant.now()
        );

        ApiResponse<List<InventoryLevelResponse>> apiResponse = ApiResponse.of(
                List.of(mockLevel),
                new PageMeta(0, 20, 1L, 1)
        );

        when(inventoryService.getInventory(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].variantId").value(1))
                .andExpect(jsonPath("$.data[0].locationName").value("Kho Tổng Hà Nội"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("Nghiệp vụ đọc: STAFF xem lịch sử biến động sổ cái GET /api/v1/inventory/movements thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void staffCallingGetMovementsReturns200Ok() throws Exception {
        InventoryMovementResponse mockMovement = new InventoryMovementResponse(
                500L, 1L, "Đen / L", "SKU-BLK-L",
                1L, "Kho Tổng Hà Nội",
                MovementType.ADJUSTMENT, 10,
                40, 50, 40, 50,
                "Kiểm kê", "ADJUSTMENT", "REF-1",
                1L, "admin@shop.com", Instant.now()
        );

        ApiResponse<List<InventoryMovementResponse>> apiResponse = ApiResponse.of(
                List.of(mockMovement),
                new PageMeta(0, 20, 1L, 1)
        );

        when(inventoryService.getMovements(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/inventory/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(500))
                .andExpect(jsonPath("$.data[0].movementType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data[0].quantityDelta").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("Tương thích OpenAPI / HAR alias: GET /api/v1/inventory/inventory-resource-index-items thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void getInventoryAliasReturns200Ok() throws Exception {
        ApiResponse<List<InventoryLevelResponse>> apiResponse = ApiResponse.of(
                List.of(),
                new PageMeta(0, 20, 0L, 0)
        );

        when(inventoryService.getInventory(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/inventory/inventory-resource-index-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("Nghiệp vụ 409: Điều chỉnh vượt quá tồn kho trả về RFC 7807 Conflict ProblemDetails")
    @WithMockUser(roles = "ADMIN")
    void adjustmentsConflictReturns409ProblemDetails() throws Exception {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(
                List.of(new InventoryAdjustmentItem(1L, 1L, -100, null, "Xuất lố")),
                "ADJUSTMENT",
                "REF-001"
        );

        when(inventoryService.adjustBatch(any(InventoryAdjustmentRequest.class), any()))
                .thenThrow(new ConflictException("Không đủ tồn kho khả dụng cho biến thể ID 1"));

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Không đủ tồn kho khả dụng cho biến thể ID 1"));
    }
}
