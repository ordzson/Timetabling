package edu.udeo.horarios.solver;

public record AnnealingConfig(
    double initialTemperature, double coolingRate, int maxIterations, int maxMoveAttempts) {
  public AnnealingConfig {
    if (initialTemperature <= 0) {
      throw new IllegalArgumentException("initialTemperature must be positive");
    }
    if (coolingRate <= 0 || coolingRate >= 1) {
      throw new IllegalArgumentException("coolingRate must be between 0 and 1");
    }
    if (maxIterations < 0 || maxMoveAttempts <= 0) {
      throw new IllegalArgumentException("iteration counts must be valid");
    }
  }

  public static AnnealingConfig defaults() {
    return new AnnealingConfig(10.0, 0.995, 1_000, 8);
  }
}
