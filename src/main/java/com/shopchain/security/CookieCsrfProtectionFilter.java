package com.shopchain.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Validate browser origins for every unsafe request, including login and other
 * anonymous forms. Bearer-token API clients do not rely on ambient browser cookies.
 */
@Component
public class CookieCsrfProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final String configuredPublicOrigin;

    public CookieCsrfProtectionFilter(
            @Value("${app.security.public-origin:}") String configuredPublicOrigin) {
        this.configuredPublicOrigin = configuredPublicOrigin == null ? "" : configuredPublicOrigin.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())
                || hasBearerAuthorization(request)
                || hasSameOriginHeader(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Yêu cầu không hợp lệ.");
    }

    private boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private boolean hasSameOriginHeader(HttpServletRequest request) {
        String originHeader = request.getHeader("Origin");
        URI suppliedOrigin;
        if (originHeader != null && !originHeader.isBlank()) {
            suppliedOrigin = parseOrigin(originHeader.trim());
        } else {
            String referer = request.getHeader("Referer");
            if (referer == null || referer.isBlank()) return false;
            try {
                suppliedOrigin = parseOrigin(new URI(referer).getScheme() + "://" + new URI(referer).getRawAuthority());
            } catch (URISyntaxException | NullPointerException exception) {
                return false;
            }
        }
        if (suppliedOrigin == null) return false;

        URI expectedOrigin = configuredPublicOrigin.isBlank()
                ? requestOrigin(request) : parseOrigin(configuredPublicOrigin);
        return expectedOrigin != null && sameOrigin(expectedOrigin, suppliedOrigin);
    }

    private URI requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (scheme == null || host == null || host.isBlank()) return null;
        try {
            String authority = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            return parseOrigin(scheme + "://" + authority + ":" + port);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private URI parseOrigin(String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            String scheme = uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            return uri;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameOrigin(URI expected, URI supplied) {
        return expected.getScheme().equalsIgnoreCase(supplied.getScheme())
                && expected.getHost().equalsIgnoreCase(supplied.getHost())
                && effectivePort(expected) == effectivePort(supplied);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
