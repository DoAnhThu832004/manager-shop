package com.thupo.managerShop.supplychain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.supplychain.dto.InventoryTransferCreateRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferItemRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferResponse;
import com.thupo.managerShop.supplychain.entity.TransferStatus;
import com.thupo.managerShop.supplychain.service.InventoryTransferService;
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

@WebMvcTest(controllers = TransferController.class)
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryTransferService inventoryTransferService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC bắt buộc: STAFF gọi POST /api/v1/transfers/1/send trả 403 kèm requiredPermissions MANAGE_INVENTORY")
    @WithMockUser(roles = "STAFF")
    void staffCallingSendReturns403WithRequiredPermissions() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/1/send")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_INVENTORY"));
    }

    @Test
    @DisplayName("RBAC: ADMIN có quyền gọi POST /api/v1/transfers/1/send thành công 200 OK")
    @WithMockUser(roles = "ADMIN")
    void adminCallingSendReturns200Ok() throws Exception {
        InventoryTransferResponse mockResp = new InventoryTransferResponse(
                1L, "TR-000001", 1L, "Kho Q1", 2L, "Kho HN",
                null, "Tham chiếu", "Ghi chú", List.of(), null, null,
                TransferStatus.IN_TRANSIT, List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(inventoryTransferService.sendTransfer(eq(1L), any()))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/transfers/1/send")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.transferNumber").value("TR-000001"))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("Nghiệp vụ đọc: STAFF xem danh sách chuyển kho GET /api/v1/transfers thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void staffCallingGetTransfersReturns200Ok() throws Exception {
        ApiResponse<List<InventoryTransferResponse>> apiResponse = ApiResponse.of(
                List.of(),
                new PageMeta(0, 20, 0L, 0)
        );

        when(inventoryTransferService.getTransfers(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("Tương thích OpenAPI / HAR alias: GET /api/v1/transfers/unified-transfers-list thành công 200 OK")
    @WithMockUser(roles = "STAFF")
    void getTransfersAliasReturns200Ok() throws Exception {
        ApiResponse<List<InventoryTransferResponse>> apiResponse = ApiResponse.of(
                List.of(),
                new PageMeta(0, 20, 0L, 0)
        );

        when(inventoryTransferService.getTransfers(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(apiResponse);

        mockMvc.perform(get("/api/v1/transfers/unified-transfers-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("RBAC: STAFF gọi POST /api/v1/transfers tạo mới trả 403 kèm requiredPermissions MANAGE_INVENTORY")
    @WithMockUser(roles = "STAFF")
    void staffCallingPostTransfersReturns403WithRequiredPermissions() throws Exception {
        InventoryTransferCreateRequest request = new InventoryTransferCreateRequest(
                1L, 2L, null, "Tham chiếu", null, List.of(), null,
                List.of(new InventoryTransferItemRequest(101L, 5))
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_INVENTORY"));
    }

    @Test
    @DisplayName("ADMIN tạo đơn chuyển kho mới POST /api/v1/transfers thành công 201 Created")
    @WithMockUser(roles = "ADMIN")
    void adminCallingCreateTransferReturns201Created() throws Exception {
        InventoryTransferCreateRequest request = new InventoryTransferCreateRequest(
                1L, 2L, null, "Tham chiếu", null, List.of(), null,
                List.of(new InventoryTransferItemRequest(101L, 5))
        );

        InventoryTransferResponse mockResp = new InventoryTransferResponse(
                10L, "TR-000010", 1L, "Kho Q1", 2L, "Kho HN",
                null, "Tham chiếu", null, List.of(), null, null,
                TransferStatus.DRAFT, List.of(), List.of(), Instant.now(), Instant.now()
        );

        when(inventoryTransferService.createTransfer(any(InventoryTransferCreateRequest.class)))
                .thenReturn(mockResp);

        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.transferNumber").value("TR-000010"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Nghiệp vụ 409: Gửi hàng khi kho nguồn không đủ tồn trả về RFC 7807 Conflict ProblemDetails")
    @WithMockUser(roles = "ADMIN")
    void sendTransferConflictReturns409ProblemDetails() throws Exception {
        when(inventoryTransferService.sendTransfer(eq(1L), any()))
                .thenThrow(new ConflictException("INSUFFICIENT_STOCK: Kho nguồn không đủ tồn kho khả dụng"));

        mockMvc.perform(post("/api/v1/transfers/1/send")
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("INSUFFICIENT_STOCK: Kho nguồn không đủ tồn kho khả dụng"));
    }
}
