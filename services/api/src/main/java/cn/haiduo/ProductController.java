package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/products") public class ProductController {
 private final JdbcTemplate db; private final SessionController sessions;
 public ProductController(JdbcTemplate db,SessionController sessions){this.db=db;this.sessions=sessions;}
 @GetMapping List<Map<String,Object>> list(Principal user,HttpSession session,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="200") int size,jakarta.servlet.http.HttpServletResponse response){
  UUID tenant=sessions.activeTenant(user,session);
  if(search.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"搜索内容过长");
  if(page<1||page>1000000||size<1||size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");
  response.setHeader("X-Total-Count",String.valueOf(db.queryForObject("select count(*) from sku where tenant_id=? and (position(lower(?) in lower(name))>0 or position(lower(?) in lower(code))>0)",Long.class,tenant,search,search)));
  return db.query("select id,code,name,cost,(select image_file_id from product p where p.tenant_id=sku.tenant_id and p.id=sku.product_id) as image_file_id,product_id from sku where tenant_id=? and (position(lower(?) in lower(name))>0 or position(lower(?) in lower(code))>0) order by code,id limit ? offset ?",(rs,n)->Map.<String,Object>of("id",rs.getObject(1),"productId",rs.getObject(6),"code",rs.getString(2),"name",rs.getString(3),"cost",rs.getBigDecimal(4).toPlainString(),"imageUrl",rs.getObject(5)==null?"":"/api/files/"+rs.getObject(5)),tenant,search,search,size,(page-1)*size);
 }
 record CreateProduct(String code,String name,String cost){}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,UUID> create(@RequestBody CreateProduct input,Principal user,HttpSession session){
  UUID tenant=sessions.activeTenant(user,session);
  if(!Set.of("ADMIN","OPERATIONS").contains(sessions.verifiedRole(user,tenant)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅管理员和运营可创建商品");
  if(input.code()==null||!input.code().matches("[A-Za-z0-9_-]{1,64}")||input.name()==null||input.name().isBlank()||input.name().length()>200||input.cost()==null||!input.cost().matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"SKU、名称或成本格式无效");
  BigDecimal cost=new BigDecimal(input.cost());UUID id=UUID.randomUUID();
  db.update("insert into product(id,tenant_id,name) values(?,?,?)",id,tenant,input.name().trim());int added=db.update("insert into sku(id,tenant_id,code,name,cost,product_id) values(?,?,?,?,?,?) on conflict(tenant_id,code) do nothing",id,tenant,input.code(),input.name().trim(),cost,id);
  if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"该企业的 SKU 已存在");
  db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) select tenant_id,?,0,0,id from warehouse where tenant_id=?",id,tenant);
  new Audit(db).record(tenant,user.getName(),"PRODUCT_CREATE",id);return Map.of("id",id);
 }
 @PostMapping("/{productId}/skus") @ResponseStatus(HttpStatus.CREATED) @Transactional Map<String,UUID> addSku(@PathVariable UUID productId,@RequestBody CreateProduct input,Principal user,HttpSession session){UUID tenant=sessions.activeTenant(user,session);if(!Role.valueOf(sessions.verifiedRole(user,tenant)).allows(Role.Permission.PRODUCT_WRITE))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(db.queryForObject("select count(*) from product where tenant_id=? and id=?",Integer.class,tenant,productId)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(input.code()==null||!input.code().matches("[A-Za-z0-9_-]{1,64}")||input.name()==null||input.name().isBlank()||input.name().length()>200||input.cost()==null||!input.cost().matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);UUID id=UUID.randomUUID();int added=db.update("insert into sku(id,tenant_id,code,name,cost,product_id) values(?,?,?,?,?,?) on conflict(tenant_id,code) do nothing",id,tenant,input.code(),input.name().trim(),new BigDecimal(input.cost()),productId);if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"SKU已存在");db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) select tenant_id,?,0,0,id from warehouse where tenant_id=?",id,tenant);new Audit(db).record(tenant,user.getName(),"SKU_CREATE",id);return Map.of("id",id,"productId",productId);}
}