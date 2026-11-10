# =============================================================================
# Propósito: imagen de la API de PokéArena (sección 12; RNF-INF-01).
# Responsabilidades: compilar el proyecto con el wrapper de Maven y ejecutar el jar con Java 21,
# con un usuario sin privilegios. No contiene secretos: APP_JWT_SECRET, DB_PASSWORD y el resto de
# la configuración llegan por variables de entorno (docker-compose.yml y .env).
#
# Decisiones:
#  - Dos etapas: la de compilación trae el JDK y Maven; la final solo el JRE y el jar, así la imagen
#    que corre es más chica y no lleva el código fuente ni las herramientas de compilación.
#  - Las dependencias se descargan en una capa aparte (solo pom.xml y el wrapper): mientras el pom no
#    cambie, Docker reutiliza esa capa y una recompilación no vuelve a bajar todo.
#  - Las pruebas NO corren aquí (-DskipTests): necesitan Docker (Testcontainers) y ya las ejecuta
#    `.\mvnw.cmd clean verify` y el CI. La imagen solo empaqueta.
# =============================================================================

# ---- Etapa 1: compilación -----------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper y pom primero: capa de dependencias reutilizable.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Código fuente y empaquetado del jar ejecutable de Spring Boot.
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/pokearena-*.jar /workspace/app.jar

# ---- Etapa 2: ejecución -------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios. La carpeta de imágenes es el volumen compartido por las 2 instancias
# (sección 9); se crea aquí con el dueño correcto para que el volumen la herede al montarse vacío.
RUN groupadd --system pokearena && useradd --system --gid pokearena --home-dir /app pokearena \
    && mkdir -p /data/images && chown -R pokearena:pokearena /data /app
USER pokearena

COPY --from=build --chown=pokearena:pokearena /workspace/app.jar /app/app.jar

ENV APP_IMAGES_ROOT_DIR=/data/images
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
