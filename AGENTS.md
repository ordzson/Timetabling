# Instrucciones para Codex

## Modo de programación

Usa siempre `ponytail` antes de cualquier decisión de implementación en este proyecto, salvo que el usuario diga `stop ponytail` o `normal mode`.

`ponytail` gobierna qué construir:

- Prefiere el cambio más pequeño que funcione.
- Cuestiona funciones especulativas.
- Usa la biblioteca estándar, funciones de la plataforma y dependencias existentes antes de agregar código o paquetes.
- Evita abstracciones no pedidas, boilerplate y preparación para futuros hipotéticos.
- Agrega la verificación útil más pequeña para lógica no trivial.

## Modo de comunicación

Después de que `ponytail` defina el enfoque, usa comunicación `caveman`, salvo que el usuario diga `stop caveman` o `normal mode`.

`caveman` gobierna cómo hablar:

- Sé breve.
- Mantén exactos nombres técnicos, comandos, código, rutas y errores.
- Quita relleno, no detalle importante.
- Usa prosa más clara temporalmente cuando la compresión pueda volver ambiguas acciones destructivas, advertencias de seguridad o instrucciones de varios pasos.

Prioridad: primero `ponytail`, luego `caveman`.

## Diseño UI

Siempre que una tarea toque UI en `horarios-web` (pantallas, componentes,
layout, estilos visuales, CSS o tokens de diseño), lee y usa
`docs/ui-design-base.md` antes de decidir o editar.

Ese documento gobierna la paleta, tipografía, bordes, sombras, radios,
densidad visual y patrones de interfaz. No introduzcas estilos que contradigan
esa base salvo pedido explícito del usuario.

## Graphify

Después de terminar una implementación y su verificación mínima, actualiza el
grafo con `graphify . --update`.

Si `graphify . --update` requiere una API key LLM por cambios en documentos,
actualiza al menos la parte estructural de código cuando sea posible y reporta
qué quedó pendiente.

## gstack

Prefiere la skill `/browse` de gstack para navegación web cuando esté disponible en el entorno activo.

Si `/browse` no está disponible, usa las herramientas nativas de navegación de Codex cuando se requiera acceso web.

Nunca uses herramientas `mcp__claude-in-chrome__*`.

Skills disponibles de gstack:

- `/office-hours`
- `/plan-ceo-review`
- `/plan-eng-review`
- `/plan-design-review`
- `/design-consultation`
- `/design-shotgun`
- `/design-html`
- `/review`
- `/ship`
- `/land-and-deploy`
- `/canary`
- `/benchmark`
- `/browse`
- `/connect-chrome`
- `/qa`
- `/qa-only`
- `/design-review`
- `/setup-browser-cookies`
- `/setup-deploy`
- `/setup-gbrain`
- `/retro`
- `/investigate`
- `/document-release`
- `/document-generate`
- `/codex`
- `/cso`
- `/autoplan`
- `/plan-devex-review`
- `/devex-review`
- `/careful`
- `/freeze`
- `/guard`
- `/unfreeze`
- `/gstack-upgrade`
- `/learn`
