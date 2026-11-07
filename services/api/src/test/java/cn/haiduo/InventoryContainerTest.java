package cn.haiduo;
import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.flywaydb.core.Flyway;
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_TESTCONTAINERS",matches="true")
class InventoryContainerTest extends InventoryDatabaseTest {
 static PostgreSQLContainer postgres;
 @BeforeAll static void container(){postgres=new PostgreSQLContainer("postgres:17.6").withDatabaseName("haiduo").withUsername("haiduo").withPassword("isolated-test-password");postgres.start();System.setProperty("haiduo.test.url",postgres.getJdbcUrl());System.setProperty("haiduo.test.password",postgres.getPassword());Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load().migrate();}
 @AfterAll static void stop(){System.clearProperty("haiduo.test.url");System.clearProperty("haiduo.test.password");if(postgres!=null)postgres.stop();}
}
