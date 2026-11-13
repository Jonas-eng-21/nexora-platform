# syntax=docker/dockerfile:1

# ---------- Estágio 1: build (Maven + JDK 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Camada de dependências: só é invalidada quando o pom.xml muda.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

# Código-fonte + empacotamento (testes rodam à parte: veja README).
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/nexora-api-*.jar /build/app.jar

# ---------- Estágio 2: runtime (somente JRE 21) ----------
FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --uid 10001 --no-create-home nexora
# Pasta dos PDFs de candidatura: o volume nomeado herda este dono na primeira montagem.
RUN mkdir -p /data/resumes && chown nexora:nexora /data/resumes
WORKDIR /app
COPY --from=build --chown=nexora:nexora /build/app.jar app.jar
USER nexora
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
ENTRYPOINT ["java", "-jar", "app.jar"]
