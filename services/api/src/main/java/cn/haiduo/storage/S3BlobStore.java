package cn.haiduo.storage;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
public class S3BlobStore implements BlobStore,AutoCloseable {
 private final S3Client client;private final String bucket;
 public S3BlobStore(S3Client client,String bucket){this.client=client;this.bucket=bucket;}
 private String key(UUID tenant,UUID id){return tenant+"/"+id;}
 public void put(UUID t,UUID id,byte[] bytes,String mime){client.putObject(PutObjectRequest.builder().bucket(bucket).key(key(t,id)).contentType(mime).build(),RequestBody.fromBytes(bytes));}
 public byte[] get(UUID t,UUID id)throws java.io.IOException{try(var response=client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(t,id)).build())){byte[] bytes=response.readNBytes(2097153);if(bytes.length>2097152)throw new java.io.IOException("Object exceeds size limit");return bytes;}}
 public void delete(UUID t,UUID id){client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(t,id)).build());}
 public void close(){client.close();}
}
