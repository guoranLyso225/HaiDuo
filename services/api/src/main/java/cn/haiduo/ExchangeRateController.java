package cn.haiduo;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/exchange-rate-snapshots") public class ExchangeRateController {
 private final JdbcTemplate db;private final OperationsController scope;public ExchangeRateController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping Object list(Principal p,HttpSession s){return db.queryForList("select id,base_currency,quote_currency,rate::text as rate,source,effective_at from exchange_rate_snapshot where tenant_id=? order by created_at desc limit 200",scope.requirePermission(p,s,Role.Permission.FINANCE_READ));}
}
