package edu.udeo.horarios.solver;

import edu.udeo.horarios.domain.DomainModule;

public final class SolverModule {
  private SolverModule() {
  }

  public static String dependencyName() {
    return DomainModule.name();
  }
}
