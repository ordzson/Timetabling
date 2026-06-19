package edu.udeo.horarios.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.udeo.horarios.testkit.BenchmarkRunner.BenchmarkResult;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BenchmarkRunnerTest {
  @Test
  void defaultSuiteCoversExpectedFixtures() {
    Map<String, BenchmarkResult> results =
        new BenchmarkRunner().runDefaultSuite().stream()
            .collect(Collectors.toMap(BenchmarkResult::name, Function.identity()));

    assertEquals(
        java.util.Set.of("small", "medium", "large", "infeasible-room", "infeasible-teacher"),
        results.keySet());
    assertTrue(results.get("small").elapsedMillis() < 2_000);
    assertTrue(results.get("large").withinTimeLimit());
    assertTrue(results.get("infeasible-room").infeasibleExplained());
    assertTrue(results.get("infeasible-teacher").infeasibleExplained());
    assertTrue(results.values().stream().allMatch(BenchmarkResult::annealingDidNotWorsen));
  }

  @Test
  void feasibleFixturesAssignAtLeastOneSession() {
    BenchmarkRunner runner = new BenchmarkRunner();

    assertFalse(runner.runDefaultSuite().stream()
        .filter(result -> !result.name().startsWith("infeasible"))
        .anyMatch(result -> result.constructiveAssignments() == 0));
  }
}
