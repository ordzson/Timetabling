package edu.udeo.horarios.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SolverModuleTest {
  @Test
  void dependsOnDomainOnly() {
    assertEquals("horarios-domain", SolverModule.dependencyName());
  }
}
