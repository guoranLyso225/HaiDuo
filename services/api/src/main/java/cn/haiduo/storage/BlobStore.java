package cn.haiduo.storage;
import java.io.IOException;
import java.util.UUID;
public interface BlobStore {void put(UUID tenant,UUID id,byte[] bytes,String mime)throws IOException;byte[] get(UUID tenant,UUID id)throws IOException;void delete(UUID tenant,UUID id)throws IOException;}
