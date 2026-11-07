package cn.haiduo.audit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component public class Audit {
 private final JdbcTemplate db;public Audit(JdbcTemplate db){this.db=db;}
 public void record(UUID tenant,String actor,String action,Object target){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Audit/outbox require a business transaction");db.update("insert into audit_log(id,tenant_id,actor,action,target_id) values(?,?,?,?,?)",UUID.randomUUID(),tenant,actor,action,target.toString());db.update("insert into outbox_event(id,tenant_id,kind,target_id) values(?,?,?,?)",UUID.randomUUID(),tenant,action,target.toString());}
}
