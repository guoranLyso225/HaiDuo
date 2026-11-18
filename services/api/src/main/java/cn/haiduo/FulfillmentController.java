package cn.haiduo;
import cn.haiduo.inventory.InventoryService;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api") public class FulfillmentController {
 private final JdbcTemplate db;private final OperationsController scope;private final InventoryService inventory;
 public FulfillmentController(JdbcTemplate db,OperationsController scope,InventoryService inventory){this.db=db;this.scope=scope;this.inventory=inventory;}
 record Receipt(UUID requestKey,UUID skuId,int quantity,UUID warehouseId){}
 @PostMapping("/inventory/receipts") @Transactional Object receive(@RequestBody Receipt r,Principal p,HttpSession s){return new ReceiptApprovals(db,scope).receive(r,p,s);}
 record Ship(String carrier,String trackingNumber){}
 @PostMapping("/orders/{id}/shipment") @Transactional Object ship(@PathVariable UUID id,@RequestBody Ship r,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","WAREHOUSE");if(r.carrier()==null||r.carrier().isBlank()||r.carrier().length()>100||r.trackingNumber()==null||r.trackingNumber().isBlank()||r.trackingNumber().length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);Object proposal=BusinessWorkflows.propose(db,t,p.getName(),"ORDER_SHIP",id.toString(),r);if(proposal!=null)return proposal;try{inventory.transition(t,id,"ship");}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage());}int added=db.update("insert into shipment(id,tenant_id,order_id,carrier,tracking_number,created_at) values(?,?,?,?,?,now()) on conflict(tenant_id,order_id) do nothing",UUID.randomUUID(),t,id,r.carrier(),r.trackingNumber());if(added==1)new Audit(db).record(t,p.getName(),"SHIPMENT_CREATE",id);if(added==0){var old=db.queryForMap("select carrier,tracking_number from shipment where tenant_id=? and order_id=?",t,id);if(!old.get("carrier").equals(r.carrier())||!old.get("tracking_number").equals(r.trackingNumber()))throw new ResponseStatusException(HttpStatus.CONFLICT,"订单已使用其他运单发货");}return Map.of("status","APPLIED");}
 @GetMapping("/shipments") Object list(Principal p,HttpSession s){return db.queryForList("select id,order_id,carrier,tracking_number,created_at,delivery_status,event_version,edit_version from shipment where tenant_id=? order by created_at desc limit 200",scope.tenant(p,s));}
}
