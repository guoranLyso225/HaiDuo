package cn.haiduo.catalog;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="product") public class ProductEntity {
 @Id private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(nullable=false) private String name;
 protected ProductEntity(){}
 public UUID getId(){return id;}public String getName(){return name;}
}
