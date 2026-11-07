package cn.haiduo.storage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.*;
@Configuration public class StorageConfiguration {
 @Bean BlobStore blobs(Environment env)throws IOException{String backend=env.getProperty("STORAGE_BACKEND","local");if(!java.util.Set.of("local","s3").contains(backend))throw new IllegalArgumentException("STORAGE_BACKEND must be local or s3");if("local".equals(backend))return new LocalBlobStore(Path.of(env.getProperty("FILE_ROOT",".haiduo-files")));String bucket=env.getRequiredProperty("S3_BUCKET");var builder=S3Client.builder().region(Region.of(env.getProperty("S3_REGION","us-east-1"))).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(env.getRequiredProperty("S3_ACCESS_KEY"),env.getRequiredProperty("S3_SECRET_KEY")))).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10))).overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(10))).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build());String endpoint=env.getProperty("S3_ENDPOINT");if(endpoint!=null&&!endpoint.isBlank())builder.endpointOverride(URI.create(endpoint));return new S3BlobStore(builder.build(),bucket);}
}
