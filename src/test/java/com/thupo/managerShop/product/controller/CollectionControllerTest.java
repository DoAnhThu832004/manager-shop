package com.thupo.managerShop.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.product.dto.AddProductToCollectionRequest;
import com.thupo.managerShop.product.dto.CollectionCreateRequest;
import com.thupo.managerShop.product.dto.CollectionListResponse;
import com.thupo.managerShop.product.dto.CollectionResponse;
import com.thupo.managerShop.product.dto.CollectionRuleRequest;
import com.thupo.managerShop.product.entity.CollectionType;
import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;
import com.thupo.managerShop.product.service.CollectionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CollectionController.class)
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CollectionService collectionService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC bắt buộc: STAFF gọi POST /api/v1/collections trả 403 kèm requiredPermissions MANAGE_PRODUCTS")
    @WithMockUser(roles = "STAFF")
    void staffCallingPostCollectionsReturns403WithRequiredPermissions() throws Exception {
        CollectionCreateRequest request = new CollectionCreateRequest(
                "Bộ sưu tập mới",
                null,
                null,
                CollectionType.MANUAL,
                false,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/collections")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_PRODUCTS"));
    }

    @Test
    @DisplayName("RBAC: ADMIN có quyền MANAGE_PRODUCTS gọi POST /api/v1/collections thành công 201 Created")
    @WithMockUser(roles = "ADMIN")
    void adminCallingPostCollectionsReturns201Created() throws Exception {
        CollectionCreateRequest request = new CollectionCreateRequest(
                "Bộ sưu tập mùa hè",
                "<p>Các sản phẩm thời trang mùa hè</p>",
                "https://example.com/summer.png",
                CollectionType.MANUAL,
                false,
                null,
                List.of(101L)
        );

        CollectionResponse mockResponse = new CollectionResponse(
                50L,
                "gid://shopify/Collection/50",
                "Bộ sưu tập mùa hè",
                "<p>Các sản phẩm thời trang mùa hè</p>",
                "https://example.com/summer.png",
                CollectionType.MANUAL,
                false,
                1,
                List.of(),
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(collectionService.createCollection(any(CollectionCreateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/collections")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.title").value("Bộ sưu tập mùa hè"))
                .andExpect(jsonPath("$.data.gid").value("gid://shopify/Collection/50"));
    }

    @Test
    @DisplayName("RBAC: STAFF được phép xem danh sách bộ sưu tập qua GET /api/v1/collections (HTTP 200)")
    @WithMockUser(roles = "STAFF")
    void staffCanAccessGetCollectionsList() throws Exception {
        CollectionListResponse item = new CollectionListResponse(
                50L,
                "gid://shopify/Collection/50",
                "Bộ sưu tập mùa hè",
                "https://example.com/summer.png",
                CollectionType.MANUAL,
                1,
                Instant.now(),
                Instant.now()
        );

        ApiResponse<List<CollectionListResponse>> mockPage = ApiResponse.of(
                List.of(item),
                new PageMeta(0, 20, 1, 1)
        );

        when(collectionService.getCollections(any(), any(), eq(0), eq(20)))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(50))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("Nghiệp vụ bắt buộc: Tạo MANUAL có rule trả HTTP 400 Bad Request ProblemDetail")
    @WithMockUser(roles = "ADMIN")
    void createManualWithRulesReturns400BadRequest() throws Exception {
        CollectionCreateRequest request = new CollectionCreateRequest(
                "Bộ sưu tập thủ công lỗi",
                null, null,
                CollectionType.MANUAL,
                false,
                List.of(new CollectionRuleRequest(RuleField.VENDOR, RuleOperator.EQUALS, "Nike", false)),
                null
        );

        when(collectionService.createCollection(any(CollectionCreateRequest.class)))
                .thenThrow(new BadRequestAlertException("Bộ sưu tập thủ công (MANUAL) không được có quy tắc điều kiện (rules)"));

        mockMvc.perform(post("/api/v1/collections")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Bộ sưu tập thủ công (MANUAL) không được có quy tắc điều kiện (rules)"));
    }

    @Test
    @DisplayName("Nghiệp vụ bắt buộc: Thêm trùng sản phẩm vào MANUAL trả HTTP 409 Conflict ProblemDetail")
    @WithMockUser(roles = "ADMIN")
    void addDuplicateProductToManualReturns409Conflict() throws Exception {
        AddProductToCollectionRequest request = new AddProductToCollectionRequest(101L, 1);

        doThrow(new ConflictException("Sản phẩm đã tồn tại trong bộ sưu tập"))
                .when(collectionService).addProductToCollection(50L, 101L, 1);

        mockMvc.perform(post("/api/v1/collections/50/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Sản phẩm đã tồn tại trong bộ sưu tập"));
    }

    @Test
    @DisplayName("ADMIN xóa mềm bộ sưu tập trả HTTP 204 No Content")
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteCollection() throws Exception {
        mockMvc.perform(delete("/api/v1/collections/50").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
