# nexora-platform
Plataforma inteligente que conecta estudantes de tecnologia a empresas e oportunidades profissionais por meio de análise de competências, matching e inteligência artificial.

## Ambiente de desenvolvimento (Docker)

Java, Maven, PostgreSQL e Node.js rodam **somente em containers**. No host basta Git + Docker.

### Pré-requisitos
- Windows com WSL2 (Ubuntu) e Docker Desktop com integração WSL ativada.
- Repositório clonado no filesystem Linux (ex.: `~/workspace/nexora-platform`), nunca em `/mnt/...`.

### Estrutura
```
compose.yaml        # orquestração (postgres + backend + frontend)
compose.dev.yaml    # override de desenvolvimento (código montado, hot reload)
.env / .env.example # variáveis (.env não é versionado)
nexora-api/         # backend Spring Boot (Dockerfile multi-stage: Maven+JDK21 -> JRE21)
nexora-web/         # frontend Vue 3 + TypeScript + Vite + Tailwind v4 + Pinia + Vue Router (Dockerfile próprio)
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
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Administrador inicial (opcional). Senha com no mínimo 12 caracteres |
| `GEMINI_MATCHING_ENABLED` | Liga o matching semântico (score híbrido). Padrão `false` |
| `GEMINI_API_KEY` | Chave da Gemini API. Só é exigida com a flag ligada; fica apenas no `.env` local |
| `GEMINI_EMBEDDING_MODEL` / `GEMINI_EMBEDDING_DIMENSIONS` | Modelo e dimensão dos embeddings, padrão `gemini-embedding-001` e `768` |

### Administrador inicial
Não existe cadastro público de administrador. Com `ADMIN_EMAIL` e `ADMIN_PASSWORD` definidos no `.env`, o backend
cria a conta `ADMIN` na subida, **uma única vez**: se já existir uma conta com esse e-mail, nada é alterado
(a senha não é redefinida e contas de outros papéis não são promovidas). Sem as variáveis, nenhum admin é criado.

```bash
cp .env.example .env   # depois edite POSTGRES_PASSWORD e JWT_SECRET
```

### Subir tudo
```bash
docker compose up --build -d
```
**Um único comando sobe os três serviços** (`postgres`, `backend` e `frontend`), sem instalar Node, npm, Java ou Maven
no host. Aplicação em **http://localhost:5173** (frontend) e API em http://localhost:8080. Rotas protegidas exigem `Authorization: Bearer <token>`; sem token a resposta é 401
em `application/problem+json`.

### Matching semântico com Gemini
O matching estruturado (competências, interesses, modelo e localização) continua sendo a base do score. Com o
matching semântico ligado, o backend também compara, por **embeddings de texto** do Gemini, o currículo-base do
estudante com a vaga. A IA fornece só vetores: o **cosseno e o score final são calculados pelo backend**, nunca
por uma nota de LLM.

- Score semântico: `100 × clamp(cosseno, 0, 1)`. Score híbrido: `round(0,80 × estruturado + 0,20 × semântico)`.
  Os pesos (`nexora.matching.semantic.semantic-weight`, padrão 20) são **hipóteses de implementação**, não validação
  científica; a POC mostrou que o score semântico separa pouco (ver `context/andamento.md`, seção 9).
- Só o **currículo-base** e a vaga entram nos textos enviados ao provedor (resumo, curso, competências do catálogo,
  experiências, projetos, cursos, interesses; na vaga, título, descrição, área, competências e modalidade). **Nunca**
  e-mail, links, nomes, instituição, localização, PDF ou snapshot de candidatura.

**Configuração (somente no `.env`, ignorado pelo Git; a chave nunca vai para imagem, build args ou logs):**
```dotenv
GEMINI_MATCHING_ENABLED=false     # padrão; true liga o score híbrido
GEMINI_API_KEY=                   # obrigatória só com a flag ligada (https://aistudio.google.com/apikey)
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
GEMINI_EMBEDDING_DIMENSIONS=768
```
Desligada, a aplicação sobe **sem chave**, não faz chamada externa nem consulta o cache, e todas as respostas saem
com `scoreType=STRUCTURED`. Ligada e sem chave, a aplicação **não sobe** (falha explícita). Para ligar ou desligar,
edite a flag no `.env` e rode `docker compose up -d backend` (o `.env` é lido na criação do container).

**Resposta de matching (campos aditivos; nada foi removido ou renomeado):**
```json
{ "score": 98, "scoreType": "HYBRID", "structuredScore": 100, "semanticScore": 89,
  "algorithmVersion": "v1", "criteria": [], "summary": "..." }
```
| `scoreType` | Significado |
|---|---|
| `STRUCTURED` | Só critérios estruturados (flag desligada, ou estudante sem competência do catálogo nem área de interesse). `semanticScore` é nulo |
| `HYBRID` | Estruturado combinado com a similaridade semântica |
| `FALLBACK` | Semântica ligada, mas indisponível para o par (falha, timeout, limite, resposta inválida): vale o estruturado, `semanticScore` nulo |

**Custo e cache.** Os embeddings de uma lista são pedidos **em lote** (uma chamada por requisição, até 100 textos) e
guardados na tabela `text_embeddings` (migração V19), com chave por hash do texto, modelo e dimensão: só textos
novos ou alterados vão ao Gemini. No máximo 100 textos novos por requisição; o excedente sai como `FALLBACK`. Timeout
de 10 s e 2 tentativas, só para erros transitórios (429, 5xx, rede).

**Testes sem consumir o Gemini.** Toda a suíte usa um provedor falso e roda com a flag desligada:
`nexora-test` (ou `scripts/nexora-test`), no Docker. Nenhum teste chama a API real.

**Limitações e cuidados.**
- A chamada ao provedor ocorre dentro da transação de leitura da requisição (segura uma conexão do pool por até 10 s).
- O cache não expira (linhas antigas ficam) e, com cache frio e muitas vagas, a primeira lista pode misturar
  `HYBRID` e `FALLBACK`.
- Os pesos 80/20 e a normalização do cosseno não foram validados; nenhuma superioridade do híbrido foi demonstrada.
- Cotas, preços e termos de uso de dados da Gemini API valem para a chave usada: a assinatura pessoal Google AI Pro
  **não** equivale à cota paga da API. Para dados reais de estudantes, revise os termos (inclusive da camada gratuita)
  e o faturamento antes de ligar a flag; nos testes, use só dados fictícios.

### Documentação da API (Swagger)
Disponível **somente no perfil `dev`** (desligada nos demais perfis):
- Swagger UI: http://localhost:8080/swagger-ui.html
- Contrato OpenAPI (JSON): http://localhost:8080/v3/api-docs

Use o botão **Authorize** do Swagger UI para informar o JWT.

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
Atalho: `scripts/nexora-test` faz tudo isso (sobe o Postgres, cria o `nexora_test` se faltar e roda a suíte
completa no Docker). Argumentos extras vão para o Maven, por exemplo `nexora-test -Dtest=NomeDaClasse`. Para usá-lo
de qualquer pasta, crie um link no `PATH`: `ln -s "$PWD/scripts/nexora-test" ~/.local/bin/nexora-test`.
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

Os PDFs de currículo das candidaturas ficam no volume `nexora_resumes_data`, montado **só no backend** em
`/data/resumes` (pasta configurável por `RESUMES_STORAGE_DIR`). Não há rota pública para eles: o download é feito
por `GET /api/v1/applications/{id}/resume`, com autenticação e autorização. O volume guarda os arquivos e o banco
guarda os metadados, então **os dois precisam de backup juntos**; `docker compose down -v` apaga ambos. Em produção,
o volume só serve enquanto houver uma única instância com disco durável; caso contrário, troque
`ResumeFileStorage` por um serviço privado de objetos antes do deploy.

### Frontend (`nexora-web/`)
SPA Vue 3 em modo desenvolvimento (Vite com HMR), serviço `frontend` do Compose, porta `${FRONTEND_PORT:-5173}`.

- **Proxy:** o navegador só fala com o Vite. Chamadas a `/api/*` são encaminhadas pelo proxy do Vite a
  `http://backend:8080` (variável `API_PROXY_TARGET`) pela rede do Compose; por isso **não há CORS** nem URL do
  backend no bundle. O cliente Axios usa `baseURL: '/api/v1'`. Não há segredos no frontend (`VITE_*` seria público).
- **Hot reload:** `./nexora-web` é montado em `/app`; as dependências ficam no volume `nexora_frontend_node_modules`
  (o bind mount não as esconde). Após alterar `package.json`/`package-lock.json`, recrie o volume:
  `docker compose rm -sf frontend && docker volume rm nexora_frontend_node_modules && docker compose up --build -d`.
- **Telas (US-34 e US-35):** `/login`, `/cadastro` (estudante, empresa, universidade; ADMIN nunca) e, para o
  estudante, a área completa em `/estudante`: início com as tarefas pendentes, `perfil` (dados e competências do
  catálogo), `curriculo` (projetos, formação, cursos, experiência, links, áreas de interesse e modelos de trabalho),
  `vinculo` (solicitar vínculo, elegibilidade e histórico), `vagas` (recomendadas com score explicável ou todas, com
  filtros na URL) e `vagas/:id` (detalhe, compatibilidade e candidatura por currículo do Nexora ou PDF de até 5 MiB),
  e `candidaturas` (histórico, filtro por situação e currículo efetivamente enviado). Para a empresa, a área completa
  em `/empresa`: início com as vagas abertas e as candidaturas aguardando análise, `perfil`, `vagas` (lista com filtro
  por situação, criar, editar, encerrar, reabrir e excluir com confirmação), `vagas/:id/candidaturas` (documento
  enviado, decisão com motivo, reativação explícita) e `vagas/:id/recomendados` (estudantes validados com score
  explicável, sem dados privados). Para a universidade, a área
  completa em `/universidade`: início com a fila de solicitações aguardando análise, `vinculos` (solicitações
  recebidas com filtro por situação, aprovar, rejeitar com motivo e revogar vínculo com confirmação; a suspensão das
  candidaturas na revogação é automática no backend) e `perfil` (dados institucionais). Para o administrador, a área
  em `/admin`: início com os cadastros de universidade aguardando aprovação e contagens reais de vínculos pendentes e
  contas suspensas, `universidades` (aprovar ou rejeitar com motivo), `usuarios` (filtros de papel e situação,
  suspender e reativar com motivo, sem suspender a própria conta), `vinculos` (intervenção em vínculos de qualquer
  universidade), `vagas` (vagas abertas, encerrar, reabrir e excluir com motivo, candidaturas por vaga e perfil da
  empresa) e `auditoria` (somente leitura). Rotas protegidas por sessão e por papel.
- **Sessão:** token JWT em `sessionStorage` (decisão temporária do MVP; exposto a XSS), papel vindo do login e
  revalidado em `GET /users/me` a cada recarga. Um 401 em rota protegida limpa a sessão e volta ao login.
- **Testes e build (sempre via Docker):**
  ```bash
  docker compose exec frontend npm test          # Vitest + Vue Test Utils
  docker compose exec frontend npm run build     # vue-tsc + build de produção
  ```
  Sem o serviço no ar: `docker compose run --rm --no-deps frontend npm test`.
- **Limitações:** imagem de desenvolvimento (Nginx/HTTPS ficam na stack de produção, veja abaixo); se a API ainda estiver
  iniciando, a tela mostra erro recuperável e basta tentar de novo; `npm audit` aponta 5 avisos moderados apenas na
  cadeia de teste (`@vue/test-utils` → `editorconfig` → `minimatch`), fora do bundle.
- **Porta 5432 ocupada no host?** Defina `POSTGRES_PORT=5433` (ou outra) no `.env`.

## Produção (`compose.prod.yaml`)

Stack separada da de desenvolvimento (projeto Compose `nexora-prod`, volumes próprios). **Guia passo a passo para a VPS
Hostinger/Ubuntu: [`docs/DEPLOY_HOSTINGER_UBUNTU.md`](docs/DEPLOY_HOSTINGER_UBUNTU.md).** Evidências, matriz de requisitos e
registro de riscos da US-39: [`context/US39_CONFIGURACAO_PRODUCAO_NEXORA.md`](context/US39_CONFIGURACAO_PRODUCAO_NEXORA.md).

| | Desenvolvimento (`compose.yaml` [+ `compose.dev.yaml`]) | Produção (`compose.prod.yaml`) |
|---|---|---|
| Frontend | Vite (HMR) na 127.0.0.1:5173 | arquivos estáticos compilados, servidos pelo Nginx |
| Backend | `dev` (Swagger ligado), 127.0.0.1:8080 | `prod` fixo (Swagger/OpenAPI off), **sem porta no host** |
| PostgreSQL | 127.0.0.1:5433 (`POSTGRES_PORT`) | **sem porta no host**, `max_connections` e memória limitados |
| Entrada pública | nenhuma (somente loopback) | só o `web`: 80 (redireciona/ACME) e 443 |
| Variáveis | `.env` (de `.env.example`) | `.env.prod` (de `.env.prod.example`), segredos próprios |

```bash
cp .env.prod.example .env.prod && chmod 600 .env.prod     # preencha (nunca versionado)
docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
```

- **Mesma origem, sem CORS:** o Nginx serve a SPA em `/` e encaminha `/api/` ao backend (método, query string,
  `Authorization: Bearer` e corpo intactos). O Axios usa `baseURL: '/api/v1'`. Nenhuma origem externa recebe
  `Access-Control-Allow-Origin` (o backend não tem CORS habilitado; coberto por `CorsPolicyTests` e pelo teste ponta a
  ponta). Não existe curinga.
- **HTTPS:** certificado real do Let's Encrypt via Certbot (HTTP-01, `scripts/prod/tls-issue.sh`), copiado para
  `TLS_CERT_DIR` (`fullchain.pem` + `privkey.pem`, fora do Git e da imagem). No primeiro boot, sem certificado, o Nginx
  usa um autoassinado **temporário** só para responder ao desafio. Renovação: `scripts/prod/tls-renew.sh` (timer
  systemd, seção 12 do guia), que recarrega o Nginx. HTTP redireciona para `https://$DOMAIN`.
- **HSTS** vem desligado (`HSTS_VALUE` vazio); ative só depois do HTTPS real estável.
- **CSP** forte: `script-src 'self'` e `style-src 'self'`, sem `unsafe-inline` nem `unsafe-eval`. O script do tema agora é o
  arquivo externo `nexora-web/public/theme-init.js`. Também: `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, `Permissions-Policy`, COOP/CORP.
- **Limite de tentativas:** login e cadastro (`/api/v1/auth/`) têm `limit_req` no Nginx, por IP (20/min + rajada de 20,
  `AUTH_RATE_LIMIT`), com 429 `TOO_MANY_REQUESTS` em JSON e `Retry-After`. Estado em memória (instância única). Atrás de NAT o
  IP é compartilhado.
- **Upload:** 5 MiB por PDF no frontend, Spring (`max-file-size`) e serviço; o Nginx corta em 6 MiB de corpo
  (`client_max_body_size`) com 413 JSON. PDFs ficam no volume `resumes_data` (só o backend o monta; sem rota pública).
- **Healthchecks:** PostgreSQL (`pg_isready`), backend (`/actuator/health/readiness` na porta interna 8081, sem
  detalhes, nunca publicada nem proxied) e Nginx (`/healthz`). Só `health` está exposto do Actuator.
- **Recursos (VPS ~4 GB):** limites `mem_limit` (768m/1g/128m), heap da JVM a 60% do limite, pool Hikari 10, PostgreSQL
  `max_connections=40`, logs em rotação (3 × 10 MB).
- **Backup:** `scripts/prod/backup.sh` (banco + PDFs no mesmo instante), `scripts/prod/restore-check.sh` (restaura num
  banco descartável). Cópia fora da VPS e restauração real: guia, seção 16. Volume Docker não é backup.
- **Dados:** nunca use `docker compose down -v` em produção. A V20 adiciona `users.version` (concorrência otimista) de
  forma aditiva.
- **Limitações:** instância única (rate limit e storage de PDFs locais); JWT no `sessionStorage` (decisão do MVP, mitigada
  pela CSP); o HTTPS público, o IP real atrás do Docker e o consumo na VPS real só serão comprovados na US-40.
