package cn.haiduo.orders;

import cn.haiduo.audit.Audit;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CsvOrders {
 private final JdbcTemplate db;
 public CsvOrders(JdbcTemplate db){this.db=db;}
 record Line(String store,String external,UUID sku,String code,int quantity,BigDecimal net,String currency,BigDecimal rate,BigDecimal cost,long version){}
 record Parsed(String text,Map<String,List<Line>> groups,List<Map<String,Object>> errors){}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private String normalized(String csv){if(csv==null||csv.length()>100000)throw bad("CSV最多100000字符");String text=csv.strip();return text.startsWith("\uFEFF")?text.substring(1):text;}
 private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Parsed parse(UUID tenant,UUID warehouse,String csv){
  String text=normalized(csv);String[] lines=text.split("\\R");List<Map<String,Object>> errors=new ArrayList<>();Map<String,List<Line>> groups=new LinkedHashMap<>();
  if(db.queryForObject("select count(*) from warehouse where tenant_id=? and id=?",Integer.class,tenant,warehouse)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"仓库不存在");
  if(lines.length<2||lines.length>501||!lines[0].equals("store,order_no,sku,quantity,net,currency,rate")){errors.add(Map.of("line",1,"message","CSV模板无效，最多500行数据"));return new Parsed(text,groups,errors);}
  for(int n=1;n<lines.length;n++)try{
   String[] c=lines[n].split(",",-1);
   if(lines[n].contains("\"")||c.length!=7||Arrays.stream(c).anyMatch(String::isBlank)||c[0].length()>100||c[1].length()>100||c[2].length()>64||!c[3].matches("[1-9][0-9]{0,5}")||!c[4].matches("[0-9]{1,12}(\\.[0-9]{1,4})?")||!c[5].matches("[A-Z]{3}")||!c[6].matches("[0-9]{1,8}(\\.[0-9]{1,8})?")||new BigDecimal(c[6]).signum()<=0)throw bad("字段格式无效，不支持带引号字段或字段内逗号");
   if(c[5].equals("CNY")&&new BigDecimal(c[6]).compareTo(BigDecimal.ONE)!=0)throw bad("CNY汇率必须为1");
   var skus=db.queryForList("select k.id,k.cost,k.version from sku k where k.tenant_id=? and (k.code=? or exists(select 1 from sku_alias a where a.tenant_id=k.tenant_id and a.sku_id=k.id and a.code=?))",tenant,c[2],c[2]);if(skus.isEmpty())throw bad("SKU不存在或不属当前企业");
   if(db.queryForObject("select count(*) from store where tenant_id=? and name=?",Integer.class,tenant,c[0])!=1)throw bad("店铺不存在或不属当前企业");var sku=skus.getFirst();if(db.queryForObject("select count(*) from product_control pc join sku sk on sk.product_id=pc.product_id and sk.tenant_id=pc.tenant_id where sk.tenant_id=? and sk.id=? and pc.archived",Integer.class,tenant,sku.get("id"))>0)throw bad("商品已归档，请先恢复");
   Line line=new Line(c[0],c[1],(UUID)sku.get("id"),c[2],Integer.parseInt(c[3]),new BigDecimal(c[4]),c[5],new BigDecimal(c[6]),(BigDecimal)sku.get("cost"),((Number)sku.get("version")).longValue());
   var group=groups.computeIfAbsent(c[0]+"\u0000"+c[1],k->new ArrayList<>());if(!group.isEmpty()){var first=group.getFirst();if(first.net().compareTo(line.net())!=0||!first.currency().equals(line.currency())||first.rate().compareTo(line.rate())!=0)throw bad("同单各行必须重复相同整单净收入、币种及汇率，金额不累加");if(group.stream().anyMatch(v->v.sku().equals(line.sku())))throw bad("同单SKU重复，请先合并数量");}group.add(line);
  }catch(ResponseStatusException failure){errors.add(Map.of("line",n+1,"message",Objects.toString(failure.getReason(),"格式无效")));}
  return new Parsed(text,groups,errors);
 }
 private List<Map<String,Object>> existing(UUID tenant,Line first){return db.queryForList("select id,status,import_voided from sales_order where tenant_id=? and store=? and external_id=?",tenant,first.store(),first.external());}
 private Map<String,Object> preview(UUID tenant,UUID warehouse,Parsed parsed){
  List<Map<String,Object>> orders=new ArrayList<>();StringBuilder fingerprint=new StringBuilder(tenant+"\n"+warehouse+"\n"+parsed.text());int added=0,duplicates=0;
  for(var group:parsed.groups().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList()){
   Line first=group.getFirst();var found=existing(tenant,first);boolean duplicate=!found.isEmpty();if(duplicate)duplicates++;else added++;
   List<Map<String,Object>> items=new ArrayList<>();for(var item:group){items.add(Map.of("sku",item.code(),"quantity",item.quantity(),"unitCostCny",item.cost().toPlainString()));fingerprint.append('\n').append(item.sku()).append(':').append(item.version()).append(':').append(item.cost().toPlainString());}
   String status=duplicate?(String)found.getFirst().get("status"):"";boolean voided=duplicate&&Boolean.TRUE.equals(found.getFirst().get("import_voided"));fingerprint.append('\n').append(first.store()).append(':').append(first.external()).append(':').append(duplicate?found.getFirst().get("id"):"NEW").append(':').append(status).append(':').append(voided);
   orders.add(Map.of("store",first.store(),"orderNo",first.external(),"action",duplicate?"DUPLICATE":"NEW","net",first.net().toPlainString(),"currency",first.currency(),"rate",first.rate().toPlainString(),"items",items,"existingStatus",status,"voided",voided));
  }
  return Map.of("orders",orders,"errors",parsed.errors(),"imported",added,"duplicates",duplicates,"digest",hash(fingerprint.toString()),"canImport",parsed.errors().isEmpty());
 }
 public Object preview(UUID tenant,UUID warehouse,String csv){return preview(tenant,warehouse,parse(tenant,warehouse,csv));}
 public Object importOrders(UUID tenant,UUID warehouse,String csv,String actor){return importOrders(tenant,warehouse,csv,actor,UUID.randomUUID(),null);}
 public Object importOrders(UUID tenant,UUID warehouse,String csv,String actor,UUID requestKey,String digest){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("CSV导入必须在业务事务中执行");if(digest!=null&&!digest.matches("[a-f0-9]{64}"))throw bad("预览摘要格式无效，请重新预览");if(requestKey==null)requestKey=UUID.randomUUID();String contentHash=hash(tenant+"\n"+warehouse+"\n"+normalized(csv));
  db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,tenant+":"+requestKey);
  var prior=db.queryForList("select id,actor,content_hash,imported,duplicates,status from csv_import_batch where tenant_id=? and request_key=?",tenant,requestKey);
  if(!prior.isEmpty()){var saved=prior.getFirst();if(!saved.get("actor").equals(actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请求键不属于当前员工");if(!saved.get("content_hash").equals(contentHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"同一请求键不能用于不同导入内容");return result(saved.get("id"),saved.get("imported"),saved.get("duplicates"),saved.get("status"));}
  Parsed parsed=parse(tenant,warehouse,csv);if(!parsed.errors().isEmpty())throw bad("第"+parsed.errors().getFirst().get("line")+"行："+parsed.errors().getFirst().get("message"));
  for(UUID sku:parsed.groups().values().stream().flatMap(List::stream).map(Line::sku).distinct().sorted().toList())db.queryForObject("select id from sku where tenant_id=? and id=? for share",UUID.class,tenant,sku);
  parsed=parse(tenant,warehouse,csv);if(!parsed.errors().isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"资料已变化，请重新预览");
  Map<String,Object> snapshot=preview(tenant,warehouse,parsed);if(digest!=null&&!digest.equals(snapshot.get("digest")))throw new ResponseStatusException(HttpStatus.CONFLICT,"预览后数据已变化，请重新预览再确认");
  UUID batch=UUID.randomUUID();db.update("insert into csv_import_batch(id,tenant_id,warehouse_id,actor,request_key,content_hash) values(?,?,?,?,?,?)",batch,tenant,warehouse,actor,requestKey,contentHash);
  int imported=0,duplicates=0;
  for(var group:parsed.groups().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList()){
   Line first=group.getFirst();UUID id=UUID.randomUUID();int added=db.update("insert into sales_order(id,tenant_id,store,external_id,net,rate,currency,rate_source,rate_at,warehouse_id) values(?,?,?,?,?,?,?,'CSV手工快照',now(),?) on conflict(tenant_id,store,external_id) do nothing",id,tenant,first.store(),first.external(),first.net(),first.rate(),first.currency(),warehouse);
   if(added==0){duplicates++;continue;}
   for(Line line:group)db.update("insert into order_item(tenant_id,order_id,sku_id,quantity,unit_cost_cny) values(?,?,?,?,?)",tenant,id,line.sku(),line.quantity(),line.cost());
   db.update("insert into csv_import_order values(?,?,?)",tenant,batch,id);new Audit(db).record(tenant,actor,"ORDER_IMPORT",id);imported++;
  }
  if(digest!=null&&(imported!=((Number)snapshot.get("imported")).intValue()||duplicates!=((Number)snapshot.get("duplicates")).intValue()))throw new ResponseStatusException(HttpStatus.CONFLICT,"确认期间订单发生变化，请重新预览");
  db.update("update csv_import_batch set imported=?,duplicates=? where tenant_id=? and id=?",imported,duplicates,tenant,batch);if(imported>0)new Audit(db).record(tenant,actor,"CSV_BATCH_IMPORT",batch);return result(batch,imported,duplicates,"ACTIVE");
 }
 private Object result(Object batch,Object imported,Object duplicates,Object status){return Map.of("batchId",batch,"imported",imported,"duplicates",duplicates,"status",status);}
}
