package cn.haiduo.ai;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
@Service public class VectorIndex {
 private final JdbcTemplate db;
 public VectorIndex(Environment env){String url=env.getProperty("VECTOR_DATABASE_URL","");db=url.isBlank()?null:new JdbcTemplate(new DriverManagerDataSource(url,env.getRequiredProperty("VECTOR_DB_USER"),env.getRequiredProperty("VECTOR_DB_PASSWORD")));if(db!=null){db.execute("CREATE EXTENSION IF NOT EXISTS vector");db.execute("CREATE TABLE IF NOT EXISTS haiduo_vectors(tenant_id uuid NOT NULL,chunk_id uuid NOT NULL,embedding vector NOT NULL,PRIMARY KEY(tenant_id,chunk_id))");}}
 private String vector(float[] values){if(values.length<1||values.length>4096)throw new IllegalArgumentException("Invalid embedding dimensions");var s=new StringJoiner(",","[","]");for(float v:values){if(!Float.isFinite(v))throw new IllegalArgumentException("Invalid embedding");s.add(Float.toString(v));}return s.toString();}
 public boolean enabled(){return db!=null;}
 public record Entry(UUID chunkId,float[] embedding){}
 public int replaceTenant(UUID tenant,List<Entry> entries){
  if(db==null)throw new IllegalStateException("Vector index disabled");
  var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));
  return Objects.requireNonNull(tx.execute(status->{
   db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",tenant.toString());
   db.update("delete from haiduo_vectors where tenant_id=?",tenant);
   for(var entry:entries)put(tenant,entry.chunkId(),entry.embedding());
   return entries.size();
  }));
 }
 public void put(UUID tenant,UUID chunk,float[] embedding){if(db!=null)db.update("insert into haiduo_vectors values(?,?,?::vector) on conflict(tenant_id,chunk_id) do update set embedding=excluded.embedding",tenant,chunk,vector(embedding));}
 public Optional<List<UUID>> find(UUID tenant,float[] embedding){if(db==null)return Optional.empty();try{return Optional.of(db.query("select chunk_id from haiduo_vectors where tenant_id=? and vector_dims(embedding)=? order by embedding <=> ?::vector limit 20",(rs,n)->rs.getObject(1,UUID.class),tenant,embedding.length,vector(embedding)));}catch(Exception unavailable){return Optional.empty();}}
}
