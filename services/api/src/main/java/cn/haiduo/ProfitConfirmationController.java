package cn.haiduo;
import cn.haiduo.finance.ProfitConfirmations;
import cn.haiduo.finance.Profit;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController public class ProfitConfirmationController {
 private final ProfitConfirmations confirmations;private final OperationsController scope;private final JdbcTemplate db;
 public ProfitConfirmationController(ProfitConfirmations confirmations,OperationsController scope,JdbcTemplate db){this.confirmations=confirmations;this.scope=scope;this.db=db;}
 record Confirm(UUID requestKey,String source){}
 @PostMapping("/api/orders/{id}/profit-confirmations") Object confirm(@PathVariable UUID id,@RequestBody Confirm r,Principal p,HttpSession s){return confirmations.confirm(scope.requirePermission(p,s,Role.Permission.FINANCE_WRITE),id,r.requestKey(),r.source(),p.getName());}
 @GetMapping("/api/profit-confirmations") Object list(Principal p,HttpSession s){return db.queryForList("select c.id,o.external_id,c.version,c.net_original::text,c.currency,c.exchange_rate::text,c.purchase_cny::text,c.freight_cny::text,c.platform_cny::text,c.contribution_cny::text,c.source,c.actor,c.confirmed_at from profit_confirmation c join sales_order o on o.tenant_id=c.tenant_id and o.id=c.order_id where c.tenant_id=? order by c.confirmed_at desc limit 200",scope.requirePermission(p,s,Role.Permission.FINANCE_WRITE));}
}
