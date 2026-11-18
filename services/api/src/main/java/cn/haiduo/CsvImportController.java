package cn.haiduo;
import java.util.*;import java.security.Principal;import jakarta.servlet.http.HttpSession;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/order-imports") public class CsvImportController {
 private final JdbcTemplate db;private final OperationsController scope;public CsvImportController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping Object list(Principal p,HttpSession s){return db.queryForList("select b.id,b.actor,b.imported,b.duplicates,b.status,b.created_at,b.rolled_back_at,w.name as warehouse_name from csv_import_batch b join warehouse w on w.tenant_id=b.tenant_id and w.id=b.warehouse_id where b.tenant_id=? order by b.created_at desc limit 200",scope.tenant(p,s,"ADMIN","OPERATIONS"));}
 @GetMapping("/{id}") Object detail(@PathVariable UUID id,Principal p,HttpSession s){return new CsvBatchService(db).detail(scope.tenant(p,s,"ADMIN","OPERATIONS"),id);}
}
