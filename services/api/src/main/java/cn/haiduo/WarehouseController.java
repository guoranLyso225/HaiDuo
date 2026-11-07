package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/warehouses") public class WarehouseController {
 private final JdbcTemplate db;private final OperationsController scope;
 public WarehouseController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping Object list(Principal p,HttpSession s){return db.queryForList("select id,name from warehouse where tenant_id=? order by name",scope.tenant(p,s));}
 record Create(String name){}
 @PostMapping @Transactional Object create(@RequestBody Create r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN"),id=UUID.randomUUID();if(r.name()==null||r.name().isBlank()||r.name().length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);int added=db.update("insert into warehouse values(?,?,?) on conflict(tenant_id,name) do nothing",t,id,r.name());if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT);db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) select tenant_id,id,0,0,? from sku where tenant_id=?",id,t);new Audit(db).record(t,p.getName(),"WAREHOUSE_CREATE",id);return Map.of("id",id);}
}
