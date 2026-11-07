package cn.haiduo;
import cn.haiduo.inventory.InventoryService;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_DATABASE_TESTS",matches="true")
class InventoryDatabaseTest {
 JdbcTemplate db; TransactionTemplate tx; UUID tenant,sku;
 @BeforeEach void setup(){
  var ds=new DriverManagerDataSource(System.getProperty("haiduo.test.url",System.getenv("DATABASE_URL")),"haiduo",System.getProperty("haiduo.test.password",System.getenv("DB_PASSWORD")));
  db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  tenant=UUID.randomUUID();sku=UUID.randomUUID();
  db.update("insert into tenant values(?,?)",tenant,"integration-test");
  db.update("insert into warehouse values(?,?::uuid,?)",tenant,"00000000-0000-0000-0000-000000000001","test");db.update("insert into product(id,tenant_id,name) values(?,?,?)",sku,tenant,"test");db.update("insert into sku values(?,?,?,?,?,?)",sku,tenant,sku.toString(),"test",200,sku);db.update("insert into store values(?,?,?,'MOCK','MOCK')",UUID.randomUUID(),tenant,"test");
  db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved) values(?,?,100,0)",tenant,sku);
 }
 UUID order(UUID product,int quantity){UUID id=UUID.randomUUID();db.update("insert into sales_order(id,tenant_id,store,external_id,net,rate,currency,rate_source,rate_at) values(?,?,?,?,100,7.2,'USD','test',now())",id,tenant,"test",id.toString());db.update("insert into order_item(tenant_id,order_id,sku_id,quantity) values(?,?,?,?)",tenant,id,product,quantity);return id;}
 void transition(UUID t,UUID id,String action){tx.executeWithoutResult(s->new InventoryService(db).transition(t,id,action));}
 @AfterEach void cleanup(){if(db==null)return;db.update("delete from inventory_reservation where tenant_id=?",tenant);db.update("delete from inventory_movement where tenant_id=?",tenant);db.update("delete from order_item where tenant_id=?",tenant);db.update("delete from sales_order where tenant_id=?",tenant);db.update("delete from inventory where tenant_id=?",tenant);db.update("delete from sku where tenant_id=?",tenant);db.update("delete from product where tenant_id=?",tenant);db.update("delete from store where tenant_id=?",tenant);db.update("delete from warehouse where tenant_id=?",tenant);/* Immutable snapshots retain their tenant until this disposable test database is destroyed. */}
 @Test void concurrentReservations() throws Exception {
  var ids=new ArrayList<UUID>();for(int i=0;i<200;i++)ids.add(order(sku,1));
  try(var pool=Executors.newFixedThreadPool(20)){
   var tasks=ids.stream().<Callable<Boolean>>map(id->()->{try{transition(tenant,id,"reserve");return true;}catch(IllegalStateException e){assertEquals("库存不足",e.getMessage());return false;}}).toList();
   int success=0;for(var result:pool.invokeAll(tasks))if(result.get())success++;
   assertEquals(100,success);
  }
  assertEquals(100,db.queryForObject("select reserved from inventory where tenant_id=?",Integer.class,tenant));
 }
 @Test void duplicateAndTenantBoundary(){var id=order(sku,1);assertThrows(RuntimeException.class,()->transition(UUID.randomUUID(),id,"reserve"));for(int i=0;i<10;i++)transition(tenant,id,"reserve");for(int i=0;i<10;i++)transition(tenant,id,"ship");assertEquals(99,db.queryForObject("select on_hand from inventory where tenant_id=?",Integer.class,tenant));assertEquals(2,db.queryForObject("select count(*) from inventory_movement where tenant_id=?",Integer.class,tenant));}
 @Test void cancelNewWithoutStockEffects(){var id=order(sku,1);for(int i=0;i<10;i++)transition(tenant,id,"cancel");assertEquals("CANCELLED",db.queryForObject("select status from sales_order where id=?",String.class,id));assertEquals(100,db.queryForObject("select on_hand from inventory where tenant_id=?",Integer.class,tenant));assertEquals(0,db.queryForObject("select count(*) from inventory_movement where tenant_id=?",Integer.class,tenant));assertThrows(IllegalStateException.class,()->transition(tenant,id,"reserve"));}
 @Test void cancelReservedReleasesExactlyOnce(){var id=order(sku,5);transition(tenant,id,"reserve");for(int i=0;i<10;i++)transition(tenant,id,"cancel");assertEquals(0,db.queryForObject("select reserved from inventory where tenant_id=?",Integer.class,tenant));assertEquals(100,db.queryForObject("select on_hand from inventory where tenant_id=?",Integer.class,tenant));assertEquals(2,db.queryForObject("select count(*) from inventory_movement where tenant_id=?",Integer.class,tenant));assertThrows(IllegalStateException.class,()->transition(tenant,id,"ship"));}
 @Test void selectedWarehouseNeverBorrowsOtherStock(){UUID w=UUID.randomUUID();db.update("insert into warehouse values(?,?,?)",tenant,w,"second");db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) values(?,?,1,0,?)",tenant,sku,w);UUID id=order(sku,2);db.update("update sales_order set warehouse_id=? where id=?",w,id);assertThrows(IllegalStateException.class,()->transition(tenant,id,"reserve"));db.update("update order_item set quantity=1 where order_id=?",id);transition(tenant,id,"reserve");transition(tenant,id,"ship");assertEquals(0,db.queryForObject("select on_hand from inventory where tenant_id=? and warehouse_id=?",Integer.class,tenant,w));assertEquals(100,db.queryForObject("select sum(on_hand) from inventory where tenant_id=?",Integer.class,tenant));}
 @Test void multiItemFailureRollsBack(){var id=order(sku,1);UUID other=UUID.randomUUID();db.update("insert into sku values(?,?,?,?,?,?)",other,tenant,other.toString(),"empty",1,sku);db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved) values(?,?,0,0)",tenant,other);db.update("insert into order_item(tenant_id,order_id,sku_id,quantity) values(?,?,?,1)",tenant,id,other);assertThrows(IllegalStateException.class,()->transition(tenant,id,"reserve"));assertEquals(0,db.queryForObject("select sum(reserved) from inventory where tenant_id=?",Integer.class,tenant));assertEquals(0,db.queryForObject("select count(*) from inventory_movement where tenant_id=?",Integer.class,tenant));}
}
