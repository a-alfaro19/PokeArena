# PokéArena: instrucciones para el agente

Proyecto del curso CE-1116 (TEC). Backend en Java 21 con Spring Boot, PostgreSQL y Redis,
más una interfaz web en HTML, CSS y JavaScript servida por la misma aplicación.

## Fuente de verdad
La especificación completa de lo que debe construirse está en:
@pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md

Si el código existente contradice ese documento, gana el documento. No tienes acceso a la
wiki de Azure DevOps: los diagramas, las decisiones de arquitectura y los criterios de
aceptación están en los anexos de ese archivo.

## Reglas de trabajo
- Trabaja **una fase a la vez** (sección 16 de la especificación) y, al terminar, entrega
  un resumen y espera la aprobación del equipo antes de seguir.
- No inventes requerimientos; ante una ambigüedad, pregunta.
- No uses Spring Security ni frameworks que tomen decisiones de autenticación (RES-01).
- El código existente está en modo "esqueleto nuevo y portar" (sección 15): lo viejo va a
  `legacy/` como referencia, y de ahí solo se porta lo que indica la especificación.
- Las seis historias deben compartir estructura, errores, autorización e idempotencia.
- Cero números mágicos: valores configurables o constantes con nombre.
- Cada clase lleva un encabezado con propósito y responsabilidades, y los casos de uso
  citan el paso del diagrama (Anexo A) que implementan.
- Todo el código debe poder defenderse en una oral: explica cada decisión que tomes.

## Git
- Nunca trabajes directo en `main`. No hagas commit ni push sin que yo lo pida.
- Una rama por tarea con el formato `feature/<id>-...`, `fix/<id>-...` o `docs/<id>-...`,
  partiendo de `main` actualizado.