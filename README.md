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

# 4. Arrancar la aplicación en la misma ventana (usa las variables anteriores).
.\mvnw.cmd spring-boot:run
```

**Si el puerto 5432 está ocupado** (por ejemplo, por un PostgreSQL instalado en Windows,
que arranca como servicio), publica la base en otro puerto y apunta la aplicación a él:

```powershell
$env:DB_PORT = "55432"
docker compose -f docker-compose.dev.yml up -d
$env:DB_URL = "jdbc:postgresql://localhost:55432/pokearena"
.\mvnw.cmd spring-boot:run
```

Las variables de `$env:` duran solo en esa ventana de PowerShell. Para detener la base:
`docker compose -f docker-compose.dev.yml down`. Si además agregas `-v`, se borran los
datos del volumen.

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
