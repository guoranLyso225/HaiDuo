package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;

public class CsvBatchService {
 private final JdbcTemplate db;public CsvBatchService(JdbcTemplate db){this.db=db;}
 private Map<String,Object> batch(UUID tenant,UUID id,boolean lock){var rows=db.queryForList("select * from csv_import_batch where tenant_id=? and id=?"+(lock?" for update":""),tenant,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"批次不存在");return rows.getFirst();}
 private List<Map<String,Object>> orders(UUID tenant,UUID id){return db.queryForList("""
 select o.id,o.store,o.external_id,o.status,o.import_voided,o.net::text as net,o.currency,
 case when o.import_voided then '已经撤销' when o.status<>'NEW' then '订单不再是NEW'
 when o.inventory_revision<>0 or exists(select 1 from inventory_movement m where m.tenant_id=o.tenant_id and m.order_id=o.id) or exists(select 1 from inventory_reservation r where r.tenant_id=o.tenant_id and r.order_id=o.id) then '已有库存操作历史'
 when o.payment_status not in('UNKNOWN','PENDING') or o.refund_status<>'NONE' then '已有付款或退款状态'
 when o.source_updated_at is not null or o.externally_fulfilled or o.sync_review_required then '已有平台同步或需核对'
 when exists(select 1 from order_fee f where f.tenant_id=o.tenant_id and f.order_id=o.id) then '已有费用记录'
 when exists(select 1 from shipment s where s.tenant_id=o.tenant_id and s.order_id=o.id) then '已有运单'
 when exists(select 1 from profit_confirmation p where p.tenant_id=o.tenant_id and p.order_id=o.id) then '已有实际贡献确认'
 when exists(select 1 from audit_log a where a.tenant_id=o.tenant_id and a.target_id=o.id::text and a.action<>'ORDER_IMPORT') then '已有后续业务修改'
 else '' end as blocked_reason
 from csv_import_order b join sales_order o on o.tenant_id=b.tenant_id and o.id=b.order_id where b.tenant_id=? and b.batch_id=? order by o.id
 """,tenant,id);}
 public Map<String,Object> detail(UUID tenant,UUID id){var batch=batch(tenant,id,false);var orders=orders(tenant,id);long blocked=orders.stream().filter(o->!o.get("blocked_reason").equals("")).count();boolean can=batch.get("status").equals("ACTIVE")&&((Number)batch.get("imported")).intValue()>0&&orders.size()==((Number)batch.get("imported")).intValue()&&blocked==0;boolean pending=db.queryForObject("select count(*) from inventory_correction where tenant_id=? and kind='CSV_ROLLBACK' and target_id=? and status='PENDING' and expires_at>now()",Integer.class,tenant,id)>0;return Map.of("batch",batch,"orders",orders,"blocked",blocked,"canRollback",can,"pending",pending);}
 public void validateRequest(UUID tenant,UUID id){var detail=detail(tenant,id);if(!Boolean.TRUE.equals(detail.get("canRollback")))throw new ResponseStatusException(HttpStatus.CONFLICT,"批次无新增订单、已撤销或存在后续业务，不能整批撤销");if(Boolean.TRUE.equals(detail.get("pending")))throw new ResponseStatusException(HttpStatus.CONFLICT,"该批次已有待审申请，请到审批中心查看");}
 public String rollback(UUID tenant,UUID id,String actor){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("批次撤销必须在业务事务中执行");var batch=batch(tenant,id,true);if(!batch.get("status").equals("ACTIVE"))return "STALE";db.queryForList("select o.id from csv_import_order b join sales_order o on o.id=b.order_id and o.tenant_id=b.tenant_id where b.tenant_id=? and b.batch_id=? order by o.id for update of o",tenant,id);var detail=detail(tenant,id);if(!Boolean.TRUE.equals(detail.get("canRollback")))return "STALE";
  @SuppressWarnings("unchecked") var orders=(List<Map<String,Object>>)detail.get("orders");for(var order:orders){int count=db.update("update sales_order set status='CANCELLED',import_voided=true where tenant_id=? and id=? and status='NEW' and not import_voided",tenant,order.get("id"));if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"批次订单已变化，整批未生效");new Audit(db).record(tenant,actor,"CSV_IMPORT_VOID",order.get("id"));}db.update("update csv_import_batch set status='ROLLED_BACK',rolled_back_at=now() where tenant_id=? and id=?",tenant,id);new Audit(db).record(tenant,actor,"CSV_BATCH_ROLLBACK",id);return "APPLIED";
 }
}
