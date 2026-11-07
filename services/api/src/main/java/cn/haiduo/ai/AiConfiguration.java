package cn.haiduo.ai;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.ai.openai.*;
import java.net.URI;
import java.time.Duration;
@Configuration @ConditionalOnProperty(name="AI_ENABLED",havingValue="true") public class AiConfiguration {
 public record ModelConnection(String baseUrl,String apiKey){@Override public String toString(){return "ModelConnection[redacted]";}}
 @Bean public ModelConnection modelApi(Environment env){String base=env.getRequiredProperty("AI_BASE_URL");URI uri=URI.create(base);if(uri.getHost()==null||!"https".equals(uri.getScheme())&&!java.util.Set.of("localhost","127.0.0.1").contains(uri.getHost()))throw new IllegalArgumentException("Model endpoint requires HTTPS or localhost");String key=env.getRequiredProperty("AI_API_KEY");if(key.isBlank())throw new IllegalArgumentException("AI_API_KEY required");if(uri.getPath()==null||uri.getPath().isBlank()||uri.getPath().equals("/"))base=base.replaceAll("/$","")+"/v1";return new ModelConnection(base,key);}
 @Bean public OpenAiChatModel chatModel(ModelConnection api,Environment env){return OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(api.baseUrl()).apiKey(api.apiKey()).model(env.getRequiredProperty("AI_CHAT_MODEL")).maxTokens(500).timeout(Duration.ofSeconds(10)).maxRetries(0).build()).httpClientBuilderCustomizer(b->b.timeout(com.openai.core.Timeout.builder().connect(Duration.ofSeconds(3)).read(Duration.ofSeconds(10)).write(Duration.ofSeconds(10)).request(Duration.ofSeconds(10)).build())).build();}
 @Bean public OpenAiEmbeddingModel embeddingModel(ModelConnection api,Environment env){return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder().baseUrl(api.baseUrl()).apiKey(api.apiKey()).model(env.getRequiredProperty("AI_EMBEDDING_MODEL")).encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT).timeout(Duration.ofSeconds(10)).maxRetries(0).build()).httpClientBuilderCustomizer(b->b.timeout(com.openai.core.Timeout.builder().connect(Duration.ofSeconds(3)).read(Duration.ofSeconds(10)).write(Duration.ofSeconds(10)).request(Duration.ofSeconds(10)).build())).build();}
}
