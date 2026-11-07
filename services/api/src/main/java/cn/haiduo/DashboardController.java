package cn.haiduo;
import cn.haiduo.finance.Profit;
import java.security.Principal;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController public class DashboardController {
 private final JdbcTemplate db;private final OperationsController scope;
 public DashboardController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping("/api/dashboard") Object summary(Principal p,HttpSession s,@RequestParam(defaultValue="30") int days,@RequestParam(defaultValue="") String store){
  if(days<1||days>366||store.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  UUID t=scope.tenant(p,s,"ADMIN","FINANCE");Instant end=Instant.now(),start=end.minus(Duration.ofDays(days));
  var totals=db.queryForMap("select count(*) as orders,coalesce(round(sum(net*rate),2),0)::text as sales_cny from sales_order where tenant_id=? and status<>'CANCELLED' and created_at>=? and created_at<? and (?='' or store=?)",t,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),store,store);
  var profit=db.queryForMap("select coalesce(round(sum(o.net*o.rate-c.cost-coalesce(f.fee,0)),2),0)::text as contribution_cny,count(*) filter(where c.missing>0) as missing_cost_orders from sales_order o join (select tenant_id,order_id,sum(quantity*unit_cost_cny) as cost,count(*) filter(where unit_cost_cny is null) as missing from order_item group by tenant_id,order_id)c on c.tenant_id=o.tenant_id and c.order_id=o.id left join(select tenant_id,order_id,sum(amount_cny) as fee from order_fee group by tenant_id,order_id)f on f.tenant_id=o.tenant_id and f.order_id=o.id where o.tenant_id=? and o.status<>'CANCELLED' and o.created_at>=? and o.created_at<? and (?='' or o.store=?)",t,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),store,store);
  // Exclude every order with a missing historical cost from the contribution total.
  var known=db.queryForObject("select coalesce(round(sum(net*rate-cost-fee),2),0)::text from(select o.net,o.rate,sum(i.quantity*i.unit_cost_cny) cost,coalesce((select sum(amount_cny) from order_fee where tenant_id=o.tenant_id and order_id=o.id),0) fee from sales_order o join order_item i on i.tenant_id=o.tenant_id and i.order_id=o.id where o.tenant_id=? and o.status<>'CANCELLED' and o.created_at>=? and o.created_at<? and (?='' or o.store=?) group by o.id having count(*) filter(where i.unit_cost_cny is null)=0)x",String.class,t,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),store,store);
  profit.put("contribution_cny",known);
  var exceptions=db.queryForList("select o.id,o.external_id,o.status from sales_order o where o.tenant_id=? and o.created_at>=? and o.created_at<? and (?='' or o.store=?) and o.status='NEW' and exists(select 1 from order_item i left join inventory v on v.tenant_id=i.tenant_id and v.sku_id=i.sku_id and v.warehouse_id=o.warehouse_id where i.tenant_id=o.tenant_id and i.order_id=o.id and coalesce(v.on_hand-v.reserved,0)<i.quantity) order by o.created_at desc limit 20",t,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),store,store);var trend=db.queryForList("select to_char(created_at at time zone 'UTC','YYYY-MM-DD') as day,round(sum(net*rate),2)::text as sales_cny from sales_order where tenant_id=? and status<>'CANCELLED' and created_at>=? and created_at<? and (?='' or store=?) group by day order by day",t,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),store,store);return Map.of("trend",trend,"exceptions",exceptions,"totals",totals,"profit",profit,"lowStock",db.queryForObject("select count(*) from inventory where tenant_id=? and on_hand-reserved<5",Long.class,t),"currency","CNY","timezone","UTC","start",start.toString(),"end",end.toString(),"basis","非取消订单，创建时间范围；利润仅包含成本快照完整订单，未录费用按0预估；低库存为企业当前可用量<5，不受时间/店铺筛选");
 }
}
