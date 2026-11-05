# PokéArena

Backend del proyecto PokéArena (curso CE-1116, TEC): Java 21 con Spring Boot, más una
interfaz web servida por la misma aplicación. La especificación completa está en
[pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md](pokearena/docs/AGENTE_Especificacion_v2_Sprint2_PokeArena.md).

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae su propio wrapper (`mvnw`).
- Git.

## Clonar y compilar

```bash
git clone https://github.com/a-alfaro19/PokeArena.git
cd PokeArena/pokearena
./mvnw verify        # en Windows (cmd o PowerShell): .\mvnw.cmd verify
```

`./mvnw verify` compila el proyecto y ejecuta todas las pruebas, incluidas las de
arquitectura (`ArchitectureRulesTest`).

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
