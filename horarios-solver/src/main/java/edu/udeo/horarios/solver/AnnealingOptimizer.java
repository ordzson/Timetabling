package edu.udeo.horarios.solver;

import java.util.SplittableRandom;

public final class AnnealingOptimizer {
  private final MoveGenerator generator;
  private final MoveEvaluator evaluator;
  private final MoveApplier applier;
  private final IncrementalSoftScorer scorer;
  private final AnnealingConfig config;
  private final SplittableRandom random;

  public AnnealingOptimizer(
      MoveGenerator generator,
      MoveEvaluator evaluator,
      MoveApplier applier,
      IncrementalSoftScorer scorer,
      AnnealingConfig config,
      long seed) {
    this.generator = generator;
    this.evaluator = evaluator;
    this.applier = applier;
    this.scorer = scorer;
    this.config = config;
    this.random = new SplittableRandom(seed);
  }

  public Schedule optimize(Schedule initial) {
    Schedule current = initial.copy();
    Schedule best = current.copy();
    Score bestScore = scorer.score(best);
    double temperature = config.initialTemperature();

    for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
      MoveProposal proposal = null;
      MoveEvaluation evaluation = null;
      for (int attempt = 0; attempt < config.maxMoveAttempts(); attempt++) {
        proposal = generator.next(current);
        evaluation = evaluator.evaluate(current, proposal);
        if (evaluation.valid()) {
          break;
        }
      }

      if (evaluation != null && evaluation.valid() && accept(evaluation.scoreDelta(), temperature)) {
        applier.apply(current, proposal, evaluation);
        Score currentScore = scorer.score(current);
        if (currentScore.compareTo(bestScore) < 0) {
          best = current.copy();
          bestScore = currentScore;
        }
      }
      temperature *= config.coolingRate();
    }

    return best;
  }

  private boolean accept(long delta, double temperature) {
    return delta < 0 || random.nextDouble() < Math.exp(-delta / temperature);
  }
}
