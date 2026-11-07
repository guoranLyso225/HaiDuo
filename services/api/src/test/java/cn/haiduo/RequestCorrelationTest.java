package cn.haiduo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.slf4j.MDC;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RequestCorrelationTest {
 @Test void generatedIdIsAvailableAndCleared()throws Exception{var response=new MockHttpServletResponse();new RequestCorrelationFilter().doFilter(new MockHttpServletRequest(),response,(req,res)->assertNotNull(MDC.get("requestId")));assertNotNull(UUID.fromString(response.getHeader("X-Request-ID")));assertNull(MDC.get("requestId"));}
 @Test void clearsIdAfterFailure(){var response=new MockHttpServletResponse();assertThrows(IllegalStateException.class,()->new RequestCorrelationFilter().doFilter(new MockHttpServletRequest(),response,(req,res)->{throw new IllegalStateException("test");}));assertNull(MDC.get("requestId"));}
}
