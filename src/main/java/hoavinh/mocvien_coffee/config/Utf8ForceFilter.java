package hoavinh.mocvien_coffee.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Chạy đầu tiên, chấp hết mọi loại config khác
public class Utf8ForceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Cưỡng ép Set Encoding ngay lập tức
        response.setCharacterEncoding("UTF-8");
        
        // Cho request đi tiếp vào Controller
        filterChain.doFilter(request, response);
    }
}