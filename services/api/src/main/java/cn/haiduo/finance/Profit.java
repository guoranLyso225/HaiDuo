package cn.haiduo.finance;
import java.math.*;
public final class Profit {
 private Profit(){}
 public static BigDecimal contribution(BigDecimal net,BigDecimal rate,BigDecimal cost,BigDecimal freight,BigDecimal platform){return net.multiply(rate).subtract(cost).subtract(freight).subtract(platform).setScale(2,RoundingMode.HALF_UP);}
}
