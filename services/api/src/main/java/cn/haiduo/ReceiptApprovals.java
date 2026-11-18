package cn.haiduo;

import cn.haiduo.audit.Audit;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Ordinary receipts: L1 proposes; L2+ may apply directly. All paths share one request ledger. */
@RestController @RequestMapping("/api/receipt-approvals")
public class ReceiptApprovals {
 private final JdbcTemplate db; private final OperationsController scope;
 public ReceiptApprovals(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 private SkuApprovals policy(){return new SkuApprovals(db,scope);}
 private void fail(HttpStatus code,String message){throw new ResponseStatusException(code,message);}
 private UUID lockedTenant(Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","WAREHOUSE");db.queryForObject("select id from tenant where id=? for no key update",UUID.class,t);scope.tenant(p,s,"ADMIN","WAREHOUSE");return t;}
 @GetMapping Object list(Principal p,HttpSession s){UUID t=scope.tenant(p,s);return db.queryForList("select a.*,k.code,w.name as warehouse_name from receipt_approval a join sku k on k.tenant_id=a.tenant_id and k.id=a.sku_id join warehouse w on w.tenant_id=a.tenant_id and w.id=a.warehouse_id where a.tenant_id=? and (a.applicant=? or ?>=3) order by a.created_at desc limit 200",t,p.getName(),policy().grade(t,p.getName()));}
 // Invoked from the existing transactional receipt endpoint, so old clients cannot bypass approval.
 Object receive(FulfillmentController.Receipt r,Principal p,HttpSession s){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("入库必须在事务中执行");
  UUID t=lockedTenant(p,s),warehouse=r.warehouseId()==null?UUID.fromString("00000000-0000-0000-0000-000000000001"):r.warehouseId();
  if(r.requestKey()==null||r.skuId()==null||r.quantity()<1||r.quantity()>1000000)fail(HttpStatus.BAD_REQUEST,"请输入有效入库数量和请求键");
  var prior=db.queryForList("select * from receipt_approval where tenant_id=? and request_key=?",t,r.requestKey());
  if(!prior.isEmpty()){var a=prior.getFirst();if(!a.get("applicant").equals(p.getName()))fail(HttpStatus.FORBIDDEN,"请求键属于其他成员");same(a,r,warehouse);return Map.of("id",r.requestKey(),"status",a.get("status"),"duplicate",true);}
  // Requests created before V27 retain their original idempotency semantics.
  var legacy=db.queryForList("select * from stock_receipt where tenant_id=? and request_key=?",t,r.requestKey());
  if(!legacy.isEmpty()){same(legacy.getFirst(),r,warehouse);return Map.of("id",r.requestKey(),"status","APPLIED","duplicate",true);}
  if(db.queryForObject("select count(*) from inventory where tenant_id=? and sku_id=? and warehouse_id=?",Integer.class,t,r.skuId(),warehouse)!=1)fail(HttpStatus.NOT_FOUND,"SKU或仓库库存记录不存在");
  String status=policy().grade(t,p.getName())==1?"PENDING":"APPLIED";
  db.update("insert into receipt_approval(tenant_id,request_key,applicant,sku_id,warehouse_id,quantity,status) values(?,?,?,?,?,?,?)",t,r.requestKey(),p.getName(),r.skuId(),warehouse,r.quantity(),status);
  if(status.equals("APPLIED"))apply(t,r.requestKey(),r.skuId(),warehouse,r.quantity(),p.getName());
  else new Audit(db).record(t,p.getName(),"RECEIPT_APPROVAL_REQUEST",r.requestKey());
  return Map.of("id",r.requestKey(),"status",status,"duplicate",false);
 }
 private void same(Map<String,Object> old,FulfillmentController.Receipt r,UUID warehouse){if(!old.get("sku_id").equals(r.skuId())||!old.get("warehouse_id").equals(warehouse)||((Number)old.get("quantity")).intValue()!=r.quantity())fail(HttpStatus.CONFLICT,"幂等键已用于其他入库请求");}
 private void apply(UUID t,UUID key,UUID sku,UUID warehouse,int quantity,String actor){
  int updated=db.update("update inventory set on_hand=on_hand+? where tenant_id=? and sku_id=? and warehouse_id=? and on_hand<=?",quantity,t,sku,warehouse,Integer.MAX_VALUE-quantity);
  if(updated!=1)fail(HttpStatus.CONFLICT,"库存记录缺失或超过数量上限，入库未生效");
  db.update("insert into stock_receipt(tenant_id,request_key,sku_id,quantity,warehouse_id) values(?,?,?,?,?)",t,key,sku,quantity,warehouse);
  new Audit(db).record(t,actor,"STOCK_RECEIPT",key);
 }
 record Decision(String action,String comment){}
 @PostMapping("/{id}") @Transactional Object decide(@PathVariable UUID id,@RequestBody Decision r,Principal p,HttpSession s){
  UUID t=scope.tenant(p,s);BusinessWorkflows.lock(db,t);scope.tenant(p,s);var rows=db.queryForList("select *,expires_at<=now() as expired from receipt_approval where tenant_id=? and request_key=? for update",t,id);
  if(rows.isEmpty())fail(HttpStatus.NOT_FOUND,"入库申请不存在");var a=rows.getFirst();String applicant=(String)a.get("applicant");
  if(r.action()==null||!Set.of("approve","reject","withdraw").contains(r.action())||r.comment()!=null&&r.comment().length()>500)fail(HttpStatus.BAD_REQUEST,"无效操作或意见过长");
  if(r.action().equals("withdraw")){if(!applicant.equals(p.getName()))fail(HttpStatus.FORBIDDEN,"仅申请人可撤回");}
  else {if(!Set.of("ADMIN","WAREHOUSE").contains(policy().role(t,p.getName())))fail(HttpStatus.FORBIDDEN,"需仓库主管或管理员");if(applicant.equals(p.getName())||policy().grade(t,p.getName())<3||r.action().equals("approve")&&policy().grade(t,p.getName())<=policy().grade(t,applicant))fail(HttpStatus.FORBIDDEN,"须由更高职级独立审批");if(r.action().equals("approve")&&!Set.of("ADMIN","WAREHOUSE").contains(policy().role(t,applicant)))fail(HttpStatus.FORBIDDEN,"申请人已无仓库职责");}
  if(!a.get("status").equals("PENDING"))return Map.of("status",a.get("status"));
  String status;
  if(Boolean.TRUE.equals(a.get("expired")))status="EXPIRED";
  else if(r.action().equals("withdraw"))status="WITHDRAWN";
  else if(r.action().equals("reject")){if(r.comment()==null||r.comment().isBlank())fail(HttpStatus.BAD_REQUEST,"请填写驳回原因");status="REJECTED";}
  else {apply(t,id,(UUID)a.get("sku_id"),(UUID)a.get("warehouse_id"),((Number)a.get("quantity")).intValue(),p.getName());status="APPLIED";}
  db.update("update receipt_approval set status=?,reviewer=?,comment=?,reviewed_at=now() where tenant_id=? and request_key=?",status,p.getName(),r.comment(),t,id);
  new Audit(db).record(t,p.getName(),"RECEIPT_APPROVAL_"+status,id);return Map.of("status",status);
 }
}
