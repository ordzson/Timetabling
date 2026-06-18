package edu.udeo.horarios.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestkitModuleTest {
  @Test
  void reachesSolverAndDomain() {
    assertEquals("horarios-domain", TestkitModule.dependencyName());
  }
}
