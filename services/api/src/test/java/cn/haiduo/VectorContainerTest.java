package cn.haiduo;
import cn.haiduo.ai.VectorIndex;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.postgresql.PostgreSQLContainer;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_SYNC_TESTS",matches="true")
class VectorContainerTest {
 @Test void tenantReplacementCleansOrphansAndRollsBackFailure(){try(var pg=new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.1-pg17").asCompatibleSubstituteFor("postgres"))){
  pg.start();var env=new MockEnvironment().withProperty("VECTOR_DATABASE_URL",pg.getJdbcUrl()).withProperty("VECTOR_DB_USER",pg.getUsername()).withProperty("VECTOR_DB_PASSWORD",pg.getPassword());var index=new VectorIndex(env);
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),old=UUID.randomUUID(),fresh=UUID.randomUUID(),foreign=UUID.randomUUID();
  index.put(a,old,new float[]{1,0});index.put(b,foreign,new float[]{1,0});
  var replacement=List.of(new VectorIndex.Entry(fresh,new float[]{1,0}));
  for(int i=0;i<10;i++)assertEquals(1,index.replaceTenant(a,replacement));
  assertEquals(List.of(fresh),index.find(a,new float[]{1,0}).orElseThrow());
  assertEquals(List.of(foreign),index.find(b,new float[]{1,0}).orElseThrow());
  // Inject a DB failure after DELETE and first INSERT; the original index must survive.
  UUID rejected=UUID.randomUUID();
  var sql=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()));
  sql.execute("alter table haiduo_vectors add constraint reject_test_chunk check(chunk_id <> '"+rejected+"'::uuid)");
  var accepted=new VectorIndex.Entry(UUID.randomUUID(),new float[]{1,0});
  var denied=new VectorIndex.Entry(rejected,new float[]{1,0});
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->index.replaceTenant(a,List.of(accepted,denied)));
  assertEquals(List.of(fresh),index.find(a,new float[]{1,0}).orElseThrow());
  assertEquals(0,index.replaceTenant(a,List.of()));
  assertTrue(index.find(a,new float[]{1,0}).orElseThrow().isEmpty());
  assertEquals(List.of(foreign),index.find(b,new float[]{1,0}).orElseThrow());
 }}
 @Test void pgvectorFiltersTenant(){try(var pg=new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.1-pg17").asCompatibleSubstituteFor("postgres"))){pg.start();var env=new MockEnvironment().withProperty("VECTOR_DATABASE_URL",pg.getJdbcUrl()).withProperty("VECTOR_DB_USER",pg.getUsername()).withProperty("VECTOR_DB_PASSWORD",pg.getPassword());var index=new VectorIndex(env);UUID a=UUID.randomUUID(),b=UUID.randomUUID(),x=UUID.randomUUID(),y=UUID.randomUUID();index.put(a,x,new float[]{1,0});index.put(b,y,new float[]{1,0});assertEquals(List.of(x),index.find(a,new float[]{1,0}).orElseThrow());assertEquals(List.of(y),index.find(b,new float[]{1,0}).orElseThrow());}}
}
