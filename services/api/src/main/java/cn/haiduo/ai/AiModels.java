package cn.haiduo.ai;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service public class AiModels {
 private final ObjectProvider<ChatModel> chats;private final ObjectProvider<EmbeddingModel> embeddings;private final boolean demo;
 public AiModels(ObjectProvider<ChatModel> chats,ObjectProvider<EmbeddingModel> embeddings,Environment env){this.chats=chats;this.embeddings=embeddings;demo=env.getProperty("AI_DEMO",Boolean.class,false);}
 public String mode(){return demo?"FIXED_DEMO":chats.getIfAvailable()!=null&&embeddings.getIfAvailable()!=null?"MODEL":"DISABLED";}
 public void require(){if(mode().equals("DISABLED"))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI未启用：未配置聊天和嵌入模型；演示需显式AI_DEMO=true");}
 private <T>T bounded(Callable<T> work){var executor=Executors.newVirtualThreadPerTaskExecutor();try{var f=executor.submit(work);try{return f.get(15,TimeUnit.SECONDS);}catch(Exception e){f.cancel(true);throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"模型调用失败或超时");}}finally{executor.shutdownNow();}}
 public String chat(String prompt){require();if(demo)return "【固定演示，非模型调用】请结合商品真实属性编写说明；此文案仅为待审核草稿，不自动发布。";return bounded(()->chats.getObject().call(prompt));}
 public float[] embed(String text){require();if(!demo)return bounded(()->embeddings.getObject().embed(text));float[] v=new float[128];text.codePoints().forEach(c->v[Math.floorMod(c,128)]++);return v;}
 public static double similarity(float[] a,float[] b){if(a.length!=b.length)return 0;double dot=0,x=0,y=0;for(int i=0;i<a.length;i++){dot+=a[i]*b[i];x+=a[i]*a[i];y+=b[i]*b[i];}return x==0||y==0?0:dot/Math.sqrt(x*y);}
}
