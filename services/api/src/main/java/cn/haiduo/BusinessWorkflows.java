package cn.haiduo;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import cn.haiduo.audit.Audit;

/** Fixed command whitelist. Replay is a private thread scope, never a client flag. */
final class BusinessWorkflows {
 private static final ThreadLocal<Boolean> REVIEWED=new ThreadLocal<>();
 static final ObjectMapper JSON=new ObjectMapper();
 static String json(Object value){return JSON.writeValueAsString(value);}
 static <T>T read(String value,Class<T> type){return JSON.readValue(value,type);}
 static Object reviewed(Supplier<Object> command){if(Boolean.TRUE.equals(REVIEWED.get()))throw new IllegalStateException("Nested workflow replay");REVIEWED.set(true);try{return command.get();}finally{REVIEWED.remove();}}
 static void fail(HttpStatus status,String message){throw new ResponseStatusException(status,message);}
 static String role(JdbcTemplate db,UUID t,String user){return db.query("select role from membership where tenant_id=? and username=?",(rs,n)->rs.getString(1),t,user).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"成员权限已撤销"));}
 static int grade(JdbcTemplate db,UUID t,String user){return db.queryForObject("select case when m.role='ADMIN' then 4 else coalesce(g.grade,2) end from membership m left join member_grade g on g.tenant_id=m.tenant_id and g.username=m.username where m.tenant_id=? and m.username=?",Integer.class,t,user);}
 static boolean security(String kind){return Set.of("MEMBER_ROLE","MEMBER_GRADE").contains(kind);}
 static boolean critical(String kind){return security(kind)||Set.of("PRODUCT_ARCHIVE","SKU_CODE","STOCK_COUNT","RECEIPT_PARTIAL","RETURN_RECEIVE").contains(kind);}
 static String duty(String kind){return switch(kind){case "PRODUCT_CREATE","SKU_CREATE","ORDER_RESERVE","ORDER_CANCEL","CSV_IMPORT","PRODUCT_EDIT","PRODUCT_ARCHIVE","SKU_CODE","ORDER_EDIT","ORDER_COPY","STORE_CREATE","LISTING_SAVE","STORE_SYNC","PRODUCT_IMAGE","KNOWLEDGE_IMPORT","COPY_DRAFT"->"OPERATIONS";case "ORDER_SHIP","STOCK_COUNT","RECEIPT_PARTIAL","RETURN_RECEIVE","SHIPMENT_EDIT","TRACKING_EVENT"->"WAREHOUSE";case "FEE_SAVE","PROFIT_CONFIRM","SYNC_SETTINGS"->"FINANCE";case "MEMBER_ROLE","MEMBER_GRADE"->"ADMIN";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的审批操作");};}
 static void authorize(JdbcTemplate db,UUID t,String user,String kind){String role=role(db,t,user);if(!role.equals("ADMIN")&&!role.equals(duty(kind)))fail(HttpStatus.FORBIDDEN,"无对应业务职责");}
 static void lock(JdbcTemplate db,UUID t){if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Workflow requires transaction");db.queryForObject("select id from tenant where id=? for no key update",UUID.class,t);}
 static String snapshot(JdbcTemplate db,UUID t,String kind,String target){
  if(security(kind))return json(db.queryForList("select m.role,coalesce(g.grade,2) as grade from membership m left join member_grade g on g.tenant_id=m.tenant_id and g.username=m.username where m.tenant_id=? and m.username=?",t,target));
  if(kind.startsWith("ORDER_")||Set.of("FEE_SAVE","PROFIT_CONFIRM","RETURN_RECEIVE").contains(kind))return json(db.queryForList("select o.status,o.inventory_revision,o.source_updated_at,o.import_voided,o.sync_review_required,(select count(*) from business_history h where h.tenant_id=o.tenant_id and h.target_id=o.id::text) as edits,(select count(*) from business_adjustment a where a.tenant_id=o.tenant_id and a.target_id=o.id) as adjustments,(select string_agg(kind||amount_cny::text,',' order by kind) from order_fee f where f.tenant_id=o.tenant_id and f.order_id=o.id) as fees from sales_order o where o.tenant_id=? and o.id=?",t,UUID.fromString(target)));
  if(Set.of("PRODUCT_EDIT","PRODUCT_ARCHIVE","PRODUCT_IMAGE","SKU_CODE").contains(kind))return json(db.queryForList("select k.version,k.code,k.name,k.cost::text,p.name as product_name,p.image_file_id,coalesce(c.archived,false) as archived,coalesce(c.version,0) as product_version from sku k join product p on p.id=k.product_id and p.tenant_id=k.tenant_id left join product_control c on c.product_id=p.id and c.tenant_id=p.tenant_id where k.tenant_id=? and k.id=?",t,UUID.fromString(target)));
  if(kind.equals("STOCK_COUNT")){String[] parts=target.split(":");return json(db.queryForList("select on_hand,reserved from inventory where tenant_id=? and sku_id=? and warehouse_id=?",t,UUID.fromString(parts[0]),UUID.fromString(parts[1])));}
  if(kind.equals("RECEIPT_PARTIAL"))return json(db.queryForList("select r.quantity,exists(select 1 from receipt_reversal v where v.tenant_id=r.tenant_id and v.receipt_key=r.request_key) as reversed,coalesce((select sum(a.quantity) from business_adjustment a where a.tenant_id=r.tenant_id and a.target_id=r.request_key and a.kind='RECEIPT_PARTIAL'),0) as partial from stock_receipt r where r.tenant_id=? and r.request_key=?",t,UUID.fromString(target)));
  if(kind.equals("SHIPMENT_EDIT")||kind.equals("TRACKING_EVENT"))return json(db.queryForList("select carrier,tracking_number,event_version,edit_version,delivery_status from shipment where tenant_id=? and id=?",t,UUID.fromString(target)));
  return "{}";
 }
 static Object propose(JdbcTemplate db,UUID t,String actor,String kind,String target,Object payload){
  if(Boolean.TRUE.equals(REVIEWED.get()))return null;
  authorize(db,t,actor,kind);
  if(!critical(kind)&&grade(db,t,actor)>1)return null;
  lock(db,t);authorize(db,t,actor,kind);
  String body=json(payload);if(body.length()>150000)fail(HttpStatus.BAD_REQUEST,"申请内容过长");
  String before=snapshot(db,t,kind,target),hash;if(before.equals("[]"))fail(HttpStatus.NOT_FOUND,"对象不存在或不属当前企业");
  try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((kind+"\n"+target+"\n"+body+"\n"+before).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  var old=db.queryForList("select id,status from business_request where tenant_id=? and applicant=? and request_hash=?",t,actor,hash);
  if(!old.isEmpty())return Map.of("id",old.getFirst().get("id"),"status",old.getFirst().get("status"));
  UUID id=UUID.randomUUID();db.update("insert into business_request(id,tenant_id,applicant,kind,target_id,payload,before_state,request_hash) values(?,?,?,?,?,?,?,?)",id,t,actor,kind,target,body,before,hash);new Audit(db).record(t,actor,"BUSINESS_REQUEST",id);
  return Map.of("id",id,"status","PENDING");
 }
 static void active(JdbcTemplate db,UUID t,UUID sku){if(db.queryForObject("select count(*) from sku k join product_control c on c.product_id=k.product_id and c.tenant_id=k.tenant_id where k.tenant_id=? and k.id=? and c.archived",Integer.class,t,sku)>0)fail(HttpStatus.CONFLICT,"商品已归档，不能新增业务；历史履约仍可继续");}
}
