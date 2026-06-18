package edu.udeo.horarios.testkit;

import edu.udeo.horarios.solver.SolverModule;

public final class TestkitModule {
  private TestkitModule() {
  }

  public static String dependencyName() {
    return SolverModule.dependencyName();
  }
}
