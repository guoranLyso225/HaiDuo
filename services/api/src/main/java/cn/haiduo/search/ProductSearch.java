package cn.haiduo.search;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
@Service public class ProductSearch {
 private final JdbcTemplate db;private final String endpoint;private final ObjectMapper json=new ObjectMapper();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public ProductSearch(JdbcTemplate db,Environment env){this.db=db;endpoint=env.getProperty("SEARCH_URL","");}
 public boolean enabled(){return !endpoint.isBlank();}
 private String index(UUID tenant){return "/haiduo-products-"+tenant;}
 private JsonNode request(String method,String path,Object body)throws Exception{var b=HttpRequest.newBuilder(URI.create(endpoint+path)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json");var r=http.send(b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("Search unavailable");return json.readTree(r.body());}
 public void indexProduct(UUID tenant,String id)throws Exception{if(!enabled())return;UUID sku=UUID.fromString(id);var rows=db.queryForList("select id,code,name from sku where tenant_id=? and id=?",tenant,sku);if(rows.isEmpty())return;var row=rows.getFirst();request("PUT",index(tenant)+"/_doc/"+sku+"?refresh=wait_for",Map.of("code",row.get("code"),"name",row.get("name")));}
 public int reindex(UUID tenant)throws Exception{var rows=db.queryForList("select id,code,name from sku where tenant_id=? order by id limit 5001",tenant);if(rows.size()>5000)throw new IllegalStateException("Reindex limit 5000; use batch maintenance");for(var row:rows)request("PUT",index(tenant)+"/_doc/"+row.get("id"),Map.of("code",row.get("code"),"name",row.get("name")));request("POST",index(tenant)+"/_refresh",Map.of());return rows.size();}
 public Optional<List<UUID>> find(UUID tenant,String query,int page,int size){if(!enabled()||query.isBlank())return Optional.empty();try{var root=request("POST",index(tenant)+"/_search",Map.of("from",(page-1)*size,"size",size,"query",Map.of("multi_match",Map.of("query",query,"fields",List.of("name","code")))));var ids=new ArrayList<UUID>();for(var hit:root.path("hits").path("hits"))ids.add(UUID.fromString(hit.path("_id").asText()));return Optional.of(ids);}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return Optional.empty();}}
}
