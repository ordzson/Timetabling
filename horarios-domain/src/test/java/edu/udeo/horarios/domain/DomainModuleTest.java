package edu.udeo.horarios.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DomainModuleTest {
  @Test
  void exposesModuleName() {
    assertEquals("horarios-domain", DomainModule.name());
  }
}
