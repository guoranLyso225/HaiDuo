package cn.haiduo;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class MonitoringSecurity {
 @Bean @Order(0) SecurityFilterChain monitoring(HttpSecurity http)throws Exception{return http.securityMatcher(request->request.getLocalPort()==9090).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/prometheus").permitAll().anyRequest().denyAll()).build();}
}
