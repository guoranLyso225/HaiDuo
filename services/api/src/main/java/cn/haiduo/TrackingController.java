package cn.haiduo;
import cn.haiduo.fulfillment.TrackingEvents;
import java.util.*;
import java.time.Instant;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/shipments/{id}/events") public class TrackingController {
 private final JdbcTemplate db;private final OperationsController scope;private final TrackingEvents events;
 public TrackingController(JdbcTemplate db,OperationsController scope,TrackingEvents events){this.db=db;this.scope=scope;this.events=events;}
 record Event(UUID eventKey,long version,String status,Instant occurredAt){}
 @PostMapping Object simulate(@PathVariable UUID id,@RequestBody Event r,Principal p,HttpSession s){return events.accept(scope.requirePermission(p,s,Role.Permission.SHIPMENT_WRITE),id,r.eventKey(),r.version(),r.status(),r.occurredAt(),p.getName());}
 @GetMapping Object timeline(@PathVariable UUID id,Principal p,HttpSession s){UUID t=scope.tenant(p,s);if(db.queryForObject("select count(*) from shipment where tenant_id=? and id=?",Integer.class,t,id)!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);return db.queryForList("select event_key,version,status,applied,source,occurred_at,received_at from shipment_event where tenant_id=? and shipment_id=? order by version desc limit 200",t,id);}
}
