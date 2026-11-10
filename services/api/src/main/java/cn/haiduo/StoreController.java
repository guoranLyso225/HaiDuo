package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/stores") public class StoreController {
 private final JdbcTemplate db;private final OperationsController scope;private final Audit audit;
 public StoreController(JdbcTemplate db,OperationsController scope,Audit audit){this.db=db;this.scope=scope;this.audit=audit;}
 @GetMapping Object list(Principal p,HttpSession s){return db.queryForList("select id,name,platform,connection_status from store where tenant_id=? order by name limit 200",scope.tenant(p,s));}
 record Create(String name,String platform){}
 @org.springframework.transaction.annotation.Transactional @PostMapping @ResponseStatus(HttpStatus.CREATED) void create(@RequestBody Create r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","OPERATIONS");String platform=r.platform()==null?"MOCK":r.platform();if(!Set.of("MOCK","SHOPIFY","WOOCOMMERCE").contains(platform)||r.name()==null||r.name().isBlank()||r.name().length()>100||r.name().contains(",")||r.name().contains("\n")||r.name().contains("\r"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);UUID id=UUID.randomUUID();int n=db.update("insert into store values(?,?,?,?,?) on conflict(tenant_id,name) do nothing",id,t,r.name().trim(),platform,platform.equals("MOCK")?"MOCK":"DISABLED");if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"店铺名称已存在");audit.record(t,p.getName(),"STORE_CREATE",id);}
}
