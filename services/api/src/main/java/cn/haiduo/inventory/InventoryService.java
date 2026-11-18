package cn.haiduo.inventory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class InventoryService {
 private final JdbcTemplate db;
 public InventoryService(JdbcTemplate db){this.db=db;}
 @Transactional public void transition(UUID tenant,UUID order,String action){
  var states=Map.of("reserve","RESERVED","cancel","CANCELLED","ship","SHIPPED");
  if(!states.containsKey(action))throw new IllegalArgumentException("未知操作");
  UUID warehouse=db.queryForObject("select warehouse_id from sales_order where tenant_id=? and id=?",UUID.class,tenant,order);String state=db.queryForObject("select status from sales_order where tenant_id=? and id=? for update",String.class,tenant,order);
  if(states.get(action).equals(state))return;if((action.equals("reserve")||action.equals("ship"))&&Boolean.TRUE.equals(db.queryForObject("select sync_review_required from sales_order where tenant_id=? and id=?",Boolean.class,tenant,order)))throw new IllegalStateException("平台履约或行项目变化需要人工核对");
  if(action.equals("cancel")&&state.equals("NEW")){
   db.update("update sales_order set status='CANCELLED' where tenant_id=? and id=?",tenant,order);
   return;
  }
  if(!(action.equals("reserve")&&state.equals("NEW") || !action.equals("reserve")&&state.equals("RESERVED")))throw new IllegalStateException("订单状态不允许该操作");
  var items=db.queryForList("select sku_id,quantity from order_item where tenant_id=? and order_id=? order by sku_id",tenant,order);
  if(items.isEmpty())throw new IllegalStateException("空订单");
  for(var item:items){if(action.equals("reserve")&&db.queryForObject("select count(*) from product_control c join sku k on k.product_id=c.product_id and k.tenant_id=c.tenant_id where k.tenant_id=? and k.id=? and c.archived",Integer.class,tenant,item.get("sku_id"))>0)throw new IllegalStateException("商品已归档，禁止新预占");int q=(Integer)item.get("quantity");Object sku=item.get("sku_id");
   String sql=switch(action){case "reserve"->"update inventory set reserved=reserved+? where tenant_id=? and sku_id=? and warehouse_id=? and on_hand-reserved>=?";case "cancel"->"update inventory set reserved=reserved-? where tenant_id=? and sku_id=? and warehouse_id=? and reserved>=?";default->"update inventory set on_hand=on_hand-?,reserved=reserved-? where tenant_id=? and sku_id=? and warehouse_id=? and reserved>=?";};
   int count=action.equals("ship")?db.update(sql,q,q,tenant,sku,warehouse,q):db.update(sql,q,tenant,sku,warehouse,q);
   if(count!=1)throw new IllegalStateException("库存不足");if(action.equals("reserve")){int reserved=db.update("insert into inventory_reservation(tenant_id,order_id,sku_id,warehouse_id,quantity,status) values(?,?,?,?,?,'RESERVED') on conflict(tenant_id,order_id,sku_id) do update set warehouse_id=excluded.warehouse_id,quantity=excluded.quantity,status='RESERVED' where inventory_reservation.status='RELEASED'",tenant,order,sku,warehouse,q);if(reserved!=1)throw new IllegalStateException("预占记录状态冲突");}else db.update("update inventory_reservation set status=? where tenant_id=? and order_id=? and sku_id=?",action.equals("ship")?"CONSUMED":"RELEASED",tenant,order,sku);
   db.update("insert into inventory_movement(id,tenant_id,order_id,sku_id,kind,quantity,warehouse_id,cycle) select ?,?,?,?,?,?,?,inventory_revision+1 from sales_order where tenant_id=? and id=?",UUID.randomUUID(),tenant,order,sku,action,q,warehouse,tenant,order);
  }
  db.update("update sales_order set status=?,inventory_revision=inventory_revision+1 where tenant_id=? and id=?",states.get(action),tenant,order);
 }
 @Transactional public void release(UUID tenant,UUID order,long expectedRevision){
  var row=db.queryForMap("select status,warehouse_id,inventory_revision from sales_order where tenant_id=? and id=? for update",tenant,order);
  if(!row.get("status").equals("RESERVED")||((Number)row.get("inventory_revision")).longValue()!=expectedRevision)throw new IllegalStateException("订单状态已变化，请重新申请");
  var lines=db.queryForList("select sku_id,quantity from inventory_reservation where tenant_id=? and order_id=? and status='RESERVED' order by sku_id",tenant,order);
  if(lines.isEmpty())throw new IllegalStateException("订单无有效预占记录");
  for(var line:lines){int q=((Number)line.get("quantity")).intValue();if(db.update("update inventory set reserved=reserved-? where tenant_id=? and sku_id=? and warehouse_id=? and reserved>=?",q,tenant,line.get("sku_id"),row.get("warehouse_id"),q)!=1)throw new IllegalStateException("预占库存不一致，需核对");db.update("update inventory_reservation set status='RELEASED' where tenant_id=? and order_id=? and sku_id=?",tenant,order,line.get("sku_id"));db.update("insert into inventory_movement(id,tenant_id,order_id,sku_id,kind,quantity,warehouse_id,cycle) values(?,?,?,?,'release',?,?,?)",UUID.randomUUID(),tenant,order,line.get("sku_id"),q,row.get("warehouse_id"),expectedRevision+1);}
  db.update("update sales_order set status='NEW',inventory_revision=inventory_revision+1 where tenant_id=? and id=?",tenant,order);
 }
}
