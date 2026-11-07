package cn.haiduo;
import cn.haiduo.catalog.ProductRepository;
import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController public class CatalogController {
 private final ProductRepository products;private final SessionController sessions;
 public CatalogController(ProductRepository products,SessionController sessions){this.products=products;this.sessions=sessions;}
 @GetMapping("/api/catalog/products/{id}") Object detail(@PathVariable UUID id,Principal p,HttpSession s){var product=products.findByIdAndTenantId(id,sessions.activeTenant(p,s)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return Map.of("id",product.getId(),"name",product.getName());}
}
