package cn.haiduo.storage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
public class LocalBlobStore implements BlobStore {
 private final Path root;
 public LocalBlobStore(Path root)throws IOException{Files.createDirectories(root);this.root=root.toRealPath();}
 private Path file(UUID tenant,UUID id)throws IOException{Path dir=root.resolve(tenant.toString());Files.createDirectories(dir);if(Files.isSymbolicLink(dir)||!dir.toRealPath().startsWith(root))throw new IOException("Invalid storage directory");Path file=dir.resolve(id.toString());if(Files.isSymbolicLink(file))throw new IOException("Invalid storage file");return file;}
 public void put(UUID t,UUID id,byte[] b,String mime)throws IOException{Path file=file(t,id);Files.write(file,b,StandardOpenOption.CREATE_NEW);}
 public byte[] get(UUID t,UUID id)throws IOException{Path path=file(t,id);if(Files.size(path)>2097152)throw new IOException("Object exceeds size limit");return Files.readAllBytes(path);}
 public void delete(UUID t,UUID id)throws IOException{Files.deleteIfExists(file(t,id));}
}
