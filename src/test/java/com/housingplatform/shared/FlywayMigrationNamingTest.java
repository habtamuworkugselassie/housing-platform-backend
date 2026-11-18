package com.housingplatform.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Two migrations with the same version stop the application at boot (Flyway refuses to run),
 * which the H2 test profile does not catch because it disables Flyway. This does.
 */
class FlywayMigrationNamingTest {

  private static final Pattern NAME = Pattern.compile("^V(\\d+)__.+\\.sql$");

  @Test
  void migrationVersionsAreUnique() throws IOException {
    Resource[] files =
        new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
    Map<Integer, String> seen = new HashMap<>();
    for (Resource f : files) {
      String name = f.getFilename();
      Matcher m = NAME.matcher(name == null ? "" : name);
      assertThat(m.matches()).as("migration file name %s", name).isTrue();
      int version = Integer.parseInt(m.group(1));
      String other = seen.put(version, name);
      assertThat(other).as("version %d used by both %s and %s", version, other, name).isNull();
    }
    assertThat(seen).isNotEmpty();
  }
}
