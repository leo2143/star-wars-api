# syntax=docker/dockerfile:1

# ---- Etapa de build: JDK 21 + el wrapper de Maven, para que el build use la versión de Maven fijada por el proyecto ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolver primero las dependencias para que esta capa quede en caché hasta que cambie el wrapper o el POM.
# `sh mvnw` evita depender del bit de ejecución del archivo.
COPY mvnw pom.xml lombok.config ./
COPY .mvn ./.mvn
RUN sh mvnw -B -q dependency:go-offline

COPY src ./src
# Los tests no se ejecutan aquí: necesitan Docker (Testcontainers), que no está disponible dentro de `docker build`.
# Ejecutarlos con `./mvnw verify` en el host o en CI.
RUN sh mvnw -B -q clean package -DskipTests

# ---- Etapa de runtime: solo JRE, sin herramientas de build, usuario no root -------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/target/star-wars-api-*.jar /app/app.jar
USER app

# La aplicación escucha en $PORT (Render lo inyecta); 8080 es el valor local por defecto.
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
