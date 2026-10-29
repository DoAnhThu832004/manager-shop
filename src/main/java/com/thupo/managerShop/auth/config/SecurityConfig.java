package com.thupo.managerShop.auth.config;

import com.thupo.managerShop.auth.exception.ErrorResponseWriter;
import com.thupo.managerShop.auth.security.AuthUserCache;
import com.thupo.managerShop.auth.security.ClientIpResolver;
import com.thupo.managerShop.auth.security.IpAccessPolicy;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.auth.security.JwtAuthenticationFilter;
import com.thupo.managerShop.auth.security.JwtService;
import com.thupo.managerShop.auth.security.RestAccessDeniedHandler;
import com.thupo.managerShop.auth.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cấu hình Spring Security: stateless, JWT, phân quyền RBAC.
 *
 * <p>CSRF được tắt vì API không dùng cookie/session mà xác thực bằng header {@code Authorization}; trình duyệt
 * không tự gửi header này nên không có kịch bản CSRF cổ điển. Nếu sau này chuyển token sang cookie thì phải bật lại.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    private static final String AUTH_BASE = "/api/v1/auth";
    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;
    /** Chạy trước Spring Security filter chain để IP bị chặn không tốn CPU xác thực. */
    private static final int IP_BLOCK_FILTER_ORDER =
            org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER - 10;

    @Bean
    public PasswordEncoder passwordEncoder(AppSecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }


    /**
     * Đăng ký {@link IpBlockFilter} như servlet filter có thứ tự ưu tiên cao hơn Spring Security.
     * Filter không khai báo {@code @Component} để tránh bị đăng ký hai lần.
     */
    @Bean
    public FilterRegistrationBean<IpBlockFilter> ipBlockFilterRegistration(
            ClientIpResolver clientIpResolver, IpAccessPolicy ipAccessPolicy,
            ErrorResponseWriter errorResponseWriter) {
        FilterRegistrationBean<IpBlockFilter> registration = new FilterRegistrationBean<>(
                new IpBlockFilter(clientIpResolver, ipAccessPolicy, errorResponseWriter));
        registration.setOrder(IP_BLOCK_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   AuthUserCache authUserCache,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                        .contentTypeOptions(options -> { })
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                AUTH_BASE + "/login",
                                AUTH_BASE + "/register",
                                AUTH_BASE + "/refresh",
                                AUTH_BASE + "/forgot-password",
                                AUTH_BASE + "/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                AUTH_BASE + "/check-status",
                                "/api/v1/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(AUTH_BASE + "/admin/**").hasAnyRole("OWNER", "MANAGER")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, authUserCache),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.getCors().getMaxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
