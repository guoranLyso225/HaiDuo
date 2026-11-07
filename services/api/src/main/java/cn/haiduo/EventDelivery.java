package cn.haiduo;
import cn.haiduo.search.ProductSearch;
import java.util.*;
import org.springframework.amqp.core.Queue;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration @EnableScheduling @ConditionalOnProperty(name="haiduo.messaging.enabled",havingValue="true") public class EventDelivery {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private SyncProcessor processor;@org.springframework.beans.factory.annotation.Autowired(required=false) private ProductSearch search;
 private final JdbcTemplate db;private final RabbitTemplate rabbit;private final TransactionTemplate tx;
 public EventDelivery(JdbcTemplate db,RabbitTemplate rabbit,PlatformTransactionManager manager){this.db=db;this.rabbit=rabbit;this.tx=new TransactionTemplate(manager);}
 @Bean Queue eventQueue(){return QueueBuilder.durable("haiduo.events").deadLetterExchange("").deadLetterRoutingKey("haiduo.dead").build();}
 @Bean Queue deadQueue(){return QueueBuilder.durable("haiduo.dead").build();}
 @Scheduled(fixedDelayString="${haiduo.messaging.poll-ms:1000}") public void publish(){tx.executeWithoutResult(s->{var rows=db.queryForList("select id,attempts from outbox_event where status='PENDING' and available_at<=now() order by created_at for update skip locked limit 1");if(rows.isEmpty())return;UUID id=(UUID)rows.getFirst().get("id");int attempts=(Integer)rows.getFirst().get("attempts")+1;try{var correlation=new CorrelationData(id.toString());rabbit.convertAndSend("","haiduo.events",id.toString(),m->{m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);m.getMessageProperties().setMessageId(id.toString());return m;},correlation);if(!correlation.getFuture().get(5,TimeUnit.SECONDS).isAck()||correlation.getReturned()!=null)throw new IllegalStateException("Broker did not confirm routing");db.update("update outbox_event set status='SENT',sent_at=now(),attempts=? where id=?",attempts,id);}catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();db.update("update outbox_event set attempts=?,status=?,available_at=now()+(? * interval '1 second') where id=?",attempts,attempts>=5?"DEAD":"PENDING",Math.min(60,1<<attempts),id);}});}
 @RabbitListener(queues="haiduo.events") public void consume(String value){UUID id=UUID.fromString(value);try{tx.executeWithoutResult(s->{var events=db.queryForList("select tenant_id,kind,target_id from outbox_event where id=?",id);if(events.isEmpty())throw new IllegalArgumentException("Unknown event");UUID tenant=(UUID)events.getFirst().get("tenant_id");int added=db.update("insert into processed_event(tenant_id,event_id) values(?,?) on conflict do nothing",tenant,id);if(added==0)return;String kind=(String)events.getFirst().get("kind");if(Set.of("PRODUCT_CREATE","SKU_CREATE").contains(kind)&&search!=null)try{search.indexProduct(tenant,(String)events.getFirst().get("target_id"));}catch(Exception e){throw new IllegalStateException("Search indexing failed",e);}boolean sync=Set.of("STORE_SYNC","SHOPIFY_WEBHOOK").contains(kind);if(sync){if(processor==null)throw new IllegalStateException("Sync processor unavailable");try{processor.process(tenant,kind,(String)events.getFirst().get("target_id"));}catch(Exception e){throw new IllegalStateException("Platform sync failed",e);}}db.update("insert into sync_job(id,tenant_id,event_id,type,status) values(?,?,?,?,'COMPLETED') on conflict(tenant_id,event_id) do update set status='COMPLETED'",UUID.randomUUID(),tenant,id,sync?kind:"LOCAL_EVENT_DELIVERY");});}catch(RuntimeException failure){tx.executeWithoutResult(s->{var rows=db.queryForList("select tenant_id,kind from outbox_event where id=?",id);if(!rows.isEmpty())db.update("insert into sync_job(id,tenant_id,event_id,type,status) values(?,?,?,?,'FAILED') on conflict(tenant_id,event_id) do update set status='FAILED' where sync_job.status<>'COMPLETED'",UUID.randomUUID(),rows.getFirst().get("tenant_id"),id,rows.getFirst().get("kind"));});throw failure;}}
}
