package cn.haiduo;
import cn.haiduo.audit.Audit;
import cn.haiduo.storage.BlobStore;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
@RestController public class FileController {
 private final BlobStore blobs;private final JdbcTemplate db;private final OperationsController scope;private final TransactionTemplate tx;
 public FileController(BlobStore blobs,JdbcTemplate db,OperationsController scope,PlatformTransactionManager manager){this.blobs=blobs;this.db=db;this.scope=scope;this.tx=new TransactionTemplate(manager);}
 @PostMapping("/api/products/{id}/image") Object image(@PathVariable UUID id,@RequestParam MultipartFile file,Principal p,HttpSession s)throws IOException{UUID tenant=scope.tenant(p,s,"ADMIN","OPERATIONS");var products=db.query("select product_id from sku where tenant_id=? and id=?",(rs,n)->rs.getObject(1,UUID.class),tenant,id);if(products.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);if(file.isEmpty()||file.getSize()>2097152)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片大小必须为1字节至2MB");byte[] bytes=file.getBytes();String mime;try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持PNG/JPEG图片");var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持PNG/JPEG图片");if(reader.getWidth(0)>4096||reader.getHeight(0)>4096)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片尺寸不超过4096×4096");reader.read(0);mime=format.equalsIgnoreCase("png")?"image/png":"image/jpeg";}finally{reader.dispose();}}catch(javax.imageio.IIOException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片无法解析");}
  UUID fileId=UUID.randomUUID();String original=Optional.ofNullable(file.getOriginalFilename()).orElse("image").replaceAll("[\\p{Cntrl}\\\\/]","_");if(original.length()>120)original=original.substring(0,120);final String name=original;blobs.put(tenant,fileId,bytes,mime);try{tx.executeWithoutResult(status->{db.update("insert into stored_file(id,tenant_id,original_name,mime,size_bytes) values(?,?,?,?,?)",fileId,tenant,name,mime,bytes.length);db.update("update product set image_file_id=? where tenant_id=? and id=?",fileId,tenant,products.getFirst());new Audit(db).record(tenant,p.getName(),"PRODUCT_IMAGE",products.getFirst());});}catch(RuntimeException e){try{blobs.delete(tenant,fileId);}catch(IOException ignored){}throw e;}return Map.of("id",fileId,"url","/api/files/"+fileId);
 }
 @GetMapping("/api/files/{id}") ResponseEntity<byte[]> get(@PathVariable UUID id,Principal p,HttpSession s)throws IOException{UUID tenant=scope.tenant(p,s);var files=db.queryForList("select mime,original_name from stored_file where tenant_id=? and id=?",tenant,id);if(files.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)files.getFirst().get("mime"))).header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.inline().filename((String)files.getFirst().get("original_name"),StandardCharsets.UTF_8).build().toString()).cacheControl(CacheControl.noStore()).body(blobs.get(tenant,id));}
}
