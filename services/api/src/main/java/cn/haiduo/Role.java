package cn.haiduo;
import java.util.*;
public enum Role {
 ADMIN(EnumSet.allOf(Permission.class)),
 OPERATIONS(EnumSet.of(Permission.BUSINESS_READ,Permission.PRODUCT_WRITE,Permission.ORDER_IMPORT,Permission.ORDER_EXPORT,Permission.SYNC_READ,Permission.SYNC_WRITE,Permission.KNOWLEDGE_WRITE)),
 WAREHOUSE(EnumSet.of(Permission.BUSINESS_READ,Permission.INVENTORY_WRITE,Permission.SHIPMENT_WRITE)),
 FINANCE(EnumSet.of(Permission.BUSINESS_READ,Permission.FINANCE_READ,Permission.FINANCE_WRITE,Permission.ORDER_EXPORT));
 public enum Permission {BUSINESS_READ,PRODUCT_WRITE,ORDER_IMPORT,ORDER_EXPORT,INVENTORY_WRITE,SHIPMENT_WRITE,FINANCE_READ,FINANCE_WRITE,SYNC_READ,SYNC_WRITE,SYNC_REPLAY,KNOWLEDGE_WRITE,MEMBER_ADMIN}
 private final Set<Permission> permissions;Role(Set<Permission> permissions){this.permissions=Set.copyOf(permissions);}public Set<Permission> permissions(){return permissions;}public boolean allows(Permission permission){return permissions.contains(permission);}
}
