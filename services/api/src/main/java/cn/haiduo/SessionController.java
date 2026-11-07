package cn.haiduo;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController @RequestMapping("/api/session") public class SessionController {
 private final JdbcTemplate db;
 public SessionController(JdbcTemplate db){this.db=db;}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @GetMapping Map<String,Object> current(Principal user,HttpSession session){return Map.of("username",user.getName(),"memberships",db.queryForList("select m.tenant_id,m.role,t.name from membership m join tenant t on t.id=m.tenant_id where m.username=?",user.getName()),"activeTenant",Optional.ofNullable(session.getAttribute("activeTenant")).orElse(""));}
 @PostMapping("/tenant/{id}") void select(@PathVariable UUID id,Principal user,HttpSession session){verifiedRole(user,id);session.setAttribute("activeTenant",id.toString());}
 String verifiedRole(Principal user,UUID tenant){return db.query("select role from membership where username=? and tenant_id=?",(rs,n)->rs.getString(1),user.getName(),tenant).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"无企业访问权限"));}
 public UUID activeTenant(Principal user,HttpSession session){Object value=session.getAttribute("activeTenant");if(value==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"请先选择企业");UUID id=UUID.fromString(value.toString());verifiedRole(user,id);return id;}
}
