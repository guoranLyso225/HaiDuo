package cn.haiduo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoleTest {
 @Test void warehouseCannotExportOrConfirmFinancials(){assertFalse(Role.WAREHOUSE.allows(Role.Permission.ORDER_EXPORT));assertFalse(Role.WAREHOUSE.allows(Role.Permission.FINANCE_WRITE));assertTrue(Role.WAREHOUSE.allows(Role.Permission.SHIPMENT_WRITE));}
 @Test void financeCannotChangeStockAndOperationsCannotReplay(){assertFalse(Role.FINANCE.allows(Role.Permission.INVENTORY_WRITE));assertFalse(Role.OPERATIONS.allows(Role.Permission.SYNC_REPLAY));assertTrue(Role.ADMIN.allows(Role.Permission.MEMBER_ADMIN));}
}
