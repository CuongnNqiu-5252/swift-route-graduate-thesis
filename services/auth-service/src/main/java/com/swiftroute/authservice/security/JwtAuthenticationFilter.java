package com.swiftroute.authservice.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.swiftroute.authservice.repository.UserRepository;
import com.swiftroute.authservice.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository; // Dùng để tìm User từ DB nếu cần

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. Kiểm tra xem Request có mang theo Token không
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // 2. Validate Token hợp lệ hay không (không hợp lệ sẽ ném lỗi)
            if (jwtService.validateToken(jwt)) {

                // 3. Lấy UserId và Role ra khỏi Token
                String userId = jwtService.extractSubject(jwt);
                String role = jwtService.extractRole(jwt);

                // 4. Nếu chưa có ai đăng nhập trong SecurityContext
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    var authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role));

                    // 5. Nhét User vào Context để Spring Security công nhận là "Đã Đăng Nhập"
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Nếu token sai, hết hạn, v.v.. thì bỏ qua không set Authentication
            logger.error("JWT Filter Error: " + e.getMessage());
        }

        // Luôn luôn phải cho filter chạy tiếp
        filterChain.doFilter(request, response);
    }
}
