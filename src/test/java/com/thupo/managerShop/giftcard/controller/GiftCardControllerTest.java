package com.thupo.managerShop.giftcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.giftcard.dto.GiftCardCreateRequest;
import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardResponse;
import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import com.thupo.managerShop.giftcard.service.GiftCardService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GiftCardController.class)
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class GiftCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GiftCardService giftCardService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC: STAFF gọi POST /api/v1/gift-cards trả 403 kèm requiredPermissions MANAGE_GIFT_CARDS")
    @WithMockUser(roles = "STAFF")
    void staffCallingCreateGiftCardReturns403WithRequiredPermissions() throws Exception {
        GiftCardCreateRequest req = new GiftCardCreateRequest(
                null, BigDecimal.valueOf(50000.00), null, null, "Test RBAC"
        );

        mockMvc.perform(post("/api/v1/gift-cards")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_GIFT_CARDS"));
    }

    @Test
    @DisplayName("Nghiệp vụ RBAC: STAFF gọi PUT /api/v1/gift-cards/1/disable trả 403 kèm requiredPermissions MANAGE_GIFT_CARDS")
    @WithMockUser(roles = "STAFF")
    void staffCallingDisableGiftCardReturns403WithRequiredPermissions() throws Exception {
        mockMvc.perform(put("/api/v1/gift-cards/1/disable")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_GIFT_CARDS"));
    }

    @Test
    @DisplayName("ADMIN gọi POST /api/v1/gift-cards -> trả 201 Created kèm raw code đúng MỘT LẦN")
    @WithMockUser(roles = "ADMIN")
    void adminCallingCreateGiftCardReturns201WithRawCode() throws Exception {
        GiftCardCreateRequest req = new GiftCardCreateRequest(
                null, BigDecimal.valueOf(100000.00), null, null, "Tạo thẻ admin"
        );

        GiftCardCreatedResponse createdResp = new GiftCardCreatedResponse(
                1L,
                "xyz987654321abcd",
                "************abcd",
                "abcd",
                BigDecimal.valueOf(100000.00),
                BigDecimal.valueOf(100000.00),
                "VND",
                null,
                null,
                "Tạo thẻ admin",
                GiftCardStatus.ACTIVE,
                1L,
                0L,
                Instant.now(),
                Instant.now(),
                List.of()
        );

        when(giftCardService.createGiftCard(any(), any())).thenReturn(createdResp);

        mockMvc.perform(post("/api/v1/gift-cards")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("xyz987654321abcd"))
                .andExpect(jsonPath("$.data.maskedCode").value("************abcd"))
                .andExpect(jsonPath("$.data.initialValue").value(100000.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("User đã đăng nhập gọi GET /api/v1/gift-cards/1 -> trả 200 OK kèm maskedCode, không có mã gốc")
    @WithMockUser(roles = "STAFF")
    void authenticatedUserCallingGetGiftCardReturns200WithMaskedCode() throws Exception {
        GiftCardResponse cardResp = new GiftCardResponse(
                1L,
                "************abcd",
                "abcd",
                BigDecimal.valueOf(100000.00),
                BigDecimal.valueOf(100000.00),
                "VND",
                null,
                null,
                "Chi tiết",
                GiftCardStatus.ACTIVE,
                null,
                1L,
                0L,
                Instant.now(),
                Instant.now(),
                List.of()
        );

        when(giftCardService.getGiftCard(1L)).thenReturn(cardResp);

        mockMvc.perform(get("/api/v1/gift-cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.maskedCode").value("************abcd"))
                .andExpect(jsonPath("$.data.code").doesNotExist());
    }

    @Test
    @DisplayName("User đã đăng nhập gọi GET /api/v1/gift-cards/gift-card-list (Alias) -> trả 200 OK")
    @WithMockUser(roles = "STAFF")
    void authenticatedUserCallingGetGiftCardsAliasReturns200() throws Exception {
        GiftCardResponse cardResp = new GiftCardResponse(
                1L,
                "************abcd",
                "abcd",
                BigDecimal.valueOf(100000.00),
                BigDecimal.valueOf(100000.00),
                "VND",
                null,
                null,
                "Danh sách",
                GiftCardStatus.ACTIVE,
                null,
                1L,
                0L,
                Instant.now(),
                Instant.now(),
                List.of()
        );

        ApiResponse<List<GiftCardResponse>> pageResp = ApiResponse.of(
                List.of(cardResp),
                new PageMeta(0, 20, 1, 1)
        );

        when(giftCardService.getGiftCards(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(pageResp);

        mockMvc.perform(get("/api/v1/gift-cards/gift-card-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].maskedCode").value("************abcd"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }
}
