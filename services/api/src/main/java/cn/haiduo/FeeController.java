package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.math.BigDecimal;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController public class FeeController {
 private final JdbcTemplate db;private final OperationsController scope;
 public FeeController(JdbcTemplate db,OperationsController scope){this.db=db;this.scope=scope;}
 record Fee(String kind,String amount,String currency,String rate,String source){}
 @PostMapping("/api/orders/{id}/fees") @Transactional Object save(@PathVariable UUID id,@RequestBody Fee f,Principal p,HttpSession s){UUID t=scope.tenant(p,s,"ADMIN","FINANCE");if(f.kind()==null||!Set.of("FREIGHT","PLATFORM").contains(f.kind())||f.amount()==null||!f.amount().matches("[0-9]{1,15}(\\.[0-9]{1,4})?"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);if(db.queryForList("select id from sales_order where tenant_id=? and id=? for update",t,id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(Boolean.TRUE.equals(db.queryForObject("select import_voided from sales_order where tenant_id=? and id=?",Boolean.class,t,id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"CSV批次已撤销，不能修改费用");String currency=(f.currency()==null||f.currency().isBlank())?"CNY":f.currency();String rate=(f.rate()==null||f.rate().isBlank())?"1":f.rate();String source=(f.source()==null||f.source().isBlank())?"手工录入":f.source();if(!currency.matches("[A-Z]{3}")||!rate.matches("[0-9]{1,8}(\\.[0-9]{1,8})?")||new BigDecimal(rate).signum()<=0||source.isBlank()||source.length()>100||currency.equals("CNY")&&new BigDecimal(rate).compareTo(BigDecimal.ONE)!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);Object proposal=BusinessWorkflows.propose(db,t,p.getName(),"FEE_SAVE",id.toString(),f);if(proposal!=null)return proposal;String before=BusinessWorkflows.json(db.queryForList("select kind,original_amount::text,currency,exchange_rate::text,rate_source from order_fee where tenant_id=? and order_id=?",t,id));BigDecimal original=new BigDecimal(f.amount()),exchange=new BigDecimal(rate);db.update("insert into order_fee(tenant_id,order_id,kind,amount_cny,original_amount,currency,exchange_rate,rate_source,effective_at) values(?,?,?,?,?,?,?,?,now()) on conflict(tenant_id,order_id,kind) do update set amount_cny=excluded.amount_cny,original_amount=excluded.original_amount,currency=excluded.currency,exchange_rate=excluded.exchange_rate,rate_source=excluded.rate_source,effective_at=excluded.effective_at",t,id,f.kind(),original.multiply(exchange).setScale(4,java.math.RoundingMode.HALF_UP),original,currency,exchange,source);db.update("insert into business_history values(?,?,?,?,?,?,?, ?,now())",UUID.randomUUID(),t,"FEE_SAVE",id.toString(),before,BusinessWorkflows.json(f),p.getName(),source);new Audit(db).record(t,p.getName(),"FEE_SET_"+f.kind(),id);return Map.of("status","APPLIED");}
}
