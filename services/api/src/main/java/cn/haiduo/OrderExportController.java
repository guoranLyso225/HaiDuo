package cn.haiduo;
import java.util.*;
import java.time.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController public class OrderExportController {
 private final JdbcTemplate db;private final OperationsController scope;public OrderExportController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 static String cell(Object value){String s=Objects.toString(value,"");if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
 @GetMapping("/api/orders/export.csv") ResponseEntity<String> export(Principal p,HttpSession session,@RequestParam(defaultValue="") String store,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){UUID tenant=scope.requirePermission(p,session,Role.Permission.ORDER_EXPORT);if(store.length()>100||!Set.of("","NEW","RESERVED","CANCELLED","SHIPPED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);Instant end=to==null?Instant.now():to,start=from==null?end.minus(Duration.ofDays(30)):from;if(!start.isBefore(end)||Duration.between(start,end).toDays()>366)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"导出范围须为1年以内的UTC区间");var rows=db.queryForList("select store,external_id,status,payment_status,refund_status,currency,net::text,rate::text,created_at from sales_order where tenant_id=? and (?='' or store=?) and (?='' or status=?) and created_at>=? and created_at<? order by created_at,id limit 5001",tenant,store,store,status,status,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end));if(rows.size()>5000)throw new ResponseStatusException(HttpStatus.CONFLICT,"最多导出5000订单，请缩小时间范围");StringBuilder csv=new StringBuilder("\uFEFFstore,order_no,fulfillment,payment,refund,currency,net,rate,created_at_utc\r\n");for(var row:rows){var cells=new ArrayList<String>();for(String column:List.of("store","external_id","status","payment_status","refund_status","currency","net","rate","created_at")){Object value=row.get(column);if(value instanceof java.sql.Timestamp time)value=time.toInstant();cells.add(cell(value));}csv.append(String.join(",",cells)).append("\r\n");}return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=haiduo-orders.csv").body(csv.toString());}
}
