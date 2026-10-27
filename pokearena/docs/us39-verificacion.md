# Verificación de US 39

Ejecutada el 27 de octubre de 2026 en Windows, JDK 21.0.1, Maven 3.9.9 y PostgreSQL 18.4 local. Comando: `scripts/test-us39.ps1` con las rutas locales de JDK y Maven. La base se creó en un clúster temporal distinto del PostgreSQL de trabajo; se detuvo al finalizar.

`clean verify`: **BUILD SUCCESS**. Se compilaron 30 clases de producción y 4 clases de pruebas, se ejecutaron **64 pruebas: 0 fallos, 0 errores y 0 omitidas**, y se generó el JAR ejecutable. Distribución: 46 PostgreSQL/HTTP, 16 unitarias de validación/identidad, 1 de arquitectura y 1 de contexto preexistente. El pom conserva Java 25; el comando verifica compatibilidad con Java 21 mediante una propiedad local.

Rendimiento observado con 10005 cartas: p95 inicial **467 ms** (12 muestras medidas después de 3 calentamientos), p95 de continuación **8 ms** (100 páginas de continuación). Se verificó el recorrido completo sin códigos repetidos y páginas de hasta 100 cartas; el tamaño por defecto es 50. El objetivo propio de búsqueda es p95 inferior a 1000 ms en esta prueba local sin contención. Estos datos no son una prueba de carga multiinstancia ni una garantía de latencia de producción.

`EXPLAIN (ANALYZE, BUFFERS)` para nombre normalizado selectivo `%09999%`: Bitmap Index Scan en `us39_cards_name` y Bitmap Heap Scan, 1 resultado final. Tiempo de ejecución observado: 1.104 ms. Los planes y tiempos pueden variar por máquina y selectividad.

También se verificó la migración partiendo del `schema.sql` anterior y una habilidad insertada antes de iniciar Spring: nombre conservado, columnas nuevas presentes, disparador/efecto null y parámetros `{}`. Reejecutar la inicialización no vuelve a aplicar V039 ni elimina cartas.

## Criterios y comprobaciones

| Criterios | Implementación y evidencia |
| --- | --- |
| 1–3 | Filtros individuales y AND; normalización de nombre; `%`, `_`, `!`, barra y comillas literales; números exactos, rangos y extremos. Pruebas parametrizadas y combinadas. |
| 4 | DTO de salida separado de Card, ambas referencias y estado de imagen, habilidad con parámetros; metadatos legados sin inventar valores. |
| 5–6 | Página 50/100, límites de bytes/filas/búsquedas, timeout y 10005 cartas recorridas; pruebas de capacidad y contención. |
| 7–8 | 401 y 403 comprobados por HTTP con identidad dev/test. Configuración cerrada por defecto y exclusión de perfiles prod/production comprobadas unitariamente. Proveedor real pendiente. |
| 9 | Errores con criterio; enums, límites, rangos invertidos y exacto+rango; parámetros repetidos y desconocidos. |
| 10–11 | Lista vacía con 200, filtros incompatibles y recorrido sin filtros con los mismos límites. |
| 12 | Inserción escrita antes del snapshot y confirmada entre páginas, otra posterior y modificación de parámetros de habilidad: el conjunto original no cambia; la búsqueda nueva sí ve las nuevas cartas. |
| 13 | Se permite visibilidad posterior; esta implementación usa primaria y las nuevas búsquedas ven las cartas confirmadas al materializar. |
| Cursor | Formato, token desconocido/expirado, filtros/tamaño distintos, otro sujeto y reintentos sin crecimiento de tokens. |
| Persistencia | Límites globales de filas y bytes, rollback completo ante exceso, limpieza en cascada y migración repetible. |
| Arquitectura | Dominio y aplicación sin Spring/JDBC/Jackson/infraestructura ni dependencias de dominio hacia aplicación. |

Se comprobó también la sintaxis YAML del contrato y la presencia de sus 15 parámetros y 6 respuestas mediante SnakeYAML instalado. Esto no sustituye un validador completo de la especificación OpenAPI ni pruebas de compatibilidad contractual en CI.

## Archivos incorporados o modificados

| Grupo | Archivos |
| --- | --- |
| Entrada y salida | `application/dto/CardSearchRequest.java`, `CardSearchItem.java`, `CardSearchPage.java` |
| Puertos | `application/port/CardSearchQuery.java`, `SearchIdentity.java` |
| Aplicación | `application/usecase/SearchCardsUseCase.java` |
| Dominio | `domain/model/CardSearchCriteria.java`, `CardSearchException.java` |
| HTTP | `infrastructure/web/CardSearchController.java`, `CardSearchErrorHandler.java` |
| Configuración | `infrastructure/config/CardSearchConfiguration.java`; referencias a variables de prueba en `application.properties` |
| Persistencia | `infrastructure/persistence/JdbcCardSearchQuery.java`, `SearchSchemaMigration.java`; `db/migration/V039__card_search.sql` |
| Pruebas | `CardSearchValidationTests.java`, `CardSearchArchitectureTests.java`, `CardSearchPostgresTests.java`; `scripts/test-us39.ps1` |
| Documentación | OpenAPI, guía de ejecución y decisiones, este registro, tres diagramas Mermaid, declaración de IA y copia del requerimiento; enlace desde README raíz |

Los caminos Java son relativos a `src/main/java/cr/ac/tec/pokearena`; los tests a `src/test/java/cr/ac/tec/pokearena`. No cambiaron Card, CardRepository, CreateCardUseCase, CreateLocationUseCase, controladores existentes, ImageService, schema.sql, pom ni wrapper. Se conservó el directorio `.vscode` que ya estaba sin seguimiento.

## Integración pendiente

El equipo de autenticación debe aportar un adaptador real de SearchIdentity y desplegar sin dev/test. El responsable de habilidades debe definir identificadores y contratos oficiales de parámetros y completar los metadatos. Las referencias locales de imágenes aún requieren un mecanismo de acceso HTTP ajeno a esta US. No se ejecutó una validación con Java 25 porque el JDK utilizado fue 21.

El entorno aislado de comandos falló antes de iniciar por un error de la unidad L:. Los comandos autorizados se ejecutaron con el mecanismo de escalamiento, sin descartar cambios del usuario. Los primeros intentos de compilación detectaron un import desactualizado y clases previas de Java 25; se corrigió el import y se hizo una reconstrucción limpia. El primer lanzador temporal se corrigió para esperar solo a pg_ctl; la ejecución final inicia y detiene correctamente el servidor.

Los reportes originales están en `target/surefire-reports` y el registro en `target/us39-verification.log`, generados e ignorados por Git. No hubo commit, push ni merge.

