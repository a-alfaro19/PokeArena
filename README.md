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

Para desarrollo y demostraciones, el perfil `dev` ofrece además credenciales de prueba
(`POST /api/v1/dev/sesiones`), que no existen con ningún otro perfil.

El contrato HTTP está en
[pokearena/src/main/resources/openapi/pokearena.yaml](pokearena/src/main/resources/openapi/pokearena.yaml),
y una prueba de contrato lo compara con la aplicación en los dos sentidos.

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae su propio wrapper (`mvnw.cmd`).
- **Docker Desktop** encendido: lo usan las pruebas de integración (Testcontainers) y la base local.
- **Node.js 20 o superior (opcional)**, en `PATH`: las pruebas de comportamiento de la interfaz del
  jugador (`src/test/javascript/player.test.mjs`) usan `node:test`, sin paquetes npm, y Maven las
  ejecuta con `PlayerInterfaceBehaviorTest`. Sin Node, o con una versión menor, esa prueba se
  **omite** con un aviso ("Pruebas de JavaScript OMITIDAS") y el build sigue. Comprueba la
  instalación con `node --version`, o ejecútalas solas con `node --test src/test/javascript/player.test.mjs`.
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
`pokearena/src/main/resources/static/` (`index.html`, `app.js`, `player.js` y `styles.css`), sin
frameworks ni bibliotecas externas, que habla con la API real en el mismo origen. La única
excepción es el script oficial de Google Identity Services, que la política de seguridad de
contenido permite solo desde `accounts.google.com/gsi/`.

- **Barra de sesión:** "Entrar con Google" (habilitado solo si el servidor tiene
  `APP_GOOGLE_CLIENT_ID`, ver arriba) y, con el perfil `dev`, "Sin sesión", "Jugador" y
  "Administrador", que piden un token a `POST /api/v1/dev/sesiones`. El token vive **solo en
  memoria**: al recargar la página se vuelve a "Sin sesión". Sin el perfil `dev`, la interfaz oculta
  los botones de prueba.
- **Bienvenida y catálogo del jugador (historias 1.1.1, 3.1.1 y 3.1.2):** la bienvenida muestra el
  estado de la colección inicial que informa el servidor (`initialCollection`, también en las
  sesiones de prueba). La pestaña "Catálogo y mi colección" filtra por tipo y costo, con la casilla
  "Solo mis cartas", la imagen grande, las copias y "Cargar más". Sus casos rápidos incluyen tipo
  inexistente, límite 0, token de Google inválido y "Segundo ingreso del jugador", que vuelve a pedir
  la sesión del jugador de prueba y confirma `ALREADY_DELIVERED`.
- **Crear carta, Crear ubicación y Buscar carta:** formularios con todos los campos. Tipos y
  rarezas son listas fijas (las semillas de la base) y la habilidad es un texto con `HAB-000` como
  valor inicial. "Generar imagen" crea con `canvas` una imagen válida del tamaño exacto. La
  ubicación arma el JSON del campo `effect` según el tipo de efecto y muestra `NONE` seleccionada
  por defecto en la energía extra. La búsqueda muestra una tabla con miniatura y estado de imagen,
  y "Cargar más" pide la página siguiente con el cursor.
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
        │       └── static                interfaz web (index.html, app.js, player.js, styles.css)
        └── test
            ├── java/cr/ac/tec/pokearena  pruebas (*Test unitarias, *IT de integración, support/)
            └── javascript                pruebas de la interfaz del jugador (node --test)
```

El código vive en `cr.ac.tec.pokearena`, organizado en capas (sección 4.1 de la especificación).
Cada paquete tiene un `package-info.java` que explica su responsabilidad y lo que puede usar.

| Capa | Paquete | Qué contiene | Puede usar |
|---|---|---|---|
| Presentación | `src/main/resources/static/` | Interfaz web: `index.html` (estructura), `app.js` (sesiones, formularios del administrador, casos rápidos y panel de respuesta), `player.js` (estado del catálogo del jugador, bienvenida y Google, sin DOM: lo prueban las pruebas de JavaScript) y `styles.css` | La API por HTTP |
| API | `api.controller` | `SessionController` (inicio de sesión), `CatalogController` (catálogo del jugador), `CardController`, `LocationController`, `ImageFileController`, `ConfigurationController` (client id de Google) y `DevSessionController` (solo `dev`) | `application` |
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
| Infraestructura | `infrastructure.persistence` | Repositorios JDBC y `TransactionalUnitOfWork` | `domain` y `application` |
| | `infrastructure.storage` | Procesador y almacenamiento de imágenes, tarea de reintentos | |
| | `infrastructure.identity` | Emisión y verificación de las credenciales propias (JWT) y `GoogleIdentityProvider`, el verificador de los tokens de Google (firma con las llaves públicas JWKS, emisor, vencimiento y audiencia) | |
| | `infrastructure.cache` | Caché del catálogo: `RedisCatalogCache` (con `REDIS_HOST`) y `NoCatalogCache` (sin Redis) | |
| | `infrastructure.config` | Ensamblado de los casos de uso con sus puertos | |

Las reglas de dependencia se verifican con ArchUnit en cada `.\mvnw.cmd clean verify`.

El código original del Sprint 1, anterior a la reconstrucción, se conserva en la etiqueta de Git
`sprint1-original` (sección 15 de la especificación).
