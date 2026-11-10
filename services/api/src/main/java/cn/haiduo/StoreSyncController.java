package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.math.BigDecimal;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/stores") public class StoreSyncController {
 private final JdbcTemplate db;private final OperationsController scope;
 public StoreSyncController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 record Settings(UUID warehouseId,String currency,String rate,String source){}
 @PostMapping("/{id}/sync-settings") @Transactional void settings(@PathVariable UUID id,@RequestBody Settings r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","FINANCE");if(r.warehouseId()==null||r.currency()==null||!r.currency().matches("[A-Z]{3}")||r.rate()==null||!r.rate().matches("[0-9]{1,8}(\\.[0-9]{1,8})?")||new BigDecimal(r.rate()).signum()<=0||(r.currency().equals("CNY")&&new BigDecimal(r.rate()).compareTo(BigDecimal.ONE)!=0)||r.source()==null||r.source().isBlank()||r.source().length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);if(db.queryForObject("select count(*) from store where tenant_id=? and id=?",Integer.class,t,id)!=1||db.queryForObject("select count(*) from warehouse where tenant_id=? and id=?",Integer.class,t,r.warehouseId())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);db.update("insert into store_sync_settings values(?,?,?) on conflict(tenant_id,store_id) do update set warehouse_id=excluded.warehouse_id",t,id,r.warehouseId());db.update("insert into store_exchange_rate(tenant_id,store_id,currency,rate,source) values(?,?,?,?,?) on conflict(tenant_id,store_id,currency) do update set rate=excluded.rate,source=excluded.source,effective_at=now()",t,id,r.currency(),new BigDecimal(r.rate()),r.source());new Audit(db).record(t,p.getName(),"STORE_SYNC_SETTINGS",id);}
 @PostMapping("/{id}/sync") @Transactional Object sync(@PathVariable UUID id,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","OPERATIONS");var stores=db.queryForList("select platform,connection_status from store where tenant_id=? and id=?",t,id);if(stores.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(!stores.getFirst().get("platform").equals("MOCK")&&(!stores.getFirst().get("connection_status").equals("CONNECTED")||db.queryForObject("select count(*) from store_sync_settings where tenant_id=? and store_id=?",Integer.class,t,id)!=1))throw new ResponseStatusException(HttpStatus.CONFLICT,"请先授权并配置履约仓库与汇率");new Audit(db).record(t,p.getName(),"STORE_SYNC",id);return Map.of("status","QUEUED","message",stores.getFirst().get("platform").equals("MOCK")?"已排队固定演示订单同步，不访问真实平台":"已排队平台订单读取任务");}
}
