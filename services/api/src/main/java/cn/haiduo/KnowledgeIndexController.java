package cn.haiduo;

import cn.haiduo.ai.VectorIndex;
import cn.haiduo.audit.Audit;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai/knowledge/index")
public class KnowledgeIndexController {
 private final JdbcTemplate db;
 private final OperationsController scope;
 private final VectorIndex vectors;
 public KnowledgeIndexController(JdbcTemplate db,OperationsController scope,VectorIndex vectors){this.db=db;this.scope=scope;this.vectors=vectors;}
 static void lock(JdbcTemplate db,UUID tenant){db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","knowledge-index:"+tenant);}
 @GetMapping Object status(Principal user,HttpSession session){
  UUID tenant=scope.tenant(user,session,"ADMIN");
  return Map.of("enabled",vectors.enabled(),"chunks",db.queryForObject("select count(*) from knowledge_chunk where tenant_id=?",Integer.class,tenant),"basis","使用主库已有向量，不调用模型；更换模型仍需重新向量化");
 }
 @PostMapping("/rebuild") @Transactional Object rebuild(Principal user,HttpSession session){
  UUID tenant=scope.tenant(user,session,"ADMIN");
  if(!vectors.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"未配置独立向量库，当前使用主库检索，无需重建");
  lock(db,tenant);
  List<VectorIndex.Entry> entries=db.query("select c.id,c.embedding from knowledge_chunk c join knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id where c.tenant_id=? order by c.id",(rs,n)->{
   Object[] values=(Object[])rs.getArray(2).getArray();float[] embedding=new float[values.length];for(int i=0;i<values.length;i++)embedding[i]=((Number)values[i]).floatValue();
   return new VectorIndex.Entry(rs.getObject(1,UUID.class),embedding);
  },tenant);
  int count;
  try{count=vectors.replaceTenant(tenant,entries);}catch(Exception failure){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"向量索引重建未确认成功；事务内失败会回滚，可检查向量库后安全重试");}
  new Audit(db).record(tenant,user.getName(),"KNOWLEDGE_INDEX_REBUILD",tenant);
  return Map.of("indexedChunks",count,"modelCalls",0,"basis","按当前企业主库向量完整替换派生索引，清理孤立记录；不重新生成嵌入");
 }
}
