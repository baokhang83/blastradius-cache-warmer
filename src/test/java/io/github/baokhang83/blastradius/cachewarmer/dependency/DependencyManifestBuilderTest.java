package io.github.baokhang83.blastradius.cachewarmer.dependency;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class DependencyManifestBuilderTest {
 @Test void buildsThirdPartyJarManifest() {
  String tree = "[INFO] +- org.junit.jupiter:junit-jupiter-api:jar:5.10.2:test\n[INFO] +- org.example:reactor:jar:1:compile\n[INFO] \\ - jakarta:api:jar:1:provided";
  DependencyManifest manifest = new DependencyManifestBuilder(new DependencyTreeParser()).build(tree, Set.of("org.example:reactor"));
  assertEquals(1, manifest.artifacts().size());
  assertEquals("org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar", manifest.artifacts().getFirst().repositoryPath().toString());
 }
}
