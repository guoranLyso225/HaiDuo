package cn.haiduo;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
@RestControllerAdvice public class ApiErrors {
 @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class) ResponseEntity<?> concurrent(org.springframework.dao.PessimisticLockingFailureException e){return ResponseEntity.status(409).body(Map.of("message","并发事务冲突，请刷新后使用原幂等键重试"));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> integrity(org.springframework.dao.DataIntegrityViolationException e){return ResponseEntity.status(409).body(Map.of("message","数据关联或唯一键冲突，请刷新后重试"));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"请求无法完成":e.getReason()));}
}
