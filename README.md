# PokéArena

Proyecto PokéArena del curso CE-1116 (TEC): un backend en **Java 21 con Spring Boot**,
PostgreSQL y una interfaz web servida por la misma aplicación. La especificación completa,
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
| 1.1.1 Inicio de sesión con proveedor | `POST /api/v1/sesiones` (pública) | — | Sprint 2, API terminada (falta la interfaz) |
| 3.1.1 Recibir colección inicial | Caso de uso `DeliverInitialCollectionUseCase` (se conectará al inicio de sesión) | — | Sprint 2, caso de uso terminado (falta conectarlo al login) |
| 3.1.2 Explorar el catálogo y mi colección | `GET /api/v1/catalogo` | — | Sprint 2, pendiente |

Mientras llega el inicio de sesión con proveedor, el perfil `dev` ofrece credenciales de prueba
(`POST /api/v1/dev/sesiones`), que no existen con ningún otro perfil.

El contrato HTTP está en
[pokearena/src/main/resources/openapi/pokearena.yaml](pokearena/src/main/resources/openapi/pokearena.yaml),
y una prueba de contrato lo compara con la aplicación en los dos sentidos.

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae su propio wrapper (`mvnw.cmd`).
- **Docker Desktop** encendido: lo usan las pruebas de integración (Testcontainers) y la base local.
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
las imágenes, por defecto `./data/images`) y `APP_ADMIN_IDENTITIES` (ver abajo).

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
   `POST /api/v1/sesiones` con `"provider": "google"` responde 400 `UNSUPPORTED_PROVIDER` (en
   desarrollo se puede seguir usando el perfil `dev`).

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
`pokearena/src/main/resources/static/` (`index.html`, `app.js` y `styles.css`), sin frameworks ni
bibliotecas externas, que habla con la API real en el mismo origen.

- **Barra de sesión:** "Sin sesión", "Jugador" y "Administrador" piden un token a
  `POST /api/v1/dev/sesiones`. El token vive **solo en memoria**: al recargar la página se vuelve
  a "Sin sesión". Sin el perfil `dev`, la interfaz oculta los botones y avisa que el ingreso real
  llegará con el inicio de sesión con proveedor.
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
├── CLAUDE.md                     instrucciones para el agente de IA
├── README.md
└── pokearena                     proyecto Maven
    ├── docker-compose.dev.yml    PostgreSQL local para desarrollo
    ├── docs                      especificación (fuente de verdad)
    ├── scripts                   seed-10000-cartas.sql (datos de rendimiento)
    └── src
        ├── main
        │   ├── java/cr/ac/tec/pokearena
        │   └── resources
        │       ├── application.properties, application-dev.properties
        │       ├── db/migration          migraciones Flyway (V1 esquema y semillas, V2, ...)
        │       ├── openapi/pokearena.yaml contrato HTTP
        │       └── static                interfaz web (index.html, app.js, styles.css)
        └── test/java/cr/ac/tec/pokearena  pruebas (*Test unitarias, *IT de integración, support/)
```

El código vive en `cr.ac.tec.pokearena`, organizado en capas (sección 4.1 de la especificación).
Cada paquete tiene un `package-info.java` que explica su responsabilidad y lo que puede usar.

| Capa | Paquete | Qué contiene | Puede usar |
|---|---|---|---|
| Presentación | `src/main/resources/static/` | Interfaz web | La API por HTTP |
| API | `api.controller` | `CardController`, `LocationController`, `ImageFileController` y `DevSessionController` (solo `dev`) | `application` |
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
| | `infrastructure.identity` | Emisión y verificación de las credenciales propias (JWT) | |
| | `infrastructure.cache` | Caché del catálogo (hoy sin Redis: no guarda nada) | |
| | `infrastructure.config` | Ensamblado de los casos de uso con sus puertos | |

Las reglas de dependencia se verifican con ArchUnit en cada `.\mvnw.cmd clean verify`.

El código original del Sprint 1, anterior a la reconstrucción, se conserva en la etiqueta de Git
`sprint1-original` (sección 15 de la especificación).
