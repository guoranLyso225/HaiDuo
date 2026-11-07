package cn.haiduo;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController public class ProfitController {
 private final JdbcTemplate db;private final OperationsController scope;
 public ProfitController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping("/api/profits") Object profits(Principal p,HttpSession s){return db.queryForList("select o.id,o.external_id,o.store,o.currency,o.net::text as net,o.rate::text as rate,'CNY' as reporting_currency,'ESTIMATED' as basis,case when count(*) filter(where i.unit_cost_cny is null)>0 then null else round(o.net*o.rate-sum(i.quantity*i.unit_cost_cny)-coalesce((select sum(f.amount_cny) from order_fee f where f.tenant_id=o.tenant_id and f.order_id=o.id),0),2)::text end as contribution from sales_order o join order_item i on i.order_id=o.id and i.tenant_id=o.tenant_id where o.tenant_id=? and o.status<>'CANCELLED' group by o.id order by o.created_at desc limit 200",scope.tenant(p,s,"ADMIN","FINANCE"));}
}
