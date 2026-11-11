# nexora-platform
Plataforma inteligente que conecta estudantes de tecnologia a empresas e oportunidades profissionais por meio de análise de competências, matching e inteligência artificial.

## Ambiente de desenvolvimento (Docker)

Java, Maven, PostgreSQL e Node.js rodam **somente em containers**. No host basta Git + Docker.

### Pré-requisitos
- Windows com WSL2 (Ubuntu) e Docker Desktop com integração WSL ativada.
- Repositório clonado no filesystem Linux (ex.: `~/workspace/nexora-platform`), nunca em `/mnt/...`.

### Estrutura
```
compose.yaml        # orquestração (postgres + backend; frontend no futuro)
compose.dev.yaml    # override de desenvolvimento (código montado, hot reload)
.env / .env.example # variáveis (.env não é versionado)
nexora-api/         # backend Spring Boot (Dockerfile multi-stage: Maven+JDK21 -> JRE21)
frontend/           # futuro (Vue 3 + TS + Vite), com Dockerfile próprio
```

### Variáveis (`.env`)
| Variável | Descrição |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Banco e credenciais |
| `POSTGRES_PORT` | Porta no host (somente 127.0.0.1), padrão 5432 |
| `BACKEND_PORT` | Porta da API no host, padrão 8080 |
| `SPRING_PROFILES_ACTIVE` | Perfil Spring, padrão `dev` |
| `JWT_SECRET` | Segredo de assinatura do JWT (HS256), mínimo de 32 caracteres; obrigatório. Gere com `openssl rand -base64 48` |
| `JWT_EXPIRATION` | Validade do token, padrão `60m` |

```bash
cp .env.example .env   # depois edite POSTGRES_PASSWORD e JWT_SECRET
```

### Subir tudo
```bash
docker compose up --build -d
```
API em http://localhost:8080 (sem endpoints públicos ainda; 401/403/404 são esperados — Spring Security ativo).

### Dia a dia
```bash
docker compose logs -f backend          # logs
docker compose ps                       # estado
docker compose stop                     # parar (mantém containers)
docker compose down                     # remove containers, MANTÉM dados
docker compose up -d --build backend    # reconstruir só o backend
```

### Maven no container
Os testes de integração usam um banco separado (`nexora_test`) no mesmo container PostgreSQL,
para não tocar nos dados de desenvolvimento. Crie-o uma única vez, com a stack no ar:
```bash
set -a; . ./.env; set +a
docker compose exec postgres createdb -U "$POSTGRES_USER" nexora_test
```
Para rodar os testes (o Flyway aplica as migrações em `nexora_test`):
```bash
set -a; . ./.env; set +a
docker run --rm --network nexora_default -v "$PWD/nexora-api:/w" -v nexora_m2:/root/.m2 -w /w \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/nexora_test \
  -e SPRING_DATASOURCE_USERNAME=$POSTGRES_USER -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
  maven:3.9-eclipse-temurin-21 mvn test
```
O build da imagem usa `-DskipTests` para não depender do banco.

### Modo desenvolvimento (hot reload)
```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d
```
O backend roda `mvn spring-boot:run` com `nexora-api/` montado. O DevTools reinicia a app quando
classes compiladas mudam; para compilar após editar `.java`:
```bash
docker compose -f compose.yaml -f compose.dev.yaml exec backend mvn -q compile
```
O banco permanece ativo. Para voltar ao modo imagem: `docker compose up -d --build`.

### PostgreSQL
```bash
docker compose exec postgres psql -U nexora -d nexora
```
Ou de uma ferramenta local: `127.0.0.1:5432`, com as credenciais do `.env`.

### Volumes
Os dados ficam no volume `nexora_postgres_data` (montado em `/var/lib/postgresql`, layout do PostgreSQL 18).
Sobrevivem a `docker compose down`. **Apagam os dados**: `docker compose down -v`.

### Frontend (futuro)
Criar `frontend/` com `Dockerfile` (build Node -> Nginx) e adicionar um serviço `frontend` em `compose.yaml`
(`build: ./frontend`, `depends_on: backend`). Nada mais precisa mudar.
