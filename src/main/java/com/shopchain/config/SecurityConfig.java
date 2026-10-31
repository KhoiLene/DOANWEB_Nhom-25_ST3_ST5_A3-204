package com.shopchain.config;

import com.shopchain.security.JwtAuthenticationFilter;
import com.shopchain.security.CookieCsrfProtectionFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CookieCsrfProtectionFilter cookieCsrfProtectionFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CookieCsrfProtectionFilter cookieCsrfProtectionFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.cookieCsrfProtectionFilter = cookieCsrfProtectionFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/css/**", "/js/**", "/img/**", "/images/**", "/fonts/**", "/favicon.ico");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<CookieCsrfProtectionFilter> cookieCsrfFilterRegistration(
            CookieCsrfProtectionFilter filter) {
        FilterRegistrationBean<CookieCsrfProtectionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // HTML forms do not yet submit CSRF tokens; the same-origin filter guards unsafe browser requests below.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/healthz", "/api/auth/**", "/api/products/**", "/api/locations/**", "/api/payment/vnpay-return", "/css/**", "/js/**", "/images/**", "/img/**", "/", "/products", "/search", "/product/**", "/news", "/news/**", "/about", "/stores", "/login", "/login-process", "/register", "/register-process", "/forgot-password", "/forgot-password-process", "/reset-password", "/reset-password-process", "/order-success", "/api/reviews/**").permitAll()
                .requestMatchers("/api/payment/vnpay").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/messages/**").hasAnyRole("ADMIN", "WAREHOUSE", "STORE_STAFF")
                .requestMatchers("/warehouse/**", "/api/warehouse/**").hasAnyRole("WAREHOUSE", "ADMIN")
                .requestMatchers("/staff/**", "/api/staff/**").hasAnyRole("STORE_STAFF", "ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/assistant/**").permitAll()
                .requestMatchers("/api/cart/**", "/api/customer/**", "/cart", "/cart/**", "/checkout", "/checkout/**", "/profile", "/beauty-passport", "/my/invoices/**", "/api/orders/**").hasRole("USER")
                .requestMatchers("/api/vendor/**").hasAnyRole("VENDOR", "ADMIN")
                .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    String path = request.getRequestURI();
                    if (path.startsWith(request.getContextPath() + "/api/")) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }

                    String returnTo = request.getRequestURI();
                    if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                        returnTo += "?" + request.getQueryString();
                    }
                    String loginUrl = request.getContextPath() + "/login?returnTo="
                            + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
                    response.sendRedirect(loginUrl);
                })
                .accessDeniedHandler((request, response, exception) ->
                        response.sendError(HttpServletResponse.SC_FORBIDDEN))
            );

        http.addFilterBefore(cookieCsrfProtectionFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, CookieCsrfProtectionFilter.class);

        return http.build();
    }
}
