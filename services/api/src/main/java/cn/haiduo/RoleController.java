package cn.haiduo;
import java.util.*;
import java.security.Principal;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
@RestController public class RoleController {
 private final OperationsController scope;public RoleController(OperationsController scope){this.scope=scope;}
 @GetMapping("/api/roles") Object roles(Principal p,HttpSession s){scope.requirePermission(p,s,Role.Permission.MEMBER_ADMIN);return Arrays.stream(Role.values()).map(r->Map.of("code",r.name(),"permissions",r.permissions().stream().map(Enum::name).sorted().toList())).toList();}
}
