package com.thupo.managerShop.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.exception.GlobalExceptionHandler;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.dto.ProductCreateRequest;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.dto.ProductResponse;
import com.thupo.managerShop.product.dto.ProductVariantRequest;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.service.ProductService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@EnableMethodSecurity
@Import({GlobalExceptionHandler.class, RestAccessDeniedHandler.class, AppSecurityProperties.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private com.thupo.managerShop.auth.exception.ErrorResponseWriter errorResponseWriter;

    @Test
    @DisplayName("Nghiệp vụ RBAC bắt buộc: STAFF gọi POST /api/v1/products trả 403 kèm requiredPermissions MANAGE_PRODUCTS")
    @WithMockUser(roles = "STAFF")
    void staffCallingPostProductsReturns403WithRequiredPermissions() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Áo phông nam",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null, null, null
        );

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.requiredPermissions[0]").value("MANAGE_PRODUCTS"));
    }

    @Test
    @DisplayName("RBAC: ADMIN có quyền MANAGE_PRODUCTS gọi POST /api/v1/products thành công 201 Created")
    @WithMockUser(roles = "ADMIN")
    void adminCallingPostProductsReturns201Created() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Áo thun cotton",
                "<p>Mô tả áo thun</p>",
                ProductStatus.DRAFT,
                "CAT-1",
                "Thời trang",
                "Áo thun",
                "Shopify Store",
                List.of("ao", "thun"),
                "Default product",
                null,
                null,
                null
        );

        ProductResponse mockResponse = new ProductResponse(
                100L,
                "gid://shopify/Product/100",
                "Áo thun cotton",
                "<p>Mô tả áo thun</p>",
                ProductStatus.DRAFT,
                "CAT-1",
                "Thời trang",
                "Áo thun",
                "Shopify Store",
                List.of("ao", "thun"),
                "Default product",
                0,
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(productService.createProduct(any(ProductCreateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("Áo thun cotton"))
                .andExpect(jsonPath("$.data.gid").value("gid://shopify/Product/100"));
    }

    @Test
    @DisplayName("RBAC: STAFF được phép gọi GET /api/v1/products để xem danh sách sản phẩm (HTTP 200)")
    @WithMockUser(roles = "STAFF")
    void staffCanAccessGetProductsList() throws Exception {
        ProductListResponse item = new ProductListResponse(
                100L,
                "gid://shopify/Product/100",
                "Áo thun cotton",
                ProductStatus.ACTIVE,
                "Shopify Store",
                "Áo thun",
                0,
                1,
                null,
                Instant.now(),
                Instant.now()
        );

        ApiResponse<List<ProductListResponse>> mockPage = ApiResponse.of(
                List.of(item),
                new PageMeta(0, 20, 1, 1)
        );

        when(productService.getProducts(any(), any(), any(), any(), any(), eq(0), eq(20), any()))
                .thenReturn(mockPage);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("Test bắt buộc: Trùng SKU trả lỗi HTTP 409 Conflict ProblemDetail")
    @WithMockUser(roles = "ADMIN")
    void duplicateSkuReturns409Conflict() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Sản phẩm trùng SKU",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null,
                List.of(new ProductVariantRequest(null, null, null, null, null,
                        BigDecimal.valueOf(100000), null, null, null, null,
                        "SKU-DUPLICATE", null, null, null, null, null, null, null, null, null, null, 1)),
                null
        );

        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenThrow(new ConflictException("Mã SKU 'SKU-DUPLICATE' đã tồn tại trong hệ thống"));

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Mã SKU 'SKU-DUPLICATE' đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("Test bắt buộc: compare_at_price nhỏ hơn hoặc bằng price trả HTTP 400 Bad Request ProblemDetail")
    @WithMockUser(roles = "ADMIN")
    void compareAtPriceLessThanOrEqualToPriceReturns400BadRequest() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Sản phẩm giảm giá lỗi",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null,
                List.of(new ProductVariantRequest(null, null, null, null, null,
                        BigDecimal.valueOf(100000), BigDecimal.valueOf(80000),
                        null, null, null, "SKU-TEST", null, null, null, null, null, null, null, null, null, null, 1)),
                null
        );

        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenThrow(new BadRequestAlertException("Giá so sánh (compare_at_price) phải lớn hơn giá bán (price)"));

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Giá so sánh (compare_at_price) phải lớn hơn giá bán (price)"));
    }

    @Test
    @DisplayName("Test bắt buộc: Validation lỗi trường bắt buộc title trả 400 kèm mảng errors")
    @WithMockUser(roles = "ADMIN")
    void missingTitleReturns400WithErrorsList() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "", // Blank title
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null, null, null
        );

        mockMvc.perform(post("/api/v1/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("Chi tiết sản phẩm không tồn tại hoặc đã xóa mềm trả HTTP 404 Not Found")
    @WithMockUser(roles = "STAFF")
    void getNonExistentProductReturns404NotFound() throws Exception {
        when(productService.getProduct(999L))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm với id: 999"));

        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    @DisplayName("ADMIN xóa mềm sản phẩm thành công trả HTTP 204 No Content")
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/v1/products/100").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
