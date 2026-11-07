package cn.haiduo;
import cn.haiduo.search.ProductSearch;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
@RestController @RequestMapping("/api/search") public class SearchController {
 private final ProductSearch search;private final OperationsController scope;private final JdbcTemplate db;
 public SearchController(ProductSearch search,OperationsController scope,JdbcTemplate db){this.search=search;this.scope=scope;this.db=db;}
 @PostMapping("/reindex") Object reindex(Principal p,HttpSession s)throws Exception{UUID tenant=scope.tenant(p,s,"ADMIN");if(!search.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"增强搜索未启用");return Map.of("indexed",search.reindex(tenant));}
 @GetMapping Object find(@RequestParam(defaultValue="") String q,Principal p,HttpSession s){UUID tenant=scope.tenant(p,s);if(q.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);var ids=search.find(tenant,q,1,50);if(ids.isPresent()){var rows=new ArrayList<Map<String,Object>>();for(UUID id:ids.get())rows.addAll(db.queryForList("select id,code,name from sku where tenant_id=? and id=?",tenant,id));return Map.of("engine","OPENSEARCH","items",rows);}return Map.of("engine","DATABASE","items",db.queryForList("select id,code,name from sku where tenant_id=? and (position(lower(?) in lower(name))>0 or position(lower(?) in lower(code))>0) order by code limit 50",tenant,q,q));}
}
