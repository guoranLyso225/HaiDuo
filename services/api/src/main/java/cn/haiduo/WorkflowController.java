package cn.haiduo;

import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import cn.haiduo.audit.Audit;

@RestController @RequestMapping("/api/workflows")
public class WorkflowController {
 private final JdbcTemplate db;private final OperationsController scope;private final ApplicationContext context;
 public WorkflowController(JdbcTemplate db,OperationsController scope,ApplicationContext context){this.db=db;this.scope=scope;this.context=context;}
 @GetMapping Object list(Principal p,HttpSession s){UUID t=scope.tenant(p,s);return db.queryForList("select id,kind,target_id,payload,before_state,applicant,status,reviewer,comment,result,created_at,expires_at from business_request where tenant_id=? and (applicant=? or ?>=3) order by created_at desc limit 200",t,p.getName(),BusinessWorkflows.grade(db,t,p.getName()));}
 record Decision(String action,String comment){}
 @PostMapping("/{id}/resubmit") @Transactional Object resubmit(@PathVariable UUID id,Principal p,HttpSession s){
  UUID t=scope.tenant(p,s);BusinessWorkflows.lock(db,t);var rows=db.queryForList("select * from business_request where tenant_id=? and id=? for update",t,id);if(rows.isEmpty())BusinessWorkflows.fail(HttpStatus.NOT_FOUND,"申请不存在");var a=rows.getFirst();if(!a.get("applicant").equals(p.getName()))BusinessWorkflows.fail(HttpStatus.FORBIDDEN,"仅申请人可重新提交");if(!Set.of("REJECTED","WITHDRAWN","EXPIRED").contains(a.get("status")))BusinessWorkflows.fail(HttpStatus.CONFLICT,"仅驳回、撤回或过期申请可重新提交；状态变化须重新编辑或预览");if(a.get("result")!=null)return BusinessWorkflows.read((String)a.get("result"),Map.class);BusinessWorkflows.authorize(db,t,p.getName(),(String)a.get("kind"));db.update("update business_request set request_hash=request_hash||':'||id::text where id=? and tenant_id=?",id,t);Object result=execute((String)a.get("kind"),(String)a.get("target_id"),(String)a.get("payload"),p,s);db.update("update business_request set result=? where id=? and tenant_id=?",BusinessWorkflows.json(result),id,t);new Audit(db).record(t,p.getName(),"WORKFLOW_RESUBMIT",id);return result;
 }
 @PostMapping("/{id}") @Transactional Object decide(@PathVariable UUID id,@RequestBody Decision r,Principal p,HttpSession s){
  UUID t=scope.tenant(p,s);BusinessWorkflows.lock(db,t);scope.tenant(p,s);
  var rows=db.queryForList("select *,expires_at<=now() as expired from business_request where tenant_id=? and id=? for update",t,id);if(rows.isEmpty())BusinessWorkflows.fail(HttpStatus.NOT_FOUND,"申请不存在");var a=rows.getFirst();String actor=(String)a.get("applicant"),kind=(String)a.get("kind");
  if(r.action()==null||!Set.of("approve","reject","withdraw").contains(r.action())||r.comment()!=null&&r.comment().length()>500)BusinessWorkflows.fail(HttpStatus.BAD_REQUEST,"操作或意见无效");
  if(r.action().equals("withdraw")){if(!actor.equals(p.getName()))BusinessWorkflows.fail(HttpStatus.FORBIDDEN,"仅申请人可撤回");}
  else {BusinessWorkflows.authorize(db,t,p.getName(),kind);int reviewer=BusinessWorkflows.grade(db,t,p.getName()),applicant=db.queryForObject("select count(*) from membership where tenant_id=? and username=?",Integer.class,t,actor)>0?BusinessWorkflows.grade(db,t,actor):0;if(actor.equals(p.getName())||reviewer<3||reviewer<applicant||!BusinessWorkflows.critical(kind)&&reviewer==applicant)BusinessWorkflows.fail(HttpStatus.FORBIDDEN,"须由独立合格主管或管理员审批");if(BusinessWorkflows.security(kind)&&reviewer!=4)BusinessWorkflows.fail(HttpStatus.FORBIDDEN,"权限变更须由独立管理员审批");if(r.action().equals("approve"))BusinessWorkflows.authorize(db,t,actor,kind);}
  if(!a.get("status").equals("PENDING"))return Map.of("status",a.get("status"));
  String status;Object result=null;
  if(Boolean.TRUE.equals(a.get("expired")))status="EXPIRED";
  else if(r.action().equals("withdraw"))status="WITHDRAWN";
  else if(r.action().equals("reject")){if(r.comment()==null||r.comment().isBlank())BusinessWorkflows.fail(HttpStatus.BAD_REQUEST,"请填写驳回原因");status="REJECTED";}
  else if(!a.get("before_state").equals(BusinessWorkflows.snapshot(db,t,kind,(String)a.get("target_id")))||csvChanged(t,kind,(String)a.get("payload")))status="STALE";
  else {result=BusinessWorkflows.reviewed(()->execute(kind,(String)a.get("target_id"),(String)a.get("payload"),()->actor,s));status="APPLIED";}
  db.update("update business_request set status=?,reviewer=?,comment=?,result=?,reviewed_at=now() where tenant_id=? and id=?",status,p.getName(),r.comment(),result==null?null:BusinessWorkflows.json(result),t,id);new Audit(db).record(t,p.getName(),"WORKFLOW_"+status,id);return result==null?Map.of("status",status):Map.of("status",status,"result",result);
 }
 private boolean csvChanged(UUID t,String kind,String payload){if(!kind.equals("CSV_IMPORT"))return false;var r=BusinessWorkflows.read(payload,OperationsController.Csv.class);var current=(Map<?,?>)new cn.haiduo.orders.CsvOrders(db).preview(t,r.warehouseId(),r.csv());return !Boolean.TRUE.equals(current.get("canImport"))||!Objects.equals(current.get("digest"),r.previewDigest());}
 Object execute(String kind,String target,String body,Principal p,HttpSession s){return switch(kind){
  case "PRODUCT_CREATE"->context.getBean(ProductController.class).create(BusinessWorkflows.read(body,ProductController.CreateProduct.class),p,s);
  case "SKU_CREATE"->context.getBean(ProductController.class).addSku(UUID.fromString(target),BusinessWorkflows.read(body,ProductController.CreateProduct.class),p,s);
  case "ORDER_RESERVE","ORDER_CANCEL"->context.getBean(OperationsController.class).transition(UUID.fromString(target),kind.equals("ORDER_RESERVE")?"reserve":"cancel",BusinessWorkflows.read(body,OperationsController.TransitionRequest.class),p,s);
  case "ORDER_SHIP"->context.getBean(FulfillmentController.class).ship(UUID.fromString(target),BusinessWorkflows.read(body,FulfillmentController.Ship.class),p,s);
  case "CSV_IMPORT"->context.getBean(OperationsController.class).importCsv(BusinessWorkflows.read(body,OperationsController.Csv.class),p,s);
  case "MEMBER_ROLE"->context.getBean(MemberController.class).change(BusinessWorkflows.read(body,MemberController.Change.class),p,s);
  case "MEMBER_GRADE"->context.getBean(SkuApprovals.class).grade(BusinessWorkflows.read(body,SkuApprovals.Grade.class),p,s);
  case "FEE_SAVE"->context.getBean(FeeController.class).save(UUID.fromString(target),BusinessWorkflows.read(body,FeeController.Fee.class),p,s);
  case "PROFIT_CONFIRM"->context.getBean(ProfitConfirmationController.class).confirm(UUID.fromString(target),BusinessWorkflows.read(body,ProfitConfirmationController.Confirm.class),p,s);
  case "PRODUCT_EDIT","PRODUCT_ARCHIVE","SKU_CODE","STOCK_COUNT","RECEIPT_PARTIAL","RETURN_RECEIVE","SHIPMENT_EDIT","ORDER_EDIT","ORDER_COPY","PRODUCT_IMAGE"->context.getBean(BusinessEdits.class).save(BusinessWorkflows.read(body,BusinessEdits.Edit.class),p,s);
  case "STORE_CREATE"->context.getBean(StoreController.class).create(BusinessWorkflows.read(body,StoreController.Create.class),p,s);
  case "LISTING_SAVE"->context.getBean(ListingController.class).save(UUID.fromString(target),BusinessWorkflows.read(body,ListingController.Mapping.class),p,s);
  case "SYNC_SETTINGS"->context.getBean(StoreSyncController.class).settings(UUID.fromString(target),BusinessWorkflows.read(body,StoreSyncController.Settings.class),p,s);
  case "STORE_SYNC"->context.getBean(StoreSyncController.class).sync(UUID.fromString(target),p,s);
  case "KNOWLEDGE_IMPORT"->context.getBean(AiController.class).importText(BusinessWorkflows.read(body,AiController.Import.class),p,s);
  case "COPY_DRAFT"->context.getBean(AiController.class).draft(BusinessWorkflows.read(body,AiController.Draft.class),p,s);
  case "TRACKING_EVENT"->context.getBean(TrackingController.class).simulate(UUID.fromString(target),BusinessWorkflows.read(body,TrackingController.Event.class),p,s);
  default->throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"操作未启用");
 };}
}
