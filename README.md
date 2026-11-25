# PokéArena

Proyecto PokéArena del curso CE-1116 (TEC): un backend en **Java 21 con Spring Boot**,
PostgreSQL, Redis (caché opcional) y una interfaz web servida por la misma aplicación. La especificación completa,
fuente de verdad del proyecto, está en
[pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md](pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md).

## Historias implementadas

| Historia | Operación | Interfaz | Estado |
|---|---|---|---|
| 2.1.1 Crear carta | `POST /api/v1/cartas` (multipart, administrador) | Pestaña "Crear carta" | Sprint 1, terminada |
| 2.2.1 Crear ubicación | `POST /api/v1/ubicaciones` (multipart, administrador) | Pestaña "Crear ubicación" | Sprint 1, terminada |
| 2.1.2 Buscar carta | `GET /api/v1/cartas` (administrador) | Pestaña "Buscar carta" | Sprint 1, terminada |
| Reintentos de miniatura (C.1 y C.2) | Tarea programada `ThumbnailRetryJob` | Estado de imagen en la ficha y en la búsqueda | Sprint 1, terminada |
| Imágenes de cartas y ubicaciones | `GET /images/{carpeta}/{id}/{archivo}` (pública) | Vistas previas y miniaturas | Sprint 1, terminada |
| 1.1.1 Inicio de sesión con proveedor | `POST /api/v1/sesiones` (pública) y `GET /api/v1/configuracion` (client id de Google, pública) | Botón "Entrar con Google" en la barra de sesión | Sprint 2, terminada |
| 3.1.1 Recibir colección inicial | Durante `POST /api/v1/sesiones` (campo `initialCollection`) y, en el perfil `dev`, para el jugador de prueba | Bienvenida con el estado de la colección; caso rápido "Segundo ingreso del jugador" | Sprint 2, terminada |
| 3.1.2 Explorar el catálogo y mi colección | `GET /api/v1/catalogo` (jugador o administrador) | Pestaña "Catálogo y mi colección" | Sprint 2, terminada |
| Opciones de la interfaz (Refactory SP2) | `GET /api/v1/opciones/catalogo` (jugador o administrador) y `GET /api/v1/opciones/creacion` (administrador) | Tipos, rarezas, rangos, habilidades, energía extra, efectos y requisitos de imagen de filtros y formularios | Terminada |
| 3.1.3 Crear Deck | `POST /api/v1/decks`, `GET /api/v1/decks`, `GET /api/v1/decks/{id}` y `GET /api/v1/opciones/juego` (jugador o administrador) | Pestañas "Armar deck" y "Mis decks" | Sprint 3, terminada (ver [Crear Deck](#crear-deck-sprint-3-historia-313)) |
| 4.1.2 Buscar partida | `POST /api/v1/busquedas`, `GET` y `DELETE /api/v1/busquedas/{id}`, `POST /api/v1/busquedas/{id}/bot` | Pestañas "Buscar partida" y "Estado de búsqueda" | Sprint 3, implementada |
| 5.1.1 Iniciar partida | Preparación transaccional desde la búsqueda o el bot; `GET /api/v1/partidas/{id}` | Pestaña "Inicio de partida", abre al encontrar rival | Sprint 3, implementada (ver [Probar búsqueda e inicio de partida](#probar-búsqueda-e-inicio-de-partida-desde-la-interfaz)) |

Para desarrollo y demostraciones, el perfil `dev` ofrece además credenciales de prueba
(`POST /api/v1/dev/sesiones`), que no existen con ningún otro perfil.

El contrato HTTP está en
[pokearena/src/main/resources/openapi/pokearena.yaml](pokearena/src/main/resources/openapi/pokearena.yaml),
y una prueba de contrato lo compara con la aplicación en los dos sentidos.

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae su propio wrapper (`mvnw.cmd`).
- **Docker Desktop** encendido: lo usan las pruebas de integración (Testcontainers) y la base local.
- **Node.js 20 o superior (opcional)**, en `PATH`: las pruebas de comportamiento de la interfaz
  (`src/test/javascript/player.test.mjs` y `options.test.mjs`) usan `node:test`, sin paquetes npm, y
  Maven las ejecuta con `PlayerInterfaceBehaviorTest`. Sin Node, o con una versión menor, esa prueba se
  **omite** con un aviso ("Pruebas de JavaScript OMITIDAS") y el build sigue. Comprueba la
  instalación con `node --version`, o ejecútalas solas con
  `node --test src/test/javascript/player.test.mjs src/test/javascript/options.test.mjs`.
- Git y PowerShell (los comandos de este documento son de PowerShell, en Windows).

## Compilar y ejecutar las pruebas

```powershell
git clone https://github.com/a-alfaro19/PokeArena.git
cd PokeArena\pokearena
.\mvnw.cmd clean verify
```

`.\mvnw.cmd clean verify` compila el proyecto y ejecuta todas las pruebas, que deben terminar en
**BUILD SUCCESS**:

- **Unitarias y de arquitectura** (`*Test`): dominio y casos de uso sin Spring, y las reglas de
  dependencia entre capas con ArchUnit.
- **De integración** (`*IT`): cada historia de punta a punta contra un PostgreSQL real que
  Testcontainers levanta en un contenedor propio. No necesitan la base local de abajo, pero sí
  Docker Desktop encendido.

**Las pruebas de integración deben ejecutarse en secuencia.** Todas comparten el mismo contenedor
de PostgreSQL, y varias vacían el catálogo (`DELETE FROM card`) para partir de datos conocidos
(por ejemplo `CardSearchIT`, `DeliverInitialCollectionIT` y las pruebas de rendimiento). Si
corrieran a la vez, una borraría las cartas que otra está usando. Es el comportamiento por defecto
de Maven: **no actives la ejecución en paralelo** (`parallel` o `forkCount` mayor que 1 en Failsafe,
ni `junit.jupiter.execution.parallel.enabled`).

Para ejecutar solo una clase de prueba:

```powershell
.\mvnw.cmd test -Dtest=CardSearchRulesTest                     # una prueba unitaria
.\mvnw.cmd verify -Dit.test=CardSearchIT -Dtest=NoTest -Dsurefire.failIfNoSpecifiedTests=false
```

En la salida de las pruebas aparecen dos "APPLICATION FAILED TO START" y un aviso de una versión
grande faltante: son esperados, porque `JwtSecretStartupIT` y `ThumbnailRetryIT` provocan esas
fallas a propósito.

## Integración continua (GitHub Actions)

`.github/workflows/ci.yml` define dos trabajos:

- **Compilar y probar** (en cada push y pull request): Java 21 (Temurin), caché de Maven y
  `./mvnw -B clean verify` en `pokearena/`. Los runners `ubuntu-latest` traen Docker, que usan
  Testcontainers (PostgreSQL y Redis), y Node.js, que usan las pruebas de JavaScript. Si falla,
  sube los informes de Surefire y Failsafe como artefacto (`informes-de-pruebas`).
- **Contrato OpenAPI sin cambios incompatibles** (solo en pull requests): compara
  `pokearena.yaml` de la rama con el de la rama base usando `oasdiff breaking` (imagen Docker
  oficial `tufin/oasdiff`) y falla ante un cambio incompatible, por ejemplo quitar una operación.
  Si el contrato no existe en la rama base, el paso se omite.

Estado: el flujo está escrito y el paso de `oasdiff` se probó localmente con Docker (sin cambios
incompatibles contra `main`, y falla si se quita una operación), pero **todavía no se ha ejecutado
en GitHub Actions**: se ejecutará en el primer push o pull request.

## Levantar todo con Docker Compose (un comando)

`pokearena/docker-compose.yml` levanta el sistema completo (sección 12 de la especificación):
PostgreSQL 17 (con healthcheck y volumen), Redis (con `maxmemory` y `allkeys-lru`), **dos
instancias de la API** (`api1` y `api2`, sin estado, que comparten el volumen de imágenes) y
**Nginx** como balanceador en `http://localhost:8080`. La imagen de la API se construye con el
`Dockerfile` (compila con el wrapper de Maven; no lleva secretos).

Una sola vez, desde la carpeta `pokearena`, crea tu `.env` a partir de la plantilla y completa
`DB_PASSWORD` y `APP_JWT_SECRET` (`.env` no se sube al repositorio):

```powershell
Copy-Item .env.example .env
notepad .env          # DB_PASSWORD, APP_JWT_SECRET y, si quieres, APP_GOOGLE_CLIENT_ID y SPRING_PROFILES_ACTIVE=dev
```

Después, **un solo comando**:

```powershell
docker compose up --build
```

Y abre `http://localhost:8080`. Con `SPRING_PROFILES_ACTIVE=dev` en `.env` aparecen las sesiones
de prueba. Cada respuesta trae el encabezado `X-Upstream-Instance` con la instancia que la
atendió, para comprobar el balanceo:

```powershell
1..4 | ForEach-Object { (Invoke-WebRequest http://localhost:8080/api/v1/configuracion).Headers["X-Upstream-Instance"] }
```

Para detener: `docker compose down` (con `-v` además se borran la base, las imágenes y la caché).
Nginx acepta cuerpos de hasta 8 MB para que una imagen de más de 5 MB la rechace la API con su
error `IMAGE_TOO_LARGE` y no Nginx. Para desarrollo con solo PostgreSQL se conserva
`docker-compose.dev.yml` (sección siguiente).

## Ejecutar la aplicación y abrir la interfaz

Desde la carpeta `pokearena`, en una misma ventana de PowerShell (las variables de `$env:` duran
solo en esa ventana):

```powershell
# 1. Base de datos local: solo PostgreSQL, con la misma imagen que las pruebas.
#    La contraseña no se guarda en el repositorio: elige la que quieras.
$env:DB_PASSWORD = "<contraseña local>"
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps        # espera a que diga "healthy"

# 2. Conexión de la aplicación. Es el valor por defecto; defínela si cambias el puerto o la base.
$env:DB_URL = "jdbc:postgresql://localhost:5432/pokearena"

# 3. Secreto de firma de las credenciales: obligatorio (sin él la aplicación no arranca),
#    de al menos 32 bytes y nunca en el repositorio. Esto genera uno aleatorio de 48 bytes.
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($b)

# 4. Perfil dev: logs en texto legible y sesiones de prueba para la interfaz.
$env:SPRING_PROFILES_ACTIVE = "dev"

# 5. Arrancar y abrir la interfaz.
.\mvnw.cmd spring-boot:run
Start-Process "http://localhost:8080"            # en otra ventana, o después de que arranque
```

Flyway crea el esquema y los datos semilla (tipos, rarezas y la habilidad `HAB-000`) al arrancar.

**Si el puerto 5432 está ocupado** (por ejemplo, por un PostgreSQL instalado en Windows), publica
la base en otro puerto y apunta la aplicación a él:

```powershell
$env:DB_PORT = "55432"
docker compose -f docker-compose.dev.yml up -d
$env:DB_URL = "jdbc:postgresql://localhost:55432/pokearena"
```

**Otras variables:** `DB_USERNAME` (por defecto `pokearena`), `APP_IMAGES_ROOT_DIR` (carpeta de
las imágenes, por defecto `./data/images`), `APP_ADMIN_IDENTITIES` (ver abajo) y las de Redis (ver
"Caché del catálogo con Redis").

### Caché del catálogo con Redis (opcional)

Las páginas del catálogo del jugador y de la búsqueda del administrador se cachean en Redis
(sección 9 de la especificación), **sin datos del jugador**: la propiedad y las copias se leen
siempre de la base. Redis es opcional: **sin `REDIS_HOST` la caché queda desactivada** y la
aplicación consulta siempre la base. Para probarla en desarrollo:

```powershell
docker run -d --name pokearena-redis -p 6379:6379 redis:7.4-alpine
$env:REDIS_HOST = "localhost"        # REDIS_PORT es 6379 por defecto
```

- Clave de cada página: `catalog:v<versión>:<espacio>:<hash de filtros>:<cursor>:<limit>`, con
  vencimiento `app.cache.catalog-ttl` (30 s). `<espacio>` separa `player-catalog` de `admin-search`.
- Crear carta incrementa `catalog:version` después de confirmar: la carta aparece enseguida.
- Si Redis falla o no responde, **nunca es un error**: se ignora la caché y se consulta la base.
  Los tiempos de espera son cortos (`REDIS_TIMEOUT` y `REDIS_CONNECT_TIMEOUT`, 250 ms) y, tras una
  falla, la caché se pausa `app.cache.failure-backoff` (5 s).
- Las pruebas de Redis (`RedisCatalogCacheIT` y `CatalogCacheRedisIT`) levantan su propio Redis con
  Testcontainers; las demás pruebas corren sin Redis.

### Inicio de sesión con Google (`APP_GOOGLE_CLIENT_ID` y `APP_ADMIN_IDENTITIES`)

`POST /api/v1/sesiones` recibe `{ "provider": "google", "idToken": "..." }` y responde con las
credenciales propias de PokéArena. El servidor verifica la firma del token con las llaves públicas
de Google, el emisor, el vencimiento y que la **audiencia** sea el `client id` de PokéArena.

1. **Client id.** Créalo en Google Cloud Console (APIs y servicios → Credenciales → ID de cliente de
   OAuth, tipo "Aplicación web", con `http://localhost:8080` como origen autorizado). No es un
   secreto, pero no se guarda en el repositorio porque depende del proyecto de cada equipo:

   ```powershell
   $env:APP_GOOGLE_CLIENT_ID = "1234567890-abc123.apps.googleusercontent.com"
   ```

   Sin esta variable la aplicación arranca igual, pero Google queda deshabilitado:
   `POST /api/v1/sesiones` con `"provider": "google"` responde 400 `UNSUPPORTED_PROVIDER`, y la
   interfaz deja el botón "Entrar con Google" deshabilitado con el aviso "falta
   APP_GOOGLE_CLIENT_ID" (en desarrollo se puede seguir usando el perfil `dev`).

   La interfaz lee el `client id` al cargar de `GET /api/v1/configuracion` (pública), que responde
   solo `{ "googleClientId": "<id>" }` o `{ "googleClientId": null }`. Así la interfaz y el
   verificador de la audiencia usan siempre el mismo valor.

2. **Administradores.** El rol `ADMIN` se asigna por configuración: una lista de identidades
   `proveedor:sub`, separadas por comas. El `sub` es el identificador de la cuenta en Google (el
   claim `sub` del token, un número largo), **no el correo**. El rol se recalcula en cada ingreso:
   quien entra o sale de la lista cambia de rol en su siguiente inicio de sesión, sin perder su cuenta.

   ```powershell
   $env:APP_ADMIN_IDENTITIES = "google:109876543210987654321,google:112233445566778899001"
   ```

   Para conocer tu `sub`: inicia sesión una vez y búscalo en la base
   (`SELECT provider_subject FROM player_identity WHERE provider = 'google';`), agrégalo a la
   variable, reinicia la aplicación y vuelve a iniciar sesión. Una entrada sin el formato
   `proveedor:sub` detiene el arranque con un mensaje claro.

3. **Probar el inicio de sesión real.** Con `APP_GOOGLE_CLIENT_ID` definida, arranca la aplicación
   (con o sin el perfil `dev`) y abre `http://localhost:8080` **con ese mismo origen** (no con
   `127.0.0.1` ni otro puerto: Google solo acepta los orígenes autorizados del client id). Pulsa
   "Entrar con Google" y elige tu cuenta. La barra muestra tu nombre y el rol, y la bienvenida el
   estado de tu colección inicial (`DELIVERED` en el primer ingreso si hay suficientes cartas
   Comunes; si no, el mensaje de pendiente del servidor). Para comprobar `ALREADY_DELIVERED`, cierra
   la sesión y vuelve a entrar. El token de Google solo viaja a `POST /api/v1/sesiones`; el resto de
   las solicitudes usa las credenciales propias, que viven solo en memoria.

Valores opcionales (en `application.properties`): `APP_GOOGLE_JWKS_URI` (URL de las llaves
públicas; las pruebas la apuntan a un servidor local), `app.identity.google.jwks-timeout` y
`app.identity.google.jwks-default-ttl` y `app.identity.google.jwks-min-reload-interval` (por defecto
1 minuto: un token con un `kid` desconocido solo fuerza otra descarga de las llaves si pasó ese
tiempo, para que nadie pueda provocar descargas ilimitadas a Google). Si Google no responde al pedir
sus llaves, el inicio de sesión responde 503 `IDENTITY_PROVIDER_UNAVAILABLE` y no crea ninguna cuenta.

**Detener la base:** `docker compose -f docker-compose.dev.yml down`. Con `-v` además se borran
sus datos.

**Datos de rendimiento:** `scripts/seed-10000-cartas.sql` carga 10.000 cartas válidas para medir
la búsqueda (sus imágenes no existen; la interfaz muestra una imagen de reemplazo):

```powershell
Get-Content scripts\seed-10000-cartas.sql | docker compose -f docker-compose.dev.yml exec -T postgres psql -U pokearena -d pokearena
```

### Perfil `dev`

Por defecto los logs salen en JSON estructurado, con el `correlationId` de cada solicitud. El
perfil `dev` (`application-dev.properties`) los muestra como texto legible y habilita
`POST /api/v1/dev/sesiones`, que entrega credenciales de prueba de un administrador o de un
jugador (cada rol es siempre el mismo jugador real, con proveedor `dev`). Sin ese perfil el
endpoint no existe y responde 404. Para usarlo sin la interfaz:

```powershell
$s = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/dev/sesiones `
  -ContentType "application/json" -Body '{"role":"ADMIN"}'
$headers = @{ Authorization = "Bearer $($s.accessToken)" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/cartas?limit=5" -Headers $headers
```

Para volver a los logs en JSON y quitar las sesiones de prueba: `Remove-Item Env:SPRING_PROFILES_ACTIVE`.

### Interfaz web

La interfaz (sección 11 de la especificación) es una sola página en
`pokearena/src/main/resources/static/` (`index.html`, `app.js`, `player.js`, `options.js`, `deck.js`,
`match-search.js`, `match-view.js` y `styles.css`), sin
frameworks ni bibliotecas externas, que habla con la API real en el mismo origen. La única
excepción es el script oficial de Google Identity Services, que la política de seguridad de
contenido permite solo desde `accounts.google.com/gsi/`.

- **Barra de sesión:** "Entrar con Google" (habilitado solo si el servidor tiene
  `APP_GOOGLE_CLIENT_ID`, ver arriba) y, con el perfil `dev`, "Sin sesión", "Jugador" y
  "Administrador", que piden un token a `POST /api/v1/dev/sesiones`. El token vive **solo en
  memoria**: al recargar la página se vuelve a "Sin sesión". Sin el perfil `dev`, la interfaz oculta
  los botones de prueba y muestra el aviso "Las sesiones de prueba no están disponibles en este
  ambiente."
- **Bienvenida y catálogo del jugador (historias 1.1.1, 3.1.1 y 3.1.2):** la bienvenida muestra el
  estado de la colección inicial que informa el servidor (`initialCollection`, también en las
  sesiones de prueba). La pestaña "Catálogo y mi colección" filtra por tipo y costo, con la casilla
  "Solo mis cartas", la imagen grande, las copias y "Cargar más". Sus casos rápidos incluyen tipo
  inexistente, límite 0, token de Google inválido y "Segundo ingreso del jugador", que vuelve a pedir
  la sesión del jugador de prueba y confirma `ALREADY_DELIVERED`.
- **Opciones desde la API:** la interfaz no tiene escritos tipos, rarezas, rangos, habilidades,
  modalidades de energía, efectos ni requisitos de imagen. Después de iniciar sesión pide
  `GET /api/v1/opciones/catalogo` y, como administrador, `GET /api/v1/opciones/creacion`; si la carga
  falla, muestra el error y deshabilita los formularios de creación, sin valores de respaldo.
- **Crear carta, Crear ubicación y Buscar carta:** formularios con todos los campos. La habilidad se
  elige en una lista de las habilidades activas. "Generar imagen" crea con `canvas` una imagen válida
  con la resolución mínima que informa el servidor. La ubicación arma los campos y el JSON de `effect`
  con la descripción de parámetros de cada efecto, y muestra seleccionada la modalidad de energía por
  defecto que informa el servidor (`NONE`). La búsqueda muestra una tabla con miniatura y estado de
  imagen, y "Cargar más" pide la página siguiente con el cursor.
- **Idempotencia:** cada creación genera su `Idempotency-Key` en el navegador; "Repetir con la
  misma clave" reenvía la última creación con la misma clave, el mismo contenido y la misma sesión,
  y debe devolver la misma respuesta sin crear nada nuevo.
- **Panel de respuesta:** método y ruta, código HTTP con color (verde 2xx, naranja 4xx, rojo 5xx),
  el JSON completo, el `correlationId`, la `Idempotency-Key` y la vista previa de las imágenes, con
  una imagen de reemplazo si alguna no carga.
- **Casos de prueba rápidos:** un botón por cada caso del Sprint 1 de la sección 11 (carta válida,
  costo 11, sin habilidad, imagen con proporción incorrecta, sin sesión, como jugador, ubicación
  válida, efecto inexistente, rango invertido, ...). Cada uno pide su propia sesión de prueba,
  genera sus imágenes y compara la respuesta con lo esperado ("✓ Coincide" o "✗ Se esperaba ...").
- **Buscar partida e inicio:** el estado consulta según el intervalo indicado por la API; cuando llega a
  `MATCHED`, la interfaz abre "Inicio de partida" con el `matchId` recibido. La vista consulta
  `GET /api/v1/partidas/{id}` y presenta ubicaciones, mano propia, energía, tiempo restante, apuesta y
  solo el conteo de la mano rival.

## Estructura del proyecto

```text
PokeArena
├── .github/workflows/ci.yml      integración continua (clean verify y oasdiff)
├── CLAUDE.md                     instrucciones para el agente de IA
├── README.md
└── pokearena                     proyecto Maven
    ├── docker-compose.yml        sistema completo: PostgreSQL, Redis, api1, api2 y Nginx
    ├── docker-compose.dev.yml    PostgreSQL local para desarrollo
    ├── Dockerfile, nginx.conf    imagen de la API y balanceador
    ├── .env.example              plantilla de variables (copiar a .env, que no se sube)
    ├── docs                      especificación (fuente de verdad)
    ├── scripts                   seed-10000-cartas.sql (datos de rendimiento)
    └── src
        ├── main
        │   ├── java/cr/ac/tec/pokearena
        │   └── resources
        │       ├── application.properties, application-dev.properties
        │       ├── db/migration          migraciones Flyway (V1 esquema y semillas, V2, ...)
        │       ├── openapi/pokearena.yaml contrato HTTP
        │       └── static                interfaz web (index.html, app.js, player.js, options.js, styles.css)
        └── test
            ├── java/cr/ac/tec/pokearena  pruebas (*Test unitarias, *IT de integración, support/)
            └── javascript                pruebas de la interfaz (player y opciones, node --test)
```

El código vive en `cr.ac.tec.pokearena`, organizado en capas (sección 4.1 de la especificación).
Cada paquete tiene un `package-info.java` que explica su responsabilidad y lo que puede usar.

| Capa | Paquete | Qué contiene | Puede usar |
|---|---|---|---|
| Presentación | `src/main/resources/static/` | Interfaz web: `index.html` (estructura), `app.js` (sesiones, formularios del administrador, casos rápidos y panel de respuesta), `player.js` (estado del catálogo del jugador, bienvenida y Google, sin DOM), `options.js` (lectura de las opciones de la API, sin DOM; ambos los prueban las pruebas de JavaScript) y `styles.css` | La API por HTTP |
| API | `api.controller` | `SessionController` (inicio de sesión), `CatalogController` (catálogo del jugador), `CardController`, `LocationController`, `ImageFileController`, `ConfigurationController` (client id de Google), `OptionsController` (opciones de filtros y formularios) y `DevSessionController` (solo `dev`) | `application` |
| | `api.dto` | Cuerpos HTTP: fichas, páginas y sesiones | |
| | `api.error` | `ApiErrorHandler` (único manejador global) y el formato único `ApiError` | |
| | `api.security` | `AuthorizationInterceptor`, `@RequiresRole`, `@PublicRoute` | |
| | `api.web` | `CorrelationIdFilter` y el encabezado `Idempotency-Key` | |
| Aplicación | `application.usecase` | Un caso de uso por historia, `IdempotencyGuard`, `Pagination`, `ThumbnailRetryUseCase` | `domain` |
| | `application.port` | Puertos: `UnitOfWork`, `ImageProcessor`, `ImageStorage`, `CatalogCache`, `TokenIssuer`, `Clock`, ... | |
| | `application.dto` | Comandos y resultados | |
| Dominio | `domain.model` | `Card`, `Location`, `Ability`, `Player`, criterios de búsqueda, ... | Solo `java.*` |
| | `domain.rule` | Validaciones (`CardFieldRules`, `CardSearchRules`, `EvolutionRules`, `ImageRules`, ...) | |
| | `domain.effect` | Plug-ins de efectos de ubicación y su registro | |
| | `domain.error` | `DomainException` y sus familias | |
| | `domain.repository` | Puertos de persistencia | |
| Infraestructura | `infrastructure.persistence` | Repositorios JDBC (un adaptador por puerto, con el mapeo de cartas compartido en `CardRows`) y `TransactionalUnitOfWork` | `domain` y `application` |
| | `infrastructure.storage` | Procesador y almacenamiento de imágenes, tarea de reintentos | |
| | `infrastructure.identity` | Emisión y verificación de las credenciales propias (JWT) y `GoogleIdentityProvider`, el verificador de los tokens de Google (firma con las llaves públicas JWKS, emisor, vencimiento y audiencia) | |
| | `infrastructure.cache` | Caché del catálogo: `RedisCatalogCache` (con `REDIS_HOST`) y `NoCatalogCache` (sin Redis) | |
| | `infrastructure.config` | Ensamblado de los casos de uso con sus puertos y comprobaciones al arrancar (por ejemplo, `ImageSizeLimitCheck`: el peso máximo de imagen tiene una sola fuente) | |

Las reglas de dependencia se verifican con ArchUnit en cada `.\mvnw.cmd clean verify`.

El código original del Sprint 1, anterior a la reconstrucción, se conserva en la etiqueta de Git
`sprint1-original` (sección 15 de la especificación).

## Crear Deck (Sprint 3, historia 3.1.3)

Especificación: `pokearena/docs/AGENTE_Especificacion_Sprint3_PokeArena.md` (sección 7.1, Anexos A.7 y A.8,
criterios C.7). Un deck son exactamente `deckSize` cartas **distintas** de mi colección y un nombre único entre
mis decks (sin distinguir mayúsculas ni tildes). Ambas reglas viven solo en `DeckRules` (`DECK_SIZE` = 12,
`NAME_MAX_LENGTH` = 40) y la interfaz las lee de `GET /api/v1/opciones/juego`.

### Armar un deck desde la interfaz

1. Levanta la aplicación con el perfil `dev` (arriba) y abre `http://localhost:8080`.
2. El jugador de prueba recibe su colección inicial solo si el catálogo tiene al menos 15 cartas Comunes
   disponibles. En una base nueva, carga antes `pokearena/scripts/seed-10000-cartas.sql` (sus cartas no
   tienen archivos de imagen: se ve la imagen de reemplazo) o crea las cartas como administrador. Si el
   jugador entró antes de que hubiera cartas, vuelve a pulsar **Jugador**.
3. Pulsa **Jugador** y abre **Armar deck**: se ve tu colección (con "Cargar más"), el contador
   "Cartas elegidas: 0 de 12" y el límite del nombre ("máximo 40 caracteres").
4. Pulsa **Elegir** en 12 cartas (pulsar **Quitar** la saca; nunca queda dos veces y no se pueden elegir
   más de 12), escribe un nombre y pulsa **Guardar deck**: el panel de respuesta muestra `201` con el deck.
5. Abre **Mis decks**: la lista paginada y, con **Ver detalle**, las 12 cartas del deck.

### Casos rápidos (pestaña "Armar deck")

Usan tu sesión y tu colección real, calculan las cantidades a partir de `deckSize` y muestran el status y el
`type` que devolvió el servidor. Si tu colección no alcanza, avisan cuántas cartas hacen falta sin enviar nada.

| Caso | Resultado esperado |
|---|---|
| Deck con una carta de menos | `400 INVALID_DECK_SIZE` con la cantidad actual (11) |
| Deck con una carta de más | `400 INVALID_DECK_SIZE` con la cantidad actual (13) |
| Cartas repetidas | `400 DUPLICATE_DECK_CARDS` con el código repetido |
| Carta ajena | `400 CARD_NOT_OWNED` con el código que no posees |
| Nombre repetido | Primero `201`, después `409 DECK_NAME_TAKEN` (nombre único por ejecución y dos `Idempotency-Key` distintas) |

Después de guardar o de un caso rápido, "Mis decks" se recarga **en silencio**: el panel de respuesta
conserva la respuesta de la acción que pidió el usuario.

### Rendimiento con 10.000 cartas

`DeckPerformanceIT` carga `scripts/seed-10000-cartas.sql`, entrega al jugador su colección inicial real y agrega
las colecciones de 400 jugadores más a `player_card` (con `ANALYZE`). Hace 3 creaciones de calentamiento y mide
20 `POST /api/v1/decks` completos por HTTP con 12 cartas de esa colección. Exige p95 < 300 ms y que la consulta
de posesión use la clave `pk_player_card` sin leer `card` ni `player_card` completas. Cada ejecución guarda las
medidas y el plan en `pokearena/target/deck-performance.txt`.

Ejecución del 19 de noviembre de 2026 (Windows 11, Docker Desktop), medido en una ejecución local; varía
según la máquina:

| Medida | Valor |
|---|---|
| Mediana | 10,8 ms |
| p95 (exigido < 300 ms) | 12,4 ms |
| Máximo | 12,5 ms |
| Ejecución de la consulta de posesión (`EXPLAIN ANALYZE`) | 0,462 ms |

Plan de la consulta de posesión (`findOwnedCards`, 12 códigos):

```text
Nested Loop
  ->  Index Scan using pk_player_card on player_card pc  (rows=12)
        Index Cond: ((player_id = ...) AND (card_code = ANY ('{...12 códigos...}')))
  ->  Index Scan using uq_card_code on card c  (loops=12)
  ->  Index Scan using pk_card_type / pk_rarity / pk_ability  (loops=12)
Execution Time: 0.462 ms
```

## Probar búsqueda e inicio de partida desde la interfaz

La historia 5.1.1 y los contratos están en
[la especificación del Sprint 3](pokearena/docs/AGENTE_Especificacion_Sprint3_PokeArena.md)
(secciones 7.2, 7.3 y 11; Anexos A.9, A.11, A.14 y A.15).

### Preparar el entorno

1. Define `APP_GOOGLE_CLIENT_ID` antes de iniciar la aplicación y levántala con el perfil `dev`, siguiendo [Ejecutar la aplicación y abrir la interfaz](#ejecutar-la-aplicación-y-abrir-la-interfaz).
2. Carga cartas del catálogo con `pokearena/scripts/seed-10000-cartas.sql` (ver [Rendimiento con 10.000 cartas](#rendimiento-con-10000-cartas)). Las cartas semilla no tienen imágenes, por lo que la interfaz muestra el reemplazo.
3. Con la sesión **Administrador** de desarrollo, crea tres ubicaciones. Al menos dos deben tener límite de cartas mayor que cero y no restringir todos los turnos; puedes generarlas desde "Crear ubicación".
4. Prepara dos cuentas Google distintas, ambas con rol de jugador (no incluidas en `APP_ADMIN_IDENTITIES`). Usa perfiles de navegador separados: cada cuenta recibe su propia colección inicial al iniciar sesión y debe crear su deck desde "Armar deck".

> Las sesiones de desarrollo `Jugador` y `Administrador` son identidades fijas y no sirven como dos jugadores independientes: repetir `Jugador` en dos ventanas reutiliza el mismo jugador. La sesión de administrador de desarrollo sirve para configurar ubicaciones, pero no recibe colección inicial.

### Emparejar y ver el inicio

1. En cada navegador, inicia sesión con una cuenta de jugador diferente y arma un deck con las cartas de su colección.
2. En ambos, abre "Buscar partida", selecciona el deck y pulsa "Buscar partida". Mantén abiertas las dos sesiones mientras el sistema consulta su estado.
3. Cuando la API confirme `MATCHED`, la interfaz abre automáticamente "Inicio de partida". Ambos jugadores consultan su propia vista mediante `GET /api/v1/partidas/{id}`.
4. Comprueba que se muestran las mismas ubicaciones, la mano y energía propias, la cuenta regresiva del turno, la apuesta y el conteo de cartas en la mano rival. No se muestran la mano rival ni el orden de ningún deck.

### Casos rápidos de Sprint 3

Los botones están distribuidos en "Armar deck", "Buscar partida" y "Estado de búsqueda"; muestran el código HTTP y el tipo de error esperado.

| Pantalla | Caso | Respuesta esperada |
|---|---|---|
| Armar deck | Deck con una carta de menos o de más | `400 INVALID_DECK_SIZE` |
| Armar deck | Cartas repetidas | `400 DUPLICATE_DECK_CARDS` |
| Armar deck | Carta ajena | `400 CARD_NOT_OWNED` |
| Buscar partida | Buscar sin deck | `400 MISSING_FIELD` |
| Buscar partida | Buscar con deck ajeno | `404 DECK_NOT_FOUND` |
| Buscar partida | Buscar dos veces | La segunda solicitud reutiliza la búsqueda (`200`) |
| Estado de búsqueda | Salir después de emparejar | `409 SEARCH_ALREADY_MATCHED` |
| Estado de búsqueda | Aceptar el bot antes de la oferta | `409 BOT_OFFER_NOT_AVAILABLE` |
