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
@RestController public class ListingController {
 private final JdbcTemplate db;private final OperationsController scope;private final Audit audit;
 public ListingController(JdbcTemplate db,OperationsController scope,Audit audit){this.db=db;this.scope=scope;this.audit=audit;}
 @GetMapping("/api/stores/{id}/listings") Object list(@PathVariable UUID id,Principal p,HttpSession s){UUID t=scope.tenant(p,s);requireStore(t,id);return db.queryForList("select l.platform_product_id,l.sku_id,s.code from store_listing l join sku s on s.tenant_id=l.tenant_id and s.id=l.sku_id where l.tenant_id=? and l.store_id=? order by l.platform_product_id limit 200",t,id);}
 record Mapping(UUID skuId,String platformProductId){}
 @PostMapping("/api/stores/{id}/listings") @Transactional void save(@PathVariable UUID id,@RequestBody Mapping r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","OPERATIONS");requireStore(t,id);if(r.skuId()==null||r.platformProductId()==null||r.platformProductId().isBlank()||r.platformProductId().length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);if(db.queryForObject("select count(*) from sku where tenant_id=? and id=?",Integer.class,t,r.skuId())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);int added=db.update("insert into store_listing values(?,?,?,?) on conflict do nothing",t,id,r.skuId(),r.platformProductId());if(added==0){UUID existing=db.queryForObject("select sku_id from store_listing where tenant_id=? and store_id=? and platform_product_id=?",UUID.class,t,id,r.platformProductId());if(!r.skuId().equals(existing))throw new ResponseStatusException(HttpStatus.CONFLICT,"平台商品已映射其他SKU");}else audit.record(t,p.getName(),"LISTING_CREATE",id);}
 void requireStore(UUID t,UUID id){if(db.queryForObject("select count(*) from store where tenant_id=? and id=?",Integer.class,t,id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 @GetMapping("/api/audit") Object logs(Principal p,HttpSession s){return db.queryForList("select id,actor,action,target_id,created_at from audit_log where tenant_id=? order by created_at desc limit 200",scope.tenant(p,s,"ADMIN"));}
}
