# =============================================================================
#  DevNet - imagen de la API
#
#  Multi-stage: la imagen final no lleva Maven, ni el codigo fuente, ni el .m2.
#  Lineamiento 7.2: "artefactos versionados y, cuando aplique, imagenes Docker
#  inmutables".
# =============================================================================

# ----------------------------------------------------------------------------
# Etapa 1 - construccion
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS construccion

WORKDIR /build

# Las dependencias se resuelven en una capa propia: mientras el pom no cambie,
# Docker la reutiliza y una reconstruccion no vuelve a bajar medio Maven Central.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Las pruebas corren en el pipeline con Testcontainers, no aqui: no hay demonio
# Docker disponible dentro de esta etapa.
RUN mvn -B clean package -DskipTests

# ----------------------------------------------------------------------------
# Etapa 2 - ejecucion
# ----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Usuario sin privilegios. Lineamiento 6.1, despliegue: "configuracion endurecida".
RUN addgroup -S devnet && adduser -S devnet -G devnet

WORKDIR /app

COPY --from=construccion --chown=devnet:devnet /build/target/*.jar app.jar

USER devnet

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

# UseSerialGC y un stack pequeno: el plan gratuito de Render da poca memoria y G1
# no rinde ahi. Revisar si se promociona el plan.

# Render decide el puerto por la variable PORT; application.yml ya la respeta.
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]