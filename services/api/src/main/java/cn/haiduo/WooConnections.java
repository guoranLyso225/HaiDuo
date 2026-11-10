package cn.haiduo;
import cn.haiduo.platform.*;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.net.URI;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service public class WooConnections {
 private final JdbcTemplate db;private final Environment env;
 public WooConnections(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
 String allowed(){String url=env.getProperty("WOO_ALLOWED_BASE_URL","");if(url.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"WooCommerce未启用：需管理员配置可信店铺地址");return url;}
 void validate(String url){if(!Objects.equals(url,allowed()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"地址不在服务端允许名单");URI uri=URI.create(url);if(uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!("https".equals(uri.getScheme())||Set.of("localhost","127.0.0.1","host.docker.internal","haiduo-woo-shop-1").contains(uri.getHost())&&"http".equals(uri.getScheme())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅允许HTTPS或本机开发HTTP地址");}
 WooCommerceConnector connector(UUID tenant,UUID store){var row=db.queryForMap("select base_url,key_cipher,secret_cipher from woo_connection where tenant_id=? and store_id=?",tenant,store);String base=(String)row.get("base_url");validate(base);String key=env.getRequiredProperty("TOKEN_ENCRYPTION_KEY");return new WooCommerceConnector(base,PlatformCrypto.decrypt(key,(String)row.get("key_cipher")),PlatformCrypto.decrypt(key,(String)row.get("secret_cipher")),env.getProperty("WOO_SIGNING_BASE_URL",base));}
 @RestController static class Controller {
  private final WooConnections woo;private final OperationsController scope;
  Controller(WooConnections woo,OperationsController scope){this.woo=woo;this.scope=scope;}
  record Connect(String baseUrl,String consumerKey,String consumerSecret,String webhookSecret){@Override public String toString(){return "WooConnect[credentials redacted]";}}
  @GetMapping("/api/woocommerce/status") Object status(Principal p,HttpSession s){scope.tenant(p,s,"ADMIN");return Map.of("baseUrl",woo.env.getProperty("WOO_ALLOWED_BASE_URL",""));}
  @PostMapping("/api/woocommerce/{id}/connect") @Transactional void connect(@PathVariable UUID id,@RequestBody Connect input,Principal p,HttpSession s)throws Exception{
   UUID tenant=scope.tenant(p,s,"ADMIN");if(woo.db.queryForObject("select count(*) from store where tenant_id=? and id=? and platform='WOOCOMMERCE'",Integer.class,tenant,id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
   woo.validate(input.baseUrl());if(input.consumerKey()==null||!input.consumerKey().matches("ck_[a-f0-9]{40}")||input.consumerSecret()==null||!input.consumerSecret().matches("cs_[a-f0-9]{40}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效的WooCommerce只读密钥");
   String encryption=woo.env.getProperty("TOKEN_ENCRYPTION_KEY","");if(encryption.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"尚未配置令牌加密密钥");
   var connector=new WooCommerceConnector(input.baseUrl(),input.consumerKey(),input.consumerSecret(),woo.env.getProperty("WOO_SIGNING_BASE_URL",input.baseUrl()));try{connector.orders();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"WooCommerce订单读取失败，请检查地址和只读密钥");}
   if(input.webhookSecret()!=null&&!input.webhookSecret().matches("[a-f0-9]{64}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Webhook密钥需为64位十六进制随机值");
   woo.db.update("insert into woo_connection(tenant_id,store_id,base_url,key_cipher,secret_cipher,webhook_cipher) values(?,?,?,?,?,?) on conflict(tenant_id,store_id) do update set base_url=excluded.base_url,key_cipher=excluded.key_cipher,secret_cipher=excluded.secret_cipher,webhook_cipher=coalesce(excluded.webhook_cipher,woo_connection.webhook_cipher)",tenant,id,input.baseUrl(),PlatformCrypto.encrypt(encryption,input.consumerKey()),PlatformCrypto.encrypt(encryption,input.consumerSecret()),input.webhookSecret()==null?null:PlatformCrypto.encrypt(encryption,input.webhookSecret()));
   woo.db.update("update store set connection_status='CONNECTED' where tenant_id=? and id=?",tenant,id);new Audit(woo.db).record(tenant,p.getName(),"WOO_CONNECTED",id);
  }
 }
}
