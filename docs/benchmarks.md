# Benchmarks del motor

Suite reproducible para `T14`.

## Fixtures

| Fixture | Proposito | Datos privados |
| --- | --- | --- |
| `small` | humo rapido, 1 cohorte x 4 cursos | no |
| `medium` | carga intermedia, 3 cohortes x 6 cursos | no |
| `large` | carga mayor, 6 cohortes x 8 cursos | no |
| `infeasible-room` | aula insuficiente | no |
| `infeasible-teacher` | curso sin docente habilitado | no |

## Metricas obligatorias

`BenchmarkRunner` reporta por fixture:

- `seed`;
- `timeLimitSeconds`;
- `elapsedMillis`;
- `withinTimeLimit`;
- asignaciones constructivas;
- no asignables constructivos;
- errores de prevalidacion;
- `constructiveScore`;
- `annealedScore`.

## Baseline

| Fixture | Seed | Limite | Criterio |
| --- | ---: | ---: | --- |
| `small` | 11 | 2s | debe terminar en menos de 2s |
| `medium` | 13 | 5s | SA no empeora la constructiva |
| `large` | 17 | 10s | debe respetar `timeLimitSeconds` |
| `infeasible-room` | 19 | 2s | explica falta de aula compatible |
| `infeasible-teacher` | 23 | 2s | explica falta de docente |

La comparacion constructiva vs SA usa `Score.compareTo`: SA debe ser menor o igual que la constructiva. No se optimizan hiperparametros sin registrar `seed`.

## Ejecucion

```bash
mvn test
```
