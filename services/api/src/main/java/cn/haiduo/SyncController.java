package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/api/sync") public class SyncController {
 private final JdbcTemplate db;private final OperationsController scope;
 public SyncController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 @GetMapping Object list(Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","OPERATIONS");return Map.of("outbox",db.queryForList("select e.id,e.kind,e.target_id,e.status,e.attempts,e.available_at,e.created_at,exists(select 1 from sync_job j where j.tenant_id=e.tenant_id and j.event_id=e.id and j.status='FAILED') as failed from outbox_event e where e.tenant_id=? order by created_at desc limit 100",t),"jobs",db.queryForList("select id,event_id,type,status,created_at from sync_job where tenant_id=? order by created_at desc limit 100",t));}
 @PostMapping("/{id}/replay") @Transactional void replay(@PathVariable UUID id,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN");var events=db.queryForList("select status from outbox_event where tenant_id=? and id=? for update",t,id);if(events.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(events.getFirst().get("status").equals("PENDING")||db.queryForObject("select count(*) from processed_event where tenant_id=? and event_id=?",Integer.class,t,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"事件处理中或已处理，不能重放");db.update("update outbox_event set status='PENDING',attempts=0,available_at=now() where tenant_id=? and id=?",t,id);new Audit(db).record(t,p.getName(),"EVENT_REPLAY",id);}
}
