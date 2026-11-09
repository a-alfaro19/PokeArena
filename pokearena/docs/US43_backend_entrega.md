# US 43: backend de catálogo y colección

Implementado en la rama `feature/43-catalogo-coleccion`, desde `main` actualizado con `git fetch origin main` y avance rápido. El árbol estaba limpio antes de comenzar. No se hicieron commits, push, PR ni modificaciones en Azure DevOps.

## Fuentes y decisiones

Se consultó por MCP la [US 43](https://dev.azure.com/hencastro/PokeArena/_workitems/edit/43), revisión 5, con descripción y criterios completos: catálogo paginado con nombre, imagen, costo, poder, tipo, rareza y habilidad; filtros combinables de tipo y costo; propiedad y cantidad de copias; solo propias; errores descriptivos con valores permitidos; lista vacía sin coincidencias; imagen grande y tolerancia de unos segundos para cartas nuevas.

La US está asignada a BAGLIANO GOMEZ ALDO ALESSANDRO. No tiene tareas hijas, dependencias explícitas, adjuntos ni enlaces de implementación: solo su padre [feature 24, Colección y Decks](https://dev.azure.com/hencastro/PokeArena/_workitems/edit/24), cuyo alcance también se consultó. Se buscaron además tareas con catálogo/colección en el título; las encontradas pertenecen a otras historias. No se encontró tarea específica de backend de la US 43 y se usó el número 43 para la rama.

Se leyeron CLAUDE.md y AGENTE_Especificacion_v2_Sprint2_PokeArena.md, especialmente 5.2, 5.3, 5.5, 6, 8.3, 9, A.6 y C.6. No se encontraron contradicciones funcionales entre la US, la especificación y el alcance solicitado. La indicación antigua de que el agente no puede acceder a la wiki no describe ya la disponibilidad del MCP; no cambia ningún requisito implementado.

La especificación distingue INVALID_FIELD en catálogo de INVALID_SEARCH_CRITERIA en búsqueda administrativa. Se conserva esa diferencia. El catálogo permite PLAYER y ADMIN explícitamente. Solo devuelve cartas con available y active; las restricciones existentes de la base garantizan READY y miniatura para una carta disponible. No se añadió una condición sobre la actividad de la habilidad, que 8.3 no exige.

La página compartida contiene CardDetails, nunca owned ni copies. Su espacio de caché es player-catalog, separado de admin-search. Tras obtenerla se consultan las copias de la sesión en un solo lote de códigos. owned=true usa una unión parametrizada con player_card y obtiene las copias en esa misma consulta, sin caché. No hay una consulta por cada carta. El puerto CatalogCache y NoCatalogCache conservan sus firmas y comportamiento; Redis queda pendiente. La búsqueda administrativa sigue incluyendo cartas inactivas y no disponibles.

## Archivos

Las rutas de la tabla se refieren al módulo `pokearena`.

| Archivo | Responsabilidad |
|---|---|
| [CatalogController.java](../src/main/java/cr/ac/tec/pokearena/api/controller/CatalogController.java) | Expone GET /api/v1/catalogo para PLAYER y ADMIN y obtiene el jugador exclusivamente de AuthenticatedUser. |
| [CatalogCardResponse.java](../src/main/java/cr/ac/tec/pokearena/api/dto/CatalogCardResponse.java) | Define la ficha HTTP con imágenes, habilidad, owned y copies sin cambiar CardResponse. |
| [ExploreCatalogCommand.java](../src/main/java/cr/ac/tec/pokearena/application/dto/ExploreCatalogCommand.java) | Transporta identidad verificada y parámetros inmutables, incluidas repeticiones. |
| [CatalogCardDetails.java](../src/main/java/cr/ac/tec/pokearena/application/dto/CatalogCardDetails.java) | Combina ficha y copias; deriva owned de copies > 0. |
| [ExploreCatalogUseCase.java](../src/main/java/cr/ac/tec/pokearena/application/usecase/ExploreCatalogUseCase.java) | Sigue A.6: valida, pagina, consulta caché pública o colección y combina copias por lote. |
| [CatalogCacheKey.java](../src/main/java/cr/ac/tec/pokearena/application/port/CatalogCacheKey.java) | Añade el espacio player-catalog para separar las páginas de la búsqueda administrativa. |
| [CatalogCriteria.java](../src/main/java/cr/ac/tec/pokearena/domain/model/CatalogCriteria.java) | Representa filtros validados y reutiliza CardSearchCriteria para las condiciones SQL existentes. |
| [OwnedCard.java](../src/main/java/cr/ac/tec/pokearena/domain/model/OwnedCard.java) | Transporta carta, id y copias obtenidos juntos para paginar la colección. |
| [CatalogRules.java](../src/main/java/cr/ac/tec/pokearena/domain/rule/CatalogRules.java) | Valida tipo, rangos y owned con INVALID_FIELD y allowedValues; rechaza parámetros ajenos, vacíos o repetidos. |
| [PlayerCatalogRepository.java](../src/main/java/cr/ac/tec/pokearena/domain/repository/PlayerCatalogRepository.java) | Puerto de lectura para catálogo disponible, colección y copias por lote; conserva intactos los puertos existentes. |
| [CardSearchSql.java](../src/main/java/cr/ac/tec/pokearena/infrastructure/persistence/CardSearchSql.java) | Reutiliza proyección y filtros parametrizados, con visibilidad y unión a player_card solo en el catálogo del jugador. |
| [JdbcCardRepository.java](../src/main/java/cr/ac/tec/pokearena/infrastructure/persistence/JdbcCardRepository.java) | Implementa las lecturas paginadas y de copias; reutiliza el mapeo de cartas y habilidades. |
| [CardConfiguration.java](../src/main/java/cr/ac/tec/pokearena/infrastructure/config/CardConfiguration.java) | Ensambla el caso de uso con Spring fuera de dominio y aplicación. |
| [pokearena.yaml](../src/main/resources/openapi/pokearena.yaml) | Añade únicamente la operación y los esquemas CatalogCard y CatalogPage. |
| [OpenApiContractIT.java](../src/test/java/cr/ac/tec/pokearena/api/OpenApiContractIT.java) | Incluye los parámetros admitidos por el nuevo controlador en la comparación bidireccional. |
| [CatalogIT.java](../src/test/java/cr/ac/tec/pokearena/api/controller/CatalogIT.java) | 44 pruebas HTTP: sesión, aislamiento, copias, filtros, paginación, actividad, disponibilidad, contrato y rendimiento. |
| [ExploreCatalogUseCaseTest.java](../src/test/java/cr/ac/tec/pokearena/application/usecase/ExploreCatalogUseCaseTest.java) | 4 pruebas sin Spring: caché sin datos personales, owned sin caché, validación previa y claves normalizadas. |
| [US43_backend_entrega.md](../docs/US43_backend_entrega.md) | Documenta fuentes, implementación, verificación y prueba manual de esta entrega. |

## Respuesta HTTP real

Capturada por CatalogIT contra el servidor real y PostgreSQL de Testcontainers con `GET /api/v1/catalogo?limit=1`. Los valores pertenecen a datos de prueba y el cursor depende de sus ids; las URLs de esta fixture no tienen archivos en disco.

```json
{
  "items" : [ {
    "code" : "PKA-FUE-001",
    "name" : "Carta PKA-FUE-001",
    "cost" : 0,
    "power" : -2,
    "type" : {
      "code" : "FUE",
      "name" : "Fuego"
    },
    "rarity" : {
      "code" : "COMMON",
      "name" : "Común"
    },
    "imageLargeUrl" : "/images/cards/PKA-FUE-001/large.png",
    "imageSmallUrl" : "/images/cards/PKA-FUE-001/small.png",
    "ability" : {
      "code" : "HAB-000",
      "name" : "Sin Habilidad",
      "limitedToType" : null,
      "trigger" : null,
      "effect" : null,
      "parameters" : { }
    },
    "owned" : true,
    "copies" : 3
  } ],
  "nextCursor" : "MTA2NTY",
  "limit" : 1
}
```

Las cartas no poseídas devuelven `owned: false` y `copies: 0`; la última página devuelve `nextCursor: null`. No se devuelve total ni vencimiento.

## Validación ejecutada

Se ejecutó desde el módulo `.\mvnw.cmd clean verify`, con Docker Desktop disponible y Temurin JDK 21.0.13. Resultado final: **BUILD SUCCESS**, 362 pruebas de Surefire y 345 de Failsafe, sin fallos, errores ni omitidas. Incluye 44 pruebas nuevas de CatalogIT y 4 de ExploreCatalogUseCaseTest, las reglas ArchUnit, contrato OpenAPI y regresión administrativa.

SPRING_PROFILES_ACTIVE se retiró temporalmente en try/finally y se restauró al terminar; el valor heredado en esta sesión estaba ausente. No se cambiaron perfiles persistentes del usuario.

Rendimiento por HTTP, 10.000 cartas de la semilla oficial, 10 solicitudes de calentamiento y 20 mediciones por caso, caché deshabilitada: p95 de 9 ms para limit=100, 7 ms para tipo FUE y costo 3..5, y 7 ms para owned=true con limit=100. EXPLAIN usa pk_card y, para propias, pk_player_card, sin lectura secuencial de toda card en los planes revisados. Son medidas locales con la base de prueba; no equivalen a una prueba de carga de producción o despliegue distribuido.

Artefactos regenerables: `target/us43-verify.log`, `target/catalog-response-example.json`, `target/catalog-performance.txt`, `target/surefire-reports/` y `target/failsafe-reports/`. clean los elimina.

No se validó Redis real, una interfaz nueva, Google real ni despliegue: no forman parte de esta implementación. Las pruebas HTTP verifican los enlaces de imágenes y el resto de la suite cubre creación/almacenamiento; las fixtures del catálogo no comprueban descarga de esas imágenes.

## Prueba manual con sesión dev

Desde PowerShell en el módulo, con JDK 21 seleccionado y Docker Desktop abierto:

```powershell
# Usa la contraseña con la que se creó la base local; el volumen conserva sus credenciales.
cd "C:\Users\Aless\OneDrive\Escritorio\PokeArena\pokearena"
$env:DB_PASSWORD = "TU_PASSWORD_LOCAL"
$env:DB_USERNAME = "pokearena"
$env:DB_URL = "jdbc:postgresql://localhost:5432/pokearena"
docker compose -f docker-compose.dev.yml up -d --wait

# Habilita el endpoint de sesiones de prueba y genera el secreto obligatorio.
$env:SPRING_PROFILES_ACTIVE = "dev"
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($bytes)
.\mvnw.cmd spring-boot:run
```

En otra ventana de PowerShell, cuando el servidor haya arrancado:

```powershell
# La sesión obtiene un jugador real y usa el mecanismo existente de colección inicial.
$session = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/dev/sesiones" -ContentType "application/json" -Body '{"role":"PLAYER"}'
$headers = @{ Authorization = "Bearer $($session.accessToken)" }

# Consulta todo el catálogo y luego solo la colección, con filtros combinados.
$page = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/catalogo?limit=2" -Headers $headers
$page | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/catalogo?type=FUE&costMin=0&costMax=7&owned=true" -Headers $headers

# Sigue exactamente el cursor del servidor si hay otra página.
if ($null -ne $page.nextCursor) {
    $cursor = [Uri]::EscapeDataString($page.nextCursor)
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/catalogo?limit=2&cursor=$cursor" -Headers $headers
}
```

Sin cartas, el resultado vacío es correcto. Para obtener una colección inicial hacen falta al menos 15 cartas Comunes disponibles y activas; pueden crearse por la interfaz administrativa existente. La semilla `scripts/seed-10000-cartas.sql` también permite pruebas locales, pero sus URLs de imagen no tienen archivos y conviene usarla solo en una base de desarrollo. Después de añadir suficientes cartas, volver a pedir la sesión PLAYER reintenta la entrega inicial. No se envía playerId al catálogo.

## Pendientes fuera del alcance

La futura interfaz debe consumir este endpoint, mostrar imageLargeUrl, propiedad, copias, filtros, estados vacíos, errores y Cargar más con nextCursor. La tarea de Redis debe implementar el puerto existente con caída a base, TTL e invalidación, conservando separados los espacios y sin almacenar datos personales. No se implementaron esos cambios ni ADR, Google, despliegue o modificaciones de otras operaciones.
