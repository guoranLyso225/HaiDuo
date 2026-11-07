package cn.haiduo;
import cn.haiduo.audit.Audit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_MESSAGING_TESTS",matches="true")
class MessagingContainerTest {
 static PostgreSQLContainer pg;static RabbitMQContainer broker;static CachingConnectionFactory connection;static JdbcTemplate db;static TransactionTemplate tx;static RabbitTemplate rabbit;static EventDelivery delivery;static SimpleMessageListenerContainer listener;
 UUID tenant;
 @BeforeAll static void start(){pg=new PostgreSQLContainer("postgres:17.6").withDatabaseName("haiduo").withUsername("haiduo").withPassword("test-password");broker=new RabbitMQContainer("rabbitmq:4.2.1-management");pg.start();broker.start();var ds=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());Flyway.configure().dataSource(ds).load().migrate();db=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);connection=new CachingConnectionFactory(broker.getHost(),broker.getAmqpPort());connection.setUsername(broker.getAdminUsername());connection.setPassword(broker.getAdminPassword());connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);connection.setPublisherReturns(true);rabbit=new RabbitTemplate(connection);rabbit.setMandatory(true);delivery=new EventDelivery(db,rabbit,manager);var admin=new RabbitAdmin(connection);admin.declareQueue(delivery.deadQueue());admin.declareQueue(delivery.eventQueue());listener=new SimpleMessageListenerContainer(connection);listener.setQueueNames("haiduo.events");listener.setDefaultRequeueRejected(false);listener.setMessageListener((MessageListener)m->delivery.consume(new String(m.getBody(),StandardCharsets.UTF_8)));listener.start();}
 @AfterAll static void close(){if(listener!=null)listener.stop();if(connection!=null)connection.destroy();if(broker!=null)broker.stop();if(pg!=null)pg.stop();}
 @BeforeEach void tenant(){tenant=UUID.randomUUID();db.update("insert into tenant values(?,?)",tenant,"messaging-test");}
 @AfterEach void retirePending(){db.update("update outbox_event set status='DEAD' where tenant_id=? and status='PENDING'",tenant);}
 UUID event(){tx.executeWithoutResult(s->new Audit(db).record(tenant,"test","TEST_ACTIVITY",UUID.randomUUID()));return db.queryForObject("select id from outbox_event where tenant_id=?",UUID.class,tenant);}
 void awaitProcessed(UUID id)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(System.nanoTime()<end){if(db.queryForObject("select count(*) from processed_event where event_id=?",Integer.class,id)==1)return;Thread.sleep(100);}fail("Consumer did not commit within 10s");}
 @Test void businessAndOutboxRollbackTogether(){assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{new Audit(db).record(tenant,"test","TEST",tenant);throw new IllegalStateException("rollback");}));assertEquals(0,db.queryForObject("select count(*) from audit_log where tenant_id=?",Integer.class,tenant));assertEquals(0,db.queryForObject("select count(*) from outbox_event where tenant_id=?",Integer.class,tenant));}
 @Test void confirmedDeliveryAndTenDuplicates()throws Exception{UUID id=event();delivery.publish();awaitProcessed(id);for(int i=0;i<10;i++)rabbit.convertAndSend("","haiduo.events",id.toString());Thread.sleep(500);assertEquals(1,db.queryForObject("select count(*) from sync_job where event_id=?",Integer.class,id));assertEquals("SENT",db.queryForObject("select status from outbox_event where id=?",String.class,id));}
 @Test void confirmedButDatabaseStatusLostCanRecover()throws Exception{UUID id=event();delivery.publish();awaitProcessed(id);db.update("update outbox_event set status='PENDING',available_at=now() where id=?",id);delivery.publish();Thread.sleep(500);assertEquals(1,db.queryForObject("select count(*) from sync_job where event_id=?",Integer.class,id));}
 @Test void consumerFailureRollsBackIdempotency(){UUID id=event();db.execute("alter table sync_job add constraint injected_failure check(status<>'COMPLETED')");try{assertThrows(RuntimeException.class,()->delivery.consume(id.toString()));assertEquals(0,db.queryForObject("select count(*) from processed_event where event_id=?",Integer.class,id));}finally{db.execute("alter table sync_job drop constraint injected_failure");}delivery.consume(id.toString());assertEquals(1,db.queryForObject("select count(*) from sync_job where event_id=?",Integer.class,id));}
 @Test void unroutablePublishRetriesFiveThenDead(){UUID id=event();listener.stop();var admin=new RabbitAdmin(connection);admin.deleteQueue("haiduo.events");try{for(int i=0;i<5;i++){db.update("update outbox_event set available_at=now() where id=?",id);delivery.publish();}assertEquals("DEAD",db.queryForObject("select status from outbox_event where id=?",String.class,id));assertEquals(5,db.queryForObject("select attempts from outbox_event where id=?",Integer.class,id));}finally{admin.declareQueue(delivery.eventQueue());listener.start();}}
 @Test void poisonMessageGoesToDeadQueue(){rabbit.convertAndSend("","haiduo.events","invalid-uuid");Message dead=rabbit.receive("haiduo.dead",10000);assertNotNull(dead);assertEquals("invalid-uuid",new String(dead.getBody(),StandardCharsets.UTF_8));}
}
