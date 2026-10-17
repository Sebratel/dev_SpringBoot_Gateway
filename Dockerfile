# syntax=docker/dockerfile:1
# --- Estágio 1: build ---
FROM maven:3.9.9-eclipse-temurin-21 AS build

# O sebratel-java-parent vem do GitHub Packages, que exige token até para pacotes públicos. Para o build da imagem não
# depender de segredo, o parent é construído a partir do código-fonte público (tag da release) e instalado no repositório local.
ARG JAVA_PARENT_VERSION=1.0.0
WORKDIR /parent
RUN curl -fsSL "https://github.com/Sebratel/dev_Maven_JavaParent/archive/refs/tags/v${JAVA_PARENT_VERSION}.tar.gz" \
      | tar xz --strip-components=1 \
 && mvn -B -q versions:set -DnewVersion="${JAVA_PARENT_VERSION}" -DprocessAllModules=true -DgenerateBackupPoms=false \
 && mvn -B -q -f sebratel-java-parent/pom.xml versions:set-property -Dproperty=sebratel-java-build-rules.version \
        -DnewVersion="${JAVA_PARENT_VERSION}" -DgenerateBackupPoms=false \
 && mvn -B -q install -DskipTests

WORKDIR /app
COPY pom.xml .
COPY gateway/pom.xml gateway/pom.xml
COPY contract-tests/pom.xml contract-tests/pom.xml
RUN mvn -B -q -pl gateway -am dependency:go-offline
COPY gateway/src gateway/src
RUN mvn -B -DskipTests -pl gateway -am package

# --- Estágio 2: runtime ---
FROM eclipse-temurin:21-jre-jammy
ENV TZ=America/Sao_Paulo \
    JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    SERVER_PORT=8085
WORKDIR /app
RUN groupadd -g 10001 app && useradd -r -u 10001 -g app app
COPY --from=build /app/gateway/target/api-gateway-0.1.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8085
# Sem curl na imagem: confere só se a porta aceita conexão. O healthcheck HTTP fica no compose.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT}' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
