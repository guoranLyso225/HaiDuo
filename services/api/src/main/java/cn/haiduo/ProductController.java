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
  String filter="k.tenant_id=? and (position(lower(?) in lower(k.name))>0 or position(lower(?) in lower(k.code))>0 or exists(select 1 from sku_alias a where a.tenant_id=k.tenant_id and a.sku_id=k.id and position(lower(?) in lower(a.code))>0))";
  response.setHeader("X-Total-Count",String.valueOf(db.queryForObject("select count(*) from sku k where "+filter,Long.class,tenant,search,search,search)));
  return db.query("select k.id,k.code,k.name,k.cost,p.image_file_id,k.product_id,p.name,coalesce(c.archived,false) as archived from sku k join product p on p.tenant_id=k.tenant_id and p.id=k.product_id left join product_control c on c.tenant_id=p.tenant_id and c.product_id=p.id where "+filter+" order by k.code,k.id limit ? offset ?",(rs,n)->Map.<String,Object>of("id",rs.getObject(1),"productId",rs.getObject(6),"code",rs.getString(2),"name",rs.getString(3),"cost",rs.getBigDecimal(4).toPlainString(),"imageUrl",rs.getObject(5)==null?"":"/api/files/"+rs.getObject(5),"productName",rs.getString(7),"archived",rs.getBoolean(8)),tenant,search,search,search,size,(page-1)*size);
 }
 record CreateProduct(String code,String name,String cost){}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional Object create(@RequestBody CreateProduct input,Principal user,HttpSession session){
  UUID tenant=sessions.activeTenant(user,session);
  if(!Set.of("ADMIN","OPERATIONS").contains(sessions.verifiedRole(user,tenant)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅管理员和运营可创建商品");
  if(input.code()==null||!input.code().matches("[A-Za-z0-9_-]{1,64}")||input.name()==null||input.name().isBlank()||input.name().length()>200||input.cost()==null||!input.cost().matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"SKU、名称或成本格式无效");
  Object proposal=BusinessWorkflows.propose(db,tenant,user.getName(),"PRODUCT_CREATE","",input);if(proposal!=null)return proposal;BusinessWorkflows.lock(db,tenant);BigDecimal cost=new BigDecimal(input.cost());UUID id=UUID.randomUUID();if(db.queryForObject("select count(*) from sku_alias where tenant_id=? and code=?",Integer.class,tenant,input.code())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"SKU或历史别名已存在");
  db.update("insert into product(id,tenant_id,name) values(?,?,?)",id,tenant,input.name().trim());int added=db.update("insert into sku(id,tenant_id,code,name,cost,product_id) values(?,?,?,?,?,?) on conflict(tenant_id,code) do nothing",id,tenant,input.code(),input.name().trim(),cost,id);
  if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"该企业的 SKU 已存在");db.update("insert into sku_alias values(?,?,?)",tenant,input.code(),id);
  db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) select tenant_id,?,0,0,id from warehouse where tenant_id=?",id,tenant);
  new Audit(db).record(tenant,user.getName(),"PRODUCT_CREATE",id);return Map.of("id",id);
 }
 @PostMapping("/{productId}/skus") @ResponseStatus(HttpStatus.CREATED) @Transactional Object addSku(@PathVariable UUID productId,@RequestBody CreateProduct input,Principal user,HttpSession session){UUID tenant=sessions.activeTenant(user,session);if(!Role.valueOf(sessions.verifiedRole(user,tenant)).allows(Role.Permission.PRODUCT_WRITE))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(db.queryForObject("select count(*) from product_control where tenant_id=? and product_id=? and archived",Integer.class,tenant,productId)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"商品已归档");if(db.queryForObject("select count(*) from product where tenant_id=? and id=?",Integer.class,tenant,productId)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(input.code()==null||!input.code().matches("[A-Za-z0-9_-]{1,64}")||input.name()==null||input.name().isBlank()||input.name().length()>200||input.cost()==null||!input.cost().matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);Object proposal=BusinessWorkflows.propose(db,tenant,user.getName(),"SKU_CREATE",productId.toString(),input);if(proposal!=null)return proposal;BusinessWorkflows.lock(db,tenant);if(db.queryForObject("select count(*) from sku_alias where tenant_id=? and code=?",Integer.class,tenant,input.code())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"SKU或历史别名已存在");UUID id=UUID.randomUUID();int added=db.update("insert into sku(id,tenant_id,code,name,cost,product_id) values(?,?,?,?,?,?) on conflict(tenant_id,code) do nothing",id,tenant,input.code(),input.name().trim(),new BigDecimal(input.cost()),productId);if(added==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"SKU已存在");db.update("insert into sku_alias values(?,?,?)",tenant,input.code(),id);db.update("insert into inventory(tenant_id,sku_id,on_hand,reserved,warehouse_id) select tenant_id,?,0,0,id from warehouse where tenant_id=?",id,tenant);new Audit(db).record(tenant,user.getName(),"SKU_CREATE",id);return Map.of("id",id,"productId",productId);}
}