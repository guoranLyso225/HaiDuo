package cn.haiduo.catalog;
import java.util.*;
import org.springframework.data.repository.Repository;
public interface ProductRepository extends Repository<ProductEntity,UUID>{Optional<ProductEntity> findByIdAndTenantId(UUID id,UUID tenantId);}
