package cn.haiduo;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component public class RequestCorrelationFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String id=UUID.randomUUID().toString();MDC.put("requestId",id);response.setHeader("X-Request-ID",id);if(request.getRequestURI().startsWith("/api/"))response.setHeader("Cache-Control","no-store");try{chain.doFilter(request,response);}finally{MDC.remove("requestId");}}
}
