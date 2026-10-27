# US 39: Buscar Carta

Se implementa únicamente `GET /api/v1/cards`. Contrato definido antes del endpoint: [OpenAPI](us39-openapi.yaml). No se modifican los casos de uso de Crear Carta y Crear Ubicación ni su API. La rama existente `feature/US-39-buscar-carta` se conserva; no se hace push ni merge.

## Configuración y ejecución

Requiere PostgreSQL con `unaccent` y `pg_trgm`, JDK compatible y Maven. La conexión existente usa `DB_PASSWORD`; Spring permite configurar `SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`. Usar la **primaria**, con el esquema `public`. No configurar una réplica como datasource de esta implementación.

El `pom.xml` original declara Java 25; se conserva. En este equipo se verifica con JDK 21 mediante `mvn "-Djava.version=21" clean verify`, sin cambio de dependencias. Si el editor generó clases Java 25, `clean` evita mezclar versiones. El wrapper recibido apunta a Maven 3.10.0; se utilizó Maven 3.9.9 instalado, sin modificar el wrapper.

La migración `src/main/resources/db/migration/V039__card_search.sql` se aplica una sola vez al iniciar, después de `schema.sql`, con historial en `search_schema_versions`, transacción y bloqueo asesor entre instancias. Requiere permisos DDL para esa inicialización. En una base administrada el DBA puede preinstalar las extensiones en `public`. No hay Flyway preexistente y no se incorpora un framework de migraciones para una sola versión. El historial y las futuras versiones deben integrarse con la estrategia de migraciones que adopte el equipo.

Para probar manualmente, activar `SPRING_PROFILES_ACTIVE=dev` y configurar `SEARCH_TEST_ADMIN_TOKEN` y, opcionalmente, `SEARCH_TEST_USER_TOKEN` con valores aleatorios diferentes de al menos 32 caracteres, suministrados externamente. Por ejemplo, en PowerShell se pueden generar para la sesión con `[guid]::NewGuid().ToString()`; no guardarlos en el repositorio ni registrarlos en logs.

```powershell
# Genera credenciales efímeras para el adaptador de desarrollo.
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SEARCH_TEST_ADMIN_TOKEN = [guid]::NewGuid().ToString()
$env:SEARCH_TEST_USER_TOKEN = [guid]::NewGuid().ToString()
# Usa el JDK elegido a través de JAVA_HOME y las variables de conexión existentes.
mvn '-Djava.version=21' spring-boot:run
```

Ejemplos desde otra terminal con la credencial configurada en su entorno:

```powershell
# Busca por nombre y rango, combinando todos los criterios con AND.
$headers = @{ Authorization = "Bearer $env:SEARCH_TEST_ADMIN_TOKEN" }
$page = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:8080/api/v1/cards?name=dragon&costMin=0&costMax=5&pageSize=50'
# Repite filtros y tamaño; sustituye el cursor con el recibido en la página anterior.
if ($page.nextCursor) {
    $uri = "http://localhost:8080/api/v1/cards?name=dragon&costMin=0&costMax=5&pageSize=50&cursor=$($page.nextCursor)"
    $next = Invoke-RestMethod -Headers $headers -Uri $uri
}
```

Consultas adicionales: `?code=PKA-FUE-001`, `?abilityCode=HAB-000`, `?cost=0&power=-20`, `?type=AGU&rarity=RARE`, `?abilityTrigger=TEST-REVEAL&abilityEffect=TEST-BOOST` (estos dos identificadores existen exclusivamente en pruebas). Sin parámetros recorre el catálogo completo. Para enviar `%`, `_`, `!`, tildes u otros caracteres, usar codificación URL.

## Reglas y decisiones

| Decisión | Comportamiento |
| --- | --- |
| Coincidencia | Código de carta y habilidad exactos, sensibles a mayúsculas. Nombre parcial literal normalizado con `unaccent` y `lower`; se escapa LIKE después de normalizar. |
| Combinación | Todos los filtros se unen con AND. Un disparador y efecto deben pertenecer a la misma habilidad asociada a la carta. Combinaciones incompatibles devuelven lista vacía. |
| Sin Habilidad | `abilityCode=HAB-000`. Se presenta con nombre `Sin Habilidad`, disparador/efecto null y parámetros `{}`, conservando el nombre almacenado existente. Con disparador/efecto no devuelve resultados. |
| Números | Costo 0..10; poder -20..20; límites incluidos. Se admiten rangos abiertos o cerrados. Un exacto junto con cualquier extremo del mismo campo es error 400. |
| Parámetros | Nombre hasta 100 caracteres; códigos hasta 50; disparador/efecto hasta 100. No se aceptan filtros vacíos, desconocidos ni repetidos. Tipos y rarezas reutilizan los enums existentes. |
| Habilidad inexistente | Un código, disparador o efecto desconocido no coincide y devuelve 200 vacío, ya que no hay catálogo oficial de disparadores/efectos. |
| Página | 50 por defecto, mínimo 1, máximo 100. `total` describe el conjunto congelado; `nextCursor=null` indica fin. |
| Cursor | UUID aleatorio opaco almacenado por página, ligado al sujeto, filtros y tamaño. Deben repetirse los mismos valores de filtros, incluidas mayúsculas y tildes, aunque puedan ser semánticamente equivalentes. Cambiarlo: 400; otro sujeto: 403; UUID mal formado: 400; token desconocido o expirado: 410. |
| Seguridad | 401 sin identidad válida y 403 sin ADMIN. `Cache-Control: no-store`; 401 incluye `WWW-Authenticate: Bearer`; 503 incluye `Retry-After: 1`. |

### Recorrido estable y recursos

Una sentencia `INSERT ... SELECT` copia **todos los resultados y sus metadatos** bajo una instantánea MVCC de PostgreSQL. El instante consultado es el inicio de esa sentencia, después de admitir la búsqueda. Se ordena por código único con colación `C` y se asigna una posición persistida. No se usa OFFSET sobre el catálogo ni un máximo identificador/timestamp como frontera.

Una transacción que insertó una carta antes de la búsqueda y confirma después no era visible en la sentencia de materialización y queda fuera de todas las páginas de ese recorrido. Una búsqueda nueva sí la ve. Las ediciones posteriores de habilidades tampoco cambian páginas ya congeladas. No hay que modificar Crear Carta para obtener esta garantía. Las búsquedas se leen siempre desde la primaria; no interviene replicación con retraso.

La instantánea caduca a los 10 minutos de admisión sin renovación. Cada instancia intenta limpiar vencidas cada 60 segundos y antes de admitir otra búsqueda. Si el servicio se detiene, los datos vencidos se limpian al volver a atender búsquedas o mediante la tarea periódica. Hay un máximo global de 20 búsquedas activas, 20000 cartas por búsqueda y 64 MiB de payload activo; cada resultado admite hasta 16 KiB. El exceso produce 503 y rollback, nunca truncamiento silencioso. Estos límites se refieren al contenido lógico; índices, WAL y espacio recuperable por autovacuum son adicionales.

Un bloqueo asesor transaccional global serializa la admisión y el cómputo de capacidad. Se intenta sin esperar: la contención devuelve 503. Continuar un recorrido no toma ese bloqueo. Los tokens de página tienen unicidad por búsqueda y posición para que reintentos no creen filas ilimitadas. No hay estado de búsqueda en memoria local ni servicios nuevos.

Cada sentencia tiene timeout de 5 s y la transacción un presupuesto de 6 s. La página incluye como máximo 100 resultados de 16 KiB más el sobre JSON. Objetivo propio de esta US: **p95 < 1000 ms por página con 10000 cartas en una máquina local sin contención**, sin atribuirle el RNF de partidas de 200 ms. La prueba mide búsqueda inicial por HTTP y recorre las páginas; no constituye certificación de carga de producción.

Índices: GIN de nombre normalizado para búsquedas selectivas de 3 o más caracteres, B-tree de costo, poder, tipo/rareza y habilidad; disparador/efecto en habilidades. La clave primaria existente cubre el código. Para términos cortos o consultas sin filtro, PostgreSQL puede preferir un escaneo de las aproximadamente 10000 filas, acotado por capacidad y timeout. No se deben cambiar el diccionario `unaccent` o la función de normalización sin reconstruir el índice GIN en una nueva migración.

## Ajustes compartidos y pendientes

Se agregan a `abilities` solo `trigger_code`, `effect_code` y `parameters` JSONB de tipo objeto, mediante ALTER TABLE versionado, preservando filas existentes. Metadatos previos desconocidos conservan null y `{}`; no se les asigna una semántica inventada. Se deben acordar los identificadores oficiales y el esquema de parámetros con el responsable de habilidades y completar esos datos. No hay creación de habilidades ni motor de juego.

`application.properties` incorpora únicamente referencias a variables externas para credenciales de simulación. El resto de cambios son clases y recursos nuevos de búsqueda. No se alteran Card, CardRepository, ImageService ni Crear Carta/Ubicación.

La interfaz `SearchIdentity` permite integrar un proveedor real. Sin proveedor, no hay acceso por defecto. El adaptador de tokens solo se construye con `dev` o `test`, y se deshabilita si también están `prod` o `production`. **No equivale a autenticación de producción**: el despliegue debe excluir los perfiles dev/test y el equipo de autenticación debe implementar identidad, validación de credenciales y asignación de ADMIN en el servidor. No se aceptan roles libres del cliente.

Las referencias y el estado de imagen se devuelven tal como existen. Actualmente pueden ser rutas locales como `./uploads/...`: el navegador no tiene garantizado acceso HTTP. Servir esas imágenes sigue siendo integración pendiente; esta US no las carga, transforma ni reintenta.

## Pruebas reproducibles

En Windows con PostgreSQL instalado:

```powershell
# Ejecuta en un clúster nuevo y aislado, inicia PostgreSQL solo en loopback y lo detiene al finalizar.
./scripts/test-us39.ps1 -PostgresBin 'C:/Program Files/PostgreSQL/18/bin' -JavaHome $env:JAVA_HOME -MavenCommand 'mvn'
```

El script prepara el esquema anterior y una habilidad legada antes de iniciar Spring; usa `clean verify`, también ejecuta el test de contexto preexistente y restaura las variables del proceso. Los registros y el clúster detenido se conservan en la ruta temporal indicada para diagnóstico. No usa Docker, una base externa real, cuentas de Google ni servicios ajenos al proceso local. La confianza sin contraseña se limita al clúster temporal de loopback.

Para una base de prueba ya preparada, configurar `POKEARENA_TEST_DATABASE_URL` con un nombre terminado en `_test`, `SPRING_DATASOURCE_URL`, usuario y contraseña de pruebas antes de ejecutar Maven. La prueba de concurrencia usa el usuario `postgres` sin contraseña, como el clúster del script; para otro mecanismo adaptar esas credenciales de la prueba. Nunca usar una base de trabajo: las pruebas truncan cartas y tablas temporales. Sin la variable, la suite PostgreSQL se omite explícitamente; las unitarias pueden ejecutarse con `mvn "-Djava.version=21" "-Dtest=CardSearchValidationTests,CardSearchArchitectureTests" test`.

La suite cubre cada filtro, AND, tildes, mayúsculas, texto literal, límites, rangos, datos de habilidad, errores HTTP, cursor/sujeto/filtros/tamaño/caducidad, recorrido sin omisiones, transacciones con confirmación tardía, límites de capacidad, limpieza en cascada, migración repetible y 10005 cartas. Las fronteras de dominio/aplicación se comprueban sobre imports. Los fixtures `TEST-*` solo se cargan en pruebas.

Los resultados observados y los archivos incorporados se registran en [verificación](us39-verificacion.md). Los diagramas editables y su declaración de IA están en [diagramas](us39-diagramas.md).
