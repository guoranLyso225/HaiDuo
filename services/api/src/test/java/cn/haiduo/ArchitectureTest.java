package cn.haiduo;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static org.junit.jupiter.api.Assertions.*;
class ArchitectureTest {
 @Test void modulesHaveNoCyclesOrUndeclaredDependencies(){var modules=ApplicationModules.of(Application.class);modules.verify();var names=modules.stream().map(m->m.getIdentifier().toString()).toList();assertTrue(names.containsAll(java.util.List.of("inventory","finance","fulfillment","audit","orders","ai","search","catalog","storage","platform")));}
}
