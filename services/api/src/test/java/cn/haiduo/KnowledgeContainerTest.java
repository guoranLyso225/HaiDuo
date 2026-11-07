package cn.haiduo;
import cn.haiduo.ai.AiModels;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_SYNC_TESTS",matches="true")
class KnowledgeContainerTest {
 @Test void importCitationsAndTenantIsolation(){try(var pg=new PostgreSQLContainer("postgres:17.6")){pg.start();var ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());Flyway.configure().dataSource(ds).load().migrate();var db=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));UUID a=UUID.randomUUID(),b=UUID.randomUUID();db.update("insert into tenant values(?,'a'),(?,'b')",a,b);var scope=mock(OperationsController.class);var session=new MockHttpSession();java.security.Principal user=()->"test";when(scope.tenant(eq(user),eq(session),any(String[].class))).thenReturn(a);ObjectProvider<org.springframework.ai.chat.model.ChatModel> chat=mock(ObjectProvider.class);ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embed=mock(ObjectProvider.class);var models=new AiModels(chat,embed,new MockEnvironment().withProperty("AI_DEMO","true"));var api=new AiController(db,scope,models);tx.executeWithoutResult(s->api.importText(new AiController.Import("仓库政策","库存不足时禁止预占，取消订单释放预占。"),user,session));Object answer=tx.execute(s->api.ask(new AiController.Question("库存不足"),user,session));assertTrue(answer.toString().contains("仓库政策"));when(scope.tenant(eq(user),eq(session),any(String[].class))).thenReturn(b);Object foreign=tx.execute(s->api.ask(new AiController.Question("库存不足"),user,session));assertFalse(foreign.toString().contains("仓库政策"));assertTrue(foreign.toString().contains("资料不足"));assertEquals(1,db.queryForObject("select count(*) from knowledge_document",Integer.class));db.update("update ai_usage set calls=99 where tenant_id=?",b);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.execute(s->api.ask(new AiController.Question("库存不足"),user,session)));assertEquals(99,db.queryForObject("select calls from ai_usage where tenant_id=?",Integer.class,b));db.update("update ai_usage set calls=98 where tenant_id=?",b);tx.execute(s->api.ask(new AiController.Question("库存不足"),user,session));assertEquals(100,db.queryForObject("select calls from ai_usage where tenant_id=?",Integer.class,b));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.execute(s->api.ask(new AiController.Question("库存不足"),user,session)));}}
}
