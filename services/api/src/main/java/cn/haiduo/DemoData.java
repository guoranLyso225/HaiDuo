package cn.haiduo;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
@Component @Profile("demo") public class DemoData implements ApplicationRunner {
 private final JdbcTemplate db; private final PasswordEncoder passwords;
 public DemoData(JdbcTemplate db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;}
 @Transactional public void run(ApplicationArguments args){
  String password=System.getenv("DEMO_PASSWORD");if(password==null||password.length()<12)throw new IllegalStateException("demo 环境要求 DEMO_PASSWORD 至少12字符");
  for(int company=1;company<=2;company++){
   UUID tenant=UUID.nameUUIDFromBytes(("haiduo-demo-"+company).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   db.update("insert into tenant values(?,?) on conflict do nothing",tenant,"演示企业"+company);
   db.update("insert into warehouse values(?,?::uuid,?) on conflict do nothing",tenant,"00000000-0000-0000-0000-000000000001","默认仓库");UUID store=UUID.nameUUIDFromBytes(("haiduo-store-"+company).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   db.update("insert into store values(?,?,?,'MOCK','MOCK') on conflict do nothing",store,tenant,"mock");
   UUID product=UUID.nameUUIDFromBytes(("haiduo-product-"+company).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   db.update("insert into product(id,tenant_id,name) values(?,?,?) on conflict do nothing",product,tenant,"演示旅行收纳包");
   db.update("insert into sku(id,tenant_id,code,name,cost,product_id) values(?,?,'DEMO-BAG',?,200,?) on conflict do nothing",product,tenant,"演示旅行收纳包",product);
   db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved) values(?,?,100,0) on conflict do nothing",tenant,product);
   UUID order=UUID.nameUUIDFromBytes(("haiduo-order-"+company).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   int added=db.update("insert into sales_order(id,tenant_id,store,external_id,net,rate,currency,rate_source,rate_at) values(?,?,'mock','DEMO-001',100,7.2,'USD','DEMO固定汇率',?) on conflict do nothing",order,tenant,java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
   if(added==1){
    db.update("insert into order_item(tenant_id,order_id,sku_id,quantity,unit_cost_cny) values(?,?,?,1,200)",tenant,order,product);
    db.update("insert into order_fee(tenant_id,order_id,kind,amount_cny,original_amount,currency,exchange_rate,rate_source,effective_at) values(?,?,'FREIGHT',50,50,'CNY',1,'DEMO',now()),(?,?,'PLATFORM',72,72,'CNY',1,'DEMO',now())",tenant,order,tenant,order);
   }
   for(String role:new String[]{"ADMIN","OPERATIONS","WAREHOUSE","FINANCE"}){
    String username="demo"+company+"."+role.toLowerCase(java.util.Locale.ROOT);
    db.update("insert into app_user(username,password_hash) values(?,?) on conflict do nothing",username,passwords.encode(password));
    db.update("insert into membership values(?,?,?) on conflict do nothing",username,tenant,role);
   }
  }
 }
}
