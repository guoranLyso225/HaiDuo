package cn.haiduo;
import cn.haiduo.storage.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import static org.junit.jupiter.api.Assertions.*;
class BlobContractTest {
 Path dir;
 @BeforeEach void directory()throws Exception{dir=Files.createTempDirectory(Path.of("target"),"storage-contract-");}
 void contract(BlobStore store)throws Exception{UUID tenant=UUID.randomUUID(),other=UUID.randomUUID(),id=UUID.randomUUID();byte[] bytes="tenant scoped persistent object".getBytes(StandardCharsets.UTF_8);store.put(tenant,id,bytes,"text/plain");assertArrayEquals(bytes,store.get(tenant,id));assertThrows(Exception.class,()->store.get(other,id));store.delete(tenant,id);assertThrows(Exception.class,()->store.get(tenant,id));}
 @Test void localPersistentContract()throws Exception{contract(new LocalBlobStore(dir));UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();new LocalBlobStore(dir).put(tenant,id,new byte[]{1,2,3},"image/png");assertArrayEquals(new byte[]{1,2,3},new LocalBlobStore(dir).get(tenant,id));}
 @Test void s3HttpContract()throws Exception{Map<String,byte[]> objects=new ConcurrentHashMap<>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{String key=e.getRequestURI().getPath();if(!e.getRequestHeaders().getFirst("Authorization").startsWith("AWS4-HMAC-SHA256")){e.sendResponseHeaders(403,-1);e.close();return;}switch(e.getRequestMethod()){case "PUT"->{byte[] body=e.getRequestBody().readAllBytes();if(Optional.ofNullable(e.getRequestHeaders().getFirst("Content-Encoding")).orElse("").contains("aws-chunked")){String text=new String(body,StandardCharsets.ISO_8859_1);int end=text.indexOf("\r\n");int length=Integer.parseInt(text.substring(0,end).split(";")[0],16);body=Arrays.copyOfRange(body,end+2,end+2+length);}objects.put(key,body);e.sendResponseHeaders(200,-1);}case "GET"->{byte[] body=objects.get(key);if(body==null)e.sendResponseHeaders(404,-1);else{e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);}}case "DELETE"->{objects.remove(key);e.sendResponseHeaders(204,-1);}default->e.sendResponseHeaders(405,-1);}e.close();});server.start();try(var client=S3Client.builder().endpointOverride(URI.create("http://127.0.0.1:"+server.getAddress().getPort())).region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key","test-secret"))).httpClientBuilder(UrlConnectionHttpClient.builder()).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build()).build()){contract(new S3BlobStore(client,"contract-bucket"));}finally{server.stop(0);}}
}
