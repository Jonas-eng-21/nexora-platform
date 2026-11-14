# Início do projeto — Infraestrutura Docker (tarefa 1)

Data: 2026-11-10 · Ambiente: Windows + Ubuntu WSL2, Docker Desktop (Engine 29.9.2, Compose v5.6.1)

## Objetivo
Containerizar todo o ambiente de desenvolvimento do Nexora (backend Spring Boot + PostgreSQL 18), sem instalar Java, Maven, PostgreSQL ou Node.js no host. Meta: subir tudo com `docker compose up --build -d`.

## Estado inicial do repositório
- Apenas o backend `nexora-api/` (Spring Boot 4.1.1, Java 21, Maven).
- Dependências: Data JPA, Flyway (+ flyway-database-postgresql), Security, Validation, WebMVC, DevTools, Lombok, driver PostgreSQL.
- `application.yaml` só continha o nome da aplicação. Sem migrações Flyway, sem entidades, sem Docker.

## Arquivos criados/modificados
| Arquivo | Responsabilidade |
|---|---|
| `compose.yaml` | Orquestra `postgres` (postgres:18) e `backend`; frontend previsto em comentário |
| `compose.dev.yaml` | Override de dev: imagem Maven, `nexora-api/` montado, `mvn spring-boot:run` (não testado) |
| `nexora-api/Dockerfile` | Multi-stage: Maven 3.9 + JDK 21 (cache BuildKit de `~/.m2`) → `eclipse-temurin:21-jre`, usuário não-root (uid 10001), porta 8080 |
| `nexora-api/.dockerignore` | Exclui target, .git, IDEs, .env* do contexto |
| `.env` / `.env.example` | Variáveis (`.env` com senha aleatória, não versionado) |
| `.gitignore` (raiz) | Ignora `.env*` (exceto `.env.example`) e IDEs (`context/` passou a ser versionada em 2026-11-14) |
| `nexora-api/src/main/resources/application.yaml` | `ddl-auto: validate`, `open-in-view: false`, Flyway ligado |
| `nexora-api/src/main/resources/application-dev.yaml` | Log DEBUG para `br.com.nexora` |
| `README.md` | Seção de ambiente Docker (original preservado) |

## Arquitetura
- `backend` conecta em `jdbc:postgresql://postgres:5432/nexora`; inicia só após o Postgres ficar `healthy` (`pg_isready`).
- Credenciais vêm do `.env`, repassadas pelo Compose para `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` e `SPRING_PROFILES_ACTIVE` (padrão `dev`).
- Postgres publicado só em `127.0.0.1:5432`; API em `0.0.0.0:8080`.
- Volume nomeado `nexora_postgres_data` montado em `/var/lib/postgresql` (layout do PostgreSQL 18, não `/var/lib/postgresql/data`).
- Variáveis do `.env`: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `BACKEND_PORT`, `SPRING_PROFILES_ACTIVE`.

## Decisões técnicas
- Build da imagem com `-DskipTests`, pois `contextLoads` exige banco; testes rodam à parte na rede do Compose.
- `pom.xml` copiado antes de `src` para aproveitar cache de dependências.
- JAR copiado como `/build/app.jar` (evita ambiguidade de múltiplos JARs).
- `ddl-auto: validate` para o esquema ficar sob controle do Flyway.
- Sem Dockerfile para o banco: imagem oficial configurada pelo Compose.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60` (Docker com ~3,7 GiB).

## Validação (resultados reais)
- `docker compose config --quiet`: válido.
- `docker compose up --build -d`: build e start OK; backend `Up`, Postgres `healthy`.
- Spring Boot 4.1.1 iniciou em ~7,5 s, sem erros.
- PostgreSQL 18.6; Flyway criou `flyway_schema_history` (sem migrações de negócio).
- `GET http://localhost:8080/` → HTTP 401 (esperado, Spring Security sem endpoints públicos).
- Persistência: tabela temporária sobreviveu a `docker compose down` + `up`; depois removida. `down -v` nunca usado.
- Testes Maven em container (`mvn -q test`, rede do Compose): código de saída 0 (modo quiet, sem contagem de testes).

## Comandos principais
```bash
docker compose up --build -d
docker compose logs -f backend
docker compose up -d --build backend
docker compose down                  # mantém dados
docker compose -f compose.yaml -f compose.dev.yaml up -d   # hot reload
docker compose exec postgres psql -U nexora -d nexora
```

## Pendências / observações
- `compose.dev.yaml` não foi executado/testado.
- Maven via bind mount cria `nexora-api/target/` como root (já no .gitignore do módulo).
- Backend sem healthcheck (não há Actuator).
- No modo dev, é preciso rodar `mvn compile` no container para o DevTools reiniciar a app.
- Nada foi commitado nesta tarefa.
- Frontend (Vue 3 + TS + Vite) ainda não existe; planejado `frontend/Dockerfile` (Node → Nginx) e serviço `frontend` no `compose.yaml`.
