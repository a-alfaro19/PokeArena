# PokéArena

Backend del proyecto PokéArena (curso CE-1116, TEC): Java 21 con Spring Boot, más una
interfaz web servida por la misma aplicación. La especificación completa está en
[pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md](pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md).

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae su propio wrapper (`mvnw`).
- **Docker Desktop** encendido: lo usan las pruebas de integración (Testcontainers) y la
  base de datos local.
- Git.

## Clonar y compilar

```bash
git clone https://github.com/a-alfaro19/PokeArena.git
cd PokeArena/pokearena
./mvnw verify        # en Windows (cmd o PowerShell): .\mvnw.cmd verify
```

`./mvnw verify` compila el proyecto y ejecuta todas las pruebas: las unitarias y de
arquitectura (`*Test`) y las de integración (`*IT`). Las de integración levantan su
propio PostgreSQL en un contenedor, así que no necesitan la base local de abajo.

## Base de datos local (desarrollo)

La aplicación necesita PostgreSQL para arrancar; Flyway crea el esquema solo al iniciar.
`docker-compose.dev.yml` levanta únicamente PostgreSQL (la misma imagen que las pruebas).
En PowerShell, desde la carpeta `pokearena`:

```powershell
# 1. Contraseña local (no se guarda en el repositorio; elige la que quieras).
$env:DB_PASSWORD = "<contraseña local>"

# 2. Levantar PostgreSQL en segundo plano y comprobar que esté "healthy".
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps

# 3. Conexión de la aplicación. Estos son los valores por defecto; defínelos solo si
#    cambias el puerto, la base o el usuario.
$env:DB_URL = "jdbc:postgresql://localhost:5432/pokearena"
$env:DB_USERNAME = "pokearena"

# 4. Secreto de firma de las credenciales (obligatorio: sin él la aplicación no arranca).
#    Genera uno aleatorio de 48 bytes; nunca lo guardes en el repositorio.
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($b)

# 5. Arrancar la aplicación en la misma ventana (usa las variables anteriores).
.\mvnw.cmd spring-boot:run
```

`APP_JWT_SECRET` firma las credenciales propias (sección 5.2) y debe tener al menos 32 bytes.
Si cambia, las sesiones abiertas dejan de valer. Las identidades administradoras se configuran
con `APP_ADMIN_IDENTITIES`, una lista `proveedor:sub` separada por comas.

**Si el puerto 5432 está ocupado** (por ejemplo, por un PostgreSQL instalado en Windows,
que arranca como servicio), publica la base en otro puerto y apunta la aplicación a él:

```powershell
$env:DB_PORT = "55432"
docker compose -f docker-compose.dev.yml up -d
$env:DB_URL = "jdbc:postgresql://localhost:55432/pokearena"
.\mvnw.cmd spring-boot:run
```

### Perfil `dev`

Por defecto los logs salen en JSON estructurado (para producción y para buscar por
`correlationId`). Para desarrollar conviene el perfil `dev`, que los muestra como texto legible
(`application-dev.properties`). Este perfil también habilita las credenciales de prueba
(`POST /api/v1/dev/sesiones`), que no existen con ningún otro perfil. Se activa con la variable
`SPRING_PROFILES_ACTIVE`, en la misma ventana donde se arranca la aplicación:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
.\mvnw.cmd spring-boot:run
```

Con el perfil `dev`, se pide un token de administrador o de jugador de prueba (cada rol es
siempre el mismo jugador real, con proveedor `dev`) y se usa en el encabezado `Authorization`:

```powershell
$s = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/dev/sesiones `
  -ContentType "application/json" -Body '{"role":"ADMIN"}'
$headers = @{ Authorization = "Bearer $($s.accessToken)" }
```

Para volver al formato JSON: `Remove-Item Env:SPRING_PROFILES_ACTIVE`.

Las variables de `$env:` duran solo en esa ventana de PowerShell. Para detener la base:
`docker compose -f docker-compose.dev.yml down`. Si además agregas `-v`, se borran los
datos del volumen.

## Interfaz web

La interfaz (sección 11 de la especificación) es una sola página en
`pokearena/src/main/resources/static/` (`index.html`, `app.js` y `styles.css`), sin frameworks ni
bibliotecas externas. La sirve la misma aplicación y habla con la API real en el mismo origen.

### Levantarla paso a paso (PowerShell, desde la carpeta `pokearena`)

```powershell
# 1. Base de datos: contraseña local (elige la que quieras; no se guarda en el repositorio).
$env:DB_PASSWORD = "<contraseña local>"
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps        # espera a que diga "healthy"

# 2. Conexión de la aplicación (este es el valor por defecto; cámbialo si usas otro puerto).
$env:DB_URL = "jdbc:postgresql://localhost:5432/pokearena"

# 3. Secreto de firma de las credenciales: obligatorio, al menos 32 bytes, nunca en el repositorio.
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($b)

# 4. Perfil dev: habilita las sesiones de prueba que usa la barra de sesión de la interfaz.
$env:SPRING_PROFILES_ACTIVE = "dev"

# 5. Arrancar (en esta misma ventana) y abrir la interfaz en el navegador.
.\mvnw.cmd spring-boot:run
Start-Process "http://localhost:8080"
```

Si el puerto 5432 está ocupado, usa `$env:DB_PORT = "55432"` antes del paso 1 y
`$env:DB_URL = "jdbc:postgresql://localhost:55432/pokearena"` en el paso 2 (ver arriba).

### Qué tiene

- **Barra de sesión:** "Sin sesión", "Jugador" y "Administrador" piden un token a
  `POST /api/v1/dev/sesiones`. El token se guarda **solo en memoria**: al recargar la página se
  vuelve a "Sin sesión". Sin el perfil `dev` ese endpoint no existe (404): la interfaz oculta los
  botones y avisa que el ingreso real llegará con el inicio de sesión con proveedor (Sprint 2).
- **Crear carta, Crear ubicación y Buscar carta:** formularios con todos los campos. Tipos y
  rarezas son listas fijas (las semillas de la base) y la habilidad es un texto con `HAB-000` como
  valor inicial. "Generar imagen" crea con `canvas` una imagen válida del tamaño exacto. La
  ubicación arma el JSON del campo `effect` según el tipo de efecto y muestra `NONE` seleccionada
  por defecto en la energía extra. La búsqueda muestra una tabla con miniatura y estado de imagen,
  y "Cargar más" pide la página siguiente con el cursor.
- **Idempotencia:** cada creación genera su `Idempotency-Key` en el navegador
  (`crypto.randomUUID()`); "Repetir con la misma clave" reenvía la última creación con la misma
  clave, el mismo contenido y la misma sesión, y debe devolver la misma respuesta.
- **Panel de respuesta:** método y ruta, código HTTP con color (verde 2xx, naranja 4xx, rojo 5xx),
  el JSON completo, el `correlationId`, la `Idempotency-Key` y la vista previa de las imágenes. Si
  una imagen no carga (por ejemplo, las cartas de `scripts/seed-10000-cartas.sql`, cuyas URLs no
  tienen archivo), se muestra una imagen de reemplazo.
- **Casos de prueba rápidos:** un botón por cada caso del Sprint 1 de la sección 11. Cada uno pide
  su propia sesión de prueba (sin cambiar la de la barra), genera sus imágenes y compara la
  respuesta con lo esperado ("✓ Coincide" o "✗ Se esperaba ...").

## Estructura de capas

El código vive en `pokearena/src/main/java/cr/ac/tec/pokearena`, organizado en capas
(sección 4.1 de la especificación). Cada paquete tiene un `package-info.java` que explica
su responsabilidad y lo que puede usar.

| Capa | Ubicación | Responsabilidad | Puede usar |
|---|---|---|---|
| Presentación | `src/main/resources/static/` | Interfaz web (HTML, CSS y JavaScript) | La API por HTTP |
| API | `api` | Controladores, DTOs, seguridad y formato de errores | `application` |
| Aplicación | `application` | Casos de uso, transacción e idempotencia | `domain` |
| Dominio | `domain` | Modelo, reglas, plug-ins y puertos de persistencia | Solo `java.*` |
| Infraestructura | `infrastructure` | Base de datos, Redis, imágenes, identidad y configuración | `domain` y `application` |

Las reglas de dependencia se verifican con ArchUnit en cada `./mvnw verify`.

## Carpeta `legacy/`

`legacy/` contiene el código original del Sprint 1 **solo como referencia** para portar
partes a la estructura nueva (sección 15 de la especificación). Está fuera de la compilación
de Maven, no se ejecuta ni se prueba, y **se borrará antes del cierre**.
