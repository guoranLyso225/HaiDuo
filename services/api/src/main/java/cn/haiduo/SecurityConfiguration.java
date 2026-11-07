package cn.haiduo;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration public class SecurityConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(JdbcTemplate db){return username->db.query("select username,password_hash,enabled from app_user where username=?",(rs,n)->User.withUsername(rs.getString(1)).password(rs.getString(2)).disabled(!rs.getBoolean(3)).authorities("AUTHENTICATED").build(),username).stream().findFirst().orElseThrow(()->new UsernameNotFoundException("账号或密码错误"));}
 @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{
  return http.csrf(c->c.ignoringRequestMatchers("/api/webhooks/shopify")).authorizeHttpRequests(a->a.requestMatchers("/api/session/csrf","/api/webhooks/shopify","/healthz").permitAll().anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401)))
   .formLogin(f->f.loginProcessingUrl("/api/session/login").successHandler((req,res,auth)->res.setStatus(204)).failureHandler((req,res,ex)->res.sendError(401)))
   .logout(l->l.logoutUrl("/api/session/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204))).build();
 }
}
