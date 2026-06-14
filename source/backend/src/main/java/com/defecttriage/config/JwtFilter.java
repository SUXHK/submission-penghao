package com.defecttriage.config;

import com.defecttriage.common.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"未登录\"}");
            return;
        }

        try {
            String token = header.substring(7);
            Claims claims = jwtUtil.validateToken(token);
            request.setAttribute("userId", jwtUtil.getUserId(claims));
            request.setAttribute("userRole", jwtUtil.getRole(claims));
            chain.doFilter(request, response);
        } catch (JwtException e) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token无效或已过期\"}");
        }
    }
}
