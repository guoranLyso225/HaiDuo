package cn.haiduo;
import cn.haiduo.finance.Profit;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProfitTest { @Test void contribution(){assertEquals(new BigDecimal("398.00"),Profit.contribution(new BigDecimal("100"),new BigDecimal("7.2"),new BigDecimal("200"),new BigDecimal("50"),new BigDecimal("72")));} }
