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
  for(var item:items){int q=(Integer)item.get("quantity");Object sku=item.get("sku_id");
   String sql=switch(action){case "reserve"->"update inventory set reserved=reserved+? where tenant_id=? and sku_id=? and warehouse_id=? and on_hand-reserved>=?";case "cancel"->"update inventory set reserved=reserved-? where tenant_id=? and sku_id=? and warehouse_id=? and reserved>=?";default->"update inventory set on_hand=on_hand-?,reserved=reserved-? where tenant_id=? and sku_id=? and warehouse_id=? and reserved>=?";};
   int count=action.equals("ship")?db.update(sql,q,q,tenant,sku,warehouse,q):db.update(sql,q,tenant,sku,warehouse,q);
   if(count!=1)throw new IllegalStateException("库存不足");if(action.equals("reserve"))db.update("insert into inventory_reservation(tenant_id,order_id,sku_id,warehouse_id,quantity,status) values(?,?,?,?,?,'RESERVED')",tenant,order,sku,warehouse,q);else db.update("update inventory_reservation set status=? where tenant_id=? and order_id=? and sku_id=?",action.equals("ship")?"CONSUMED":"RELEASED",tenant,order,sku);
   db.update("insert into inventory_movement(id,tenant_id,order_id,sku_id,kind,quantity,warehouse_id) values(?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,order,sku,action,q,warehouse);
  }
  db.update("update sales_order set status=? where tenant_id=? and id=?",states.get(action),tenant,order);
 }
}
