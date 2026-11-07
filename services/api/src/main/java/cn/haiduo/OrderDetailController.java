package cn.haiduo;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController public class OrderDetailController {
 private final JdbcTemplate db;private final OperationsController scope;
 public OrderDetailController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping("/api/orders/{id}") Object detail(@PathVariable UUID id,Principal p,HttpSession s){UUID tenant=scope.tenant(p,s);var orders=db.queryForList("select id,external_id,store,status,sync_review_required,platform_fulfillment_status,externally_fulfilled,payment_status,refund_status,net::text as net,currency,rate::text as rate,rate_source,rate_at,exchange_rate_snapshot_id,created_at from sales_order where tenant_id=? and id=?",tenant,id);if(orders.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return Map.of("order",orders.getFirst(),"items",db.queryForList("select s.code,s.name,i.quantity,i.unit_cost_cny::text as unit_cost_cny from order_item i join sku s on s.id=i.sku_id and s.tenant_id=i.tenant_id where i.tenant_id=? and i.order_id=?",tenant,id),"fees",db.queryForList("select kind,amount_cny::text as amount_cny,original_amount::text as original_amount,currency,exchange_rate::text as exchange_rate,rate_source,effective_at,exchange_rate_snapshot_id from order_fee where tenant_id=? and order_id=?",tenant,id),"timeline",db.queryForList("select kind,min(created_at) as time from inventory_movement where tenant_id=? and order_id=? group by kind union all select concat('MOCK_',e.status) as kind,e.occurred_at as time from shipment_event e join shipment s on s.tenant_id=e.tenant_id and s.id=e.shipment_id where e.tenant_id=? and s.order_id=? and e.applied order by time",tenant,id,tenant,id),"shipments",db.queryForList("select carrier,tracking_number,created_at from shipment where tenant_id=? and order_id=?",tenant,id));
 }
}
