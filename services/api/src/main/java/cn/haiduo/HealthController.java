package cn.haiduo;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController public class HealthController {
 private final JdbcTemplate db;private final RedisConnectionFactory redis;
 public HealthController(JdbcTemplate db,RedisConnectionFactory redis){this.db=db;this.redis=redis;}
 @GetMapping("/healthz") Object health(){try{db.queryForObject("select 1",Integer.class);try(var connection=redis.getConnection()){connection.ping();}return Map.of("status","UP");}catch(Exception e){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}}
}
