# US 39: Buscar Carta

Se implementa únicamente `GET /api/v1/cards`. Contrato definido antes del endpoint: [OpenAPI](us39-openapi.yaml). No se modifican los casos de uso de Crear Carta y Crear Ubicación ni su API. La rama existente `feature/US-39-buscar-carta` se conserva; no se hace push ni merge.

## Configuración y ejecución

Requiere PostgreSQL con `unaccent` y `pg_trgm`, JDK compatible y Maven. La conexión existente usa `DB_PASSWORD`; Spring permite configurar `SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`. Usar la **primaria**, con el esquema `public`. No configurar una réplica como datasource de esta implementación.

El `pom.xml` original declara Java 25; se conserva. En este equipo se verifica con JDK 21 mediante `mvn "-Djava.version=21" clean verify`, sin cambio de dependencias. Si el editor generó clases Java 25, `clean` evita mezclar versiones. El wrapper recibido apunta a Maven 3.10.0; se utilizó Maven 3.9.9 instalado, sin modificar el wrapper.

La migración `src/main/resources/db/migration/V039__card_search.sql` se aplica una sola vez al iniciar, después de `schema.sql`, con historial en `search_schema_versions`, transacción y bloqueo asesor entre instancias. Requiere permisos DDL para esa inicialización. En una base administrada el DBA puede preinstalar las extensiones en `public`. No hay Flyway preexistente y no se incorpora un framework de migraciones para una sola versión. El historial y las futuras versiones deben integrarse con la estrategia de migraciones que adopte el equipo.

Para probar manualmente, activar `dev` y configurar un token aleatorio de al menos 32 caracteres mediante entrada oculta. Generar y conservar el token en un gestor de contraseñas, fuera del repositorio. **Las variables de entorno de dos terminales PowerShell no se comparten automáticamente**: introducir el mismo token en ambas sesiones, sin imprimirlo, incluirlo en comandos literales ni guardarlo en archivos del proyecto.

En la primera terminal:

```powershell
# Lee el token acordado sin mostrarlo ni incorporarlo al historial como texto literal.
$token = Read-Host 'Token administrador del gestor de contraseñas' -AsSecureString
$env:SEARCH_TEST_ADMIN_TOKEN = [Net.NetworkCredential]::new('', $token).Password
$env:SPRING_PROFILES_ACTIVE = 'dev'
# Usa el JDK elegido y las variables de conexión configuradas en esta terminal.
mvn '-Djava.version=21' spring-boot:run
```

En la segunda terminal, introducir exactamente el mismo token. No generar otro diferente:

```powershell
# Configura explícitamente la credencial de esta sesión; no se hereda de la primera terminal.
$token = Read-Host 'El mismo token administrador del gestor de contraseñas' -AsSecureString
$env:SEARCH_TEST_ADMIN_TOKEN = [Net.NetworkCredential]::new('', $token).Password
$headers = @{ Authorization = "Bearer $env:SEARCH_TEST_ADMIN_TOKEN" }
# Busca por nombre y rango combinando criterios con AND, sin imprimir la cabecera de autorización.
$page = Invoke-RestMethod -Headers $headers -Uri 'http://localhost:8080/api/v1/cards?name=dragon&costMin=0&costMax=5&pageSize=50'
# Conserva filtros y tamaño al continuar el recorrido.
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
| Seguridad | La identidad se comprueba una sola vez en aplicación antes de validar incluso parámetros repetidos: 401 sin identidad válida y 403 sin ADMIN; solo un administrador recibe 400 por repetición. `Cache-Control: no-store`; 401 incluye `WWW-Authenticate: Bearer`; 503 incluye `Retry-After: 1`. |

### Recorrido estable y recursos

Primero se ejecuta una lectura MVCC acotada a `pageSize + 1` filas (máximo 101), con un máximo de 16 KiB por payload comprobado en PostgreSQL antes de enviarlo a la aplicación. Si el conjunto está vacío o cabe en la página, se responde directamente sin escribir cabeceras, resultados ni cursores. Estas respuestas funcionan incluso con veinte recorridos ocupados o con el bloqueo de admisión retenido; conservan los límites de tamaño y tiempo. `expiresAt` permanece por compatibilidad en el sobre de respuesta, pero sin cursor no representa un estado recuperable.

Si hace falta paginar, una sentencia `INSERT ... SELECT` copia **todos los resultados y sus metadatos** bajo una instantánea MVCC de PostgreSQL. El instante consultado es el inicio de esa sentencia, después del sondeo y de admitir el recorrido. El sondeo no fija el conjunto de páginas: una inserción confirmada entre ambas sentencias puede entrar en la materialización, pero ninguna confirmada después cambia el recorrido. Se ordena por código único con colación `C` y se asigna una posición persistida. No se usa OFFSET sobre el catálogo ni un máximo identificador/timestamp como frontera.

Una transacción que insertó una carta antes de la búsqueda y confirma después no era visible en la sentencia de materialización y queda fuera de todas las páginas de ese recorrido. Una búsqueda nueva sí la ve. Las ediciones posteriores de habilidades tampoco cambian páginas ya congeladas. No hay que modificar Crear Carta para obtener esta garantía. Las búsquedas se leen siempre desde la primaria; no interviene replicación con retraso.

La instantánea caduca a los 10 minutos de admisión sin renovación. Cada instancia intenta limpiar vencidas cada 60 segundos y antes de admitir otro recorrido paginado. Si el servicio se detiene, los datos vencidos se limpian al volver a atender búsquedas o mediante la tarea periódica. Hay un máximo global de 20 recorridos retenidos, 20000 cartas por búsqueda y 64 MiB de payload activo; cada resultado admite hasta 16 KiB. El exceso produce 503 y rollback, nunca truncamiento silencioso. Estos límites se refieren al contenido lógico; índices, WAL y espacio recuperable por autovacuum son adicionales.

Las respuestas iniciales completas liberan recursos al no persistir nada. Un recorrido que ya emitió cursores **se conserva hasta caducar aunque se entregue su última página**: permite reintentar cualquier cursor emitido sin perder el resultado estable. Por ello un recorrido completado sigue ocupando capacidad durante su TTL. No se aumentaron límites ni se descartan recorridos vigentes para admitir otros.

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

La opción recomendada es el script de clúster temporal. Crea `pokearena_us39_test` y otra base temporal, `pokearena_us39_rejected`, exclusivamente para probar el rechazo de una URL engañosa. Antes de cargar el esquema previo, el script consulta `current_database()` y exige el nombre autorizado.

En Java, `DisposableDatabaseInitializer` se registra antes de crear los beans y envuelve el DataSource de **ambos tests de contexto**. Comprueba `SELECT current_database()` en cada conexión antes de entregarla a Spring o a los fixtures. Solo autoriza el nombre exacto `pokearena_us39_test`; la URL y sus parámetros no deciden esta autorización. Así se bloquea también `schema.sql` y la migración, antes de `@BeforeEach`. Las conexiones de concurrencia usan ese mismo DataSource y sus credenciales configuradas.

Si se usa una base desechable ya preparada, su nombre real debe ser exactamente `pokearena_us39_test`; configurar `POKEARENA_TEST_DATABASE_URL`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y la contraseña correspondiente. Nunca usar una base de trabajo. Sin `POKEARENA_TEST_DATABASE_URL`, las pruebas de catálogo PostgreSQL se omiten explícitamente. Las pruebas de rechazo requieren `POKEARENA_REJECTED_DATABASE_URL`, que el script configura hacia su segunda base temporal; no apuntarla a una base de trabajo. Las unitarias se pueden ejecutar con `mvn "-Djava.version=21" "-Dtest=CardSearchValidationTests,CardSearchArchitectureTests" test`.

La suite cubre cada filtro, AND, tildes, mayúsculas, texto literal, límites, rangos, datos de habilidad, errores HTTP, cursor/sujeto/filtros/tamaño/caducidad, recorrido sin omisiones, transacciones con confirmación tardía, límites de capacidad, limpieza en cascada, migración repetible y 10005 cartas. Las fronteras de dominio/aplicación se comprueban sobre imports. Los fixtures `TEST-*` solo se cargan en pruebas.

Los resultados observados y los archivos incorporados se registran en [verificación](us39-verificacion.md). Los diagramas editables y su declaración de IA están en [diagramas](us39-diagramas.md).
