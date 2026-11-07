package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/members") public class MemberController {
 private final JdbcTemplate db;private final OperationsController scope;private final Audit audit;
 public MemberController(JdbcTemplate db,OperationsController scope,Audit audit){this.db=db;this.scope=scope;this.audit=audit;}
 @GetMapping Object list(Principal p,HttpSession s){return db.queryForList("select username,role from membership where tenant_id=? order by username",scope.tenant(p,s,"ADMIN"));}
 record Change(String username,String role){}
 record Create(String username,String password,String role){}
 @PostMapping @Transactional void create(@RequestBody Create r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN");if(r.username()==null||!r.username().matches("[a-zA-Z0-9._-]{3,64}")||r.password()==null||r.password().length()<12||r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||r.role()==null||!Set.of("ADMIN","OPERATIONS","WAREHOUSE","FINANCE").contains(r.role()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"账号3至64字符，密码至少12字符且不超过72个UTF-8字节");
  int n=db.update("insert into app_user(username,password_hash) values(?,?) on conflict do nothing",r.username(),new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(r.password()));if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"账号已存在，不能直接绑定其他账号");db.update("insert into membership values(?,?,?)",r.username(),t,r.role());audit.record(t,p.getName(),"MEMBER_CREATE",r.username());
 }
 @PostMapping("/role") @Transactional void change(@RequestBody Change r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN");if(r.username()==null||r.role()==null||!Set.of("ADMIN","OPERATIONS","WAREHOUSE","FINANCE").contains(r.role()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  // Serialize member changes per enterprise, including the last-admin check.
  db.queryForObject("select id from tenant where id=? for update",UUID.class,t);
  if(!"ADMIN".equals(db.queryForObject("select role from membership where tenant_id=? and username=?",String.class,t,p.getName())))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var existing=db.query("select role from membership where tenant_id=? and username=?",(rs,n)->rs.getString(1),t,r.username());if(existing.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(existing.getFirst().equals(r.role()))return;
  if(existing.getFirst().equals("ADMIN")&&!r.role().equals("ADMIN")&&db.queryForObject("select count(*) from membership where tenant_id=? and role='ADMIN'",Integer.class,t)<=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"必须保留至少一名管理员");
  db.update("update membership set role=? where tenant_id=? and username=?",r.role(),t,r.username());audit.record(t,p.getName(),"MEMBER_ROLE_"+r.role(),r.username());
 }
}
