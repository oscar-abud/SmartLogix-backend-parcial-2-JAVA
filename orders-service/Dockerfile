# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Etapa 1: compilacion. Queda fuera de la imagen final, asi el JDK, Maven y el
# codigo fuente no viajan a produccion.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Las dependencias se resuelven en una capa propia: mientras no cambie el pom.xml
# Docker reutiliza la cache y no vuelve a descargar el repositorio entero.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# El wrapper puede venir con saltos de linea CRLF si se edito en Windows. Sin
# normalizarlo, el shebang apunta a un interprete inexistente y la capa falla
# con "exit code: 127".
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests \
 && java -Djarmode=layertools -jar target/*.jar extract --destination /app/layers

# ---------------------------------------------------------------------------
# Etapa 2: ejecucion. Solo JRE (no JDK) y el jar explotado en capas.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Nunca ejecutar como root: si alguien escapa del proceso, no es administrador.
RUN addgroup -S smartlogix && adduser -S smartlogix -G smartlogix
WORKDIR /app

# Capas ordenadas de menos a mas volatil: un cambio de codigo solo invalida la ultima.
COPY --from=build --chown=smartlogix:smartlogix /app/layers/dependencies/ ./
COPY --from=build --chown=smartlogix:smartlogix /app/layers/spring-boot-loader/ ./
COPY --from=build --chown=smartlogix:smartlogix /app/layers/snapshot-dependencies/ ./
COPY --from=build --chown=smartlogix:smartlogix /app/layers/application/ ./

USER smartlogix
EXPOSE 3003

# Lo usan "docker compose" y Docker Swarm para saber si la replica esta sana
# antes de enviarle trafico.
HEALTHCHECK --interval=20s --timeout=3s --start-period=70s --retries=3 \
  CMD wget -qO- http://localhost:3003/actuator/health || exit 1

# MaxRAMPercentage hace que la JVM respete el limite de memoria del contenedor
# en vez de mirar la RAM del host.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
