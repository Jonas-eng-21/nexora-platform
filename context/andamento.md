# Nexora — Andamento do desenvolvimento (AI Handoff Document)

> **Última atualização:** 2026-11-13, ao final da US-33 (reativação explícita de candidaturas), **a última da etapa 6**: **implementada, verificada, mesclada e enviada à `main`** (`cfd2b7b`). **A etapa 6 está inteira na `main`.**
> **Para quem é:** qualquer IA (ou pessoa) que assuma o desenvolvimento sem acesso à conversa anterior.
> **Como ler:** a seção 6 diz exatamente onde o trabalho parou; a seção 7 diz o que fazer em seguida. As seções 1 a 5 dão o contexto necessário para não quebrar decisões já tomadas.
>
> **Legenda de confiabilidade usada neste documento**
> - **[CONFIRMADO]** verificado no repositório ou por execução real (testes/HTTP) nesta data.
> - **[DECISÃO]** regra definida durante o desenvolvimento, que deve ser respeitada.
> - **[PLANEJADO]** ainda não implementado; é só intenção.
> - **[NÃO VERIFICADO]** não foi possível conferir.

---

## 1. Visão geral do projeto

**Nexora** é o MVP de um Trabalho de Conclusão de Curso (TCC): uma plataforma web que conecta estudantes de tecnologia a empresas de Garanhuns (PE) e região. O diferencial não é ser um mural de vagas, e sim combinar **currículo-base estruturado**, **validação de vínculo acadêmico por universidades**, **candidaturas** e **matching explicável** nos dois sentidos (vagas para o estudante, estudantes para a empresa).

Quatro perfis: `STUDENT`, `COMPANY`, `UNIVERSITY`, `ADMIN`.

Fluxo final esperado (demonstração): universidade se cadastra → admin aprova → estudante monta currículo e pede vínculo → universidade aprova → empresa publica vaga → sistema calcula score e recomenda → estudante se candidata (snapshot do currículo Nexora **ou** PDF de até 5 MiB) → empresa analisa → universidade revoga vínculo → candidaturas em andamento são suspensas → reativação explícita após nova validação. Tudo publicado com HTTPS.

### Stack [CONFIRMADO]

| Camada | Escolha |
|---|---|
| Backend | Java 21, Spring Boot **4.1.1**, Maven, Spring Security + JWT (HS256) |
| Persistência | PostgreSQL 18, Spring Data JPA / Hibernate, Flyway |
| Documentação da API | springdoc-openapi **3.2.0** (Swagger), só no perfil `dev` |
| Frontend | Vue 3 + TypeScript + Vite — **ainda não existe** |
| Infra | Docker + Docker Compose em Ubuntu WSL2; nada de Java/Maven/Node instalado no host |

### Documentos normativos (fonte de verdade) — pasta `context/`, **versionada desde 2026-11-14** (antes estava no `.gitignore`)

> **Atenção:** como a pasta vai para o GitHub, **nunca** escrever nesses documentos valores de segredos (senha do admin, `JWT_SECRET`, senha do banco, tokens). Cite só o nome da variável.

| Arquivo | Conteúdo |
|---|---|
| `ESCOPO_MVP_NEXORA.md` | O que desenvolver: RFs, RNFs, regras de negócio, modelo de dados mínimo |
| `HISTORIAS_USUARIO_MVP_NEXORA.md` | Backlog US-01 a US-43 em ordem de execução, com checkboxes de progresso |
| `DIRETRIZES_ENGENHARIA_IA_NEXORA.md` | Norma para IAs: TDD, KISS, YAGNI, fluxo por US, Definition of Done |
| `SYSTEM_DESIGN_NEXORA.md` | Camadas, módulos, segurança, contrato de erros |
| `guia-commit.md` | Conventional Commits em inglês, atômicos |
| `inicio-do-projeto.md` | Registro da containerização inicial |
| `Context Injection - projeto de TCC.md` | Contexto acadêmico do TCC |
| `andamento.md` | Este documento |

Divergências conhecidas entre os documentos e o código [CONFIRMADO]:
- O system design cita o pacote `br.com.nexora.api`; o pacote real é **`br.com.nexora`** (mantido de propósito).
- O system design cita a pasta `docs/`; os documentos estão em **`context/`**.

---

## 2. Arquitetura e organização

### 2.1. Repositório [CONFIRMADO]

```
nexora-platform/
├── compose.yaml            # postgres:18 + backend (imagem construída de ./nexora-api)
├── compose.dev.yaml        # override de hot reload — NUNCA foi testado
├── .env / .env.example     # .env não é versionado
├── README.md               # ambiente Docker, variáveis, Swagger, admin inicial, banco de testes
├── context/                # documentos normativos (versionados)
└── nexora-api/             # backend Spring Boot (monólito modular)
    ├── Dockerfile          # multi-stage Maven+JDK21 -> JRE21, build com -DskipTests
    ├── pom.xml
    └── src/
        ├── main/java/br/com/nexora/<módulo>/...
        ├── main/resources/application.yaml, application-dev.yaml
        ├── main/resources/db/migration/V1..V14
        └── test/java/br/com/nexora/<módulo>/...   + test/resources/config/application.yaml
```

Remoto: `git@github.com:Jonas-eng-21/nexora-platform.git`.

### 2.2. Módulos do backend (feature-first) [CONFIRMADO]

Cada módulo tem só as subpastas que possuem código: `controller/`, `dto/`, `service/`, `entity/`, `repository/`.

| Módulo | Responsabilidade | Classes principais |
|---|---|---|
| `shared/exception` | Contrato único de erros | `GlobalExceptionHandler`, `ApiException` |
| `shared/security` | JWT, RBAC, status da conta | `SecurityConfig`, `JwtConfig`, `TokenService`, `UserJwtAuthenticationConverter`, `AuthenticatedUser`, `AccountNotActiveException` |
| `shared/config` | OpenAPI | `OpenApiConfig` |
| `shared/dto` | Paginação | `PageResponse` |
| `shared/domain` | Vocabulário fixo compartilhado entre módulos + rota do catálogo de áreas | `ProfessionalArea`, `WorkModel`, `AreaCatalogController` |
| `users` | Conta: credenciais, papel, status | `User`, `UserRole`, `AccountStatus`, `UserRepository`, `UserAccountService`, `PasswordPolicy` |
| `authentication` | Registro e login | `AuthController`, `RegistrationService`, `LoginService` |
| `administration` | Admin inicial, gestão de contas, intervenções, auditoria | `AdminProvisioner`, `AdminUserService`, `AdminCompanyService`, `AdminUniversityService`, `AdminAffiliationService`, `AuditLogService`, `AdminAuditLog` |
| `students` | Perfil, currículo-base (JSONB), competências | `Student`, `resume/Resume`, `StudentProfileService`, `StudentProfileController` |
| `skills` | Catálogo de competências (módulo criado além da lista do system design) | `Skill`, `SkillService`, `SkillController` |
| `companies` | Perfil institucional da empresa | `Company`, `CompanyProfileService` |
| `universities` | Perfil, aprovação do cadastro, vínculo acadêmico, elegibilidade e eventos de domínio | `University`, `UniversityAffiliation`, `UniversityProfileService`, `UniversityApprovalService`, `AffiliationService`, `AffiliationDecisionService`, `AcademicEligibilityService`, `event/AcademicAffiliationRevoked`, `event/UniversitySuspended` |
| `jobs` | Vagas publicadas por empresas, competências exigidas/desejáveis, ciclo de vida e busca | `Job`, `JobSkill`, `JobStatus`, `JobLevel`, `SkillRequirement`, `JobService`, `JobController` |
| `matching` | Score explicável estudante–vaga, vagas recomendadas ao estudante e estudantes recomendados à empresa | `calculator/MatchCalculator` (função pura), `MatchingWeights`, `MatchInput`, `MatchResult`, `MatchingService`, `MatchingController`, `dto/JobMatchResponse`, `dto/RecommendedJobResponse`, `dto/RecommendedStudentResponse`, `dto/StudentSummary` |

| `applications` | Candidaturas com currículo da plataforma (snapshot) ou PDF privado, e download do PDF | `Application`, `ApplicationStatus`, `ResumeType`, `ResumeSnapshot`, `ApplicationRepository`, `ApplicationService`, `ResumeFileStorage`, `ApplicationController`, `dto/CreateApplicationRequest`, `JobApplicationService`, `ApplicationStatusService`, `ApplicationSuspensionService`, `listener/ApplicationSuspensionListener`, `ApplicationReactivationService`, `repository/ApplicationParties`, `dto/ApplicationReactivationResponse`, `entity/SuspensionReason`, `dto/ChangeApplicationStatusRequest`, `dto/ApplicationStatusResponse`, `dto/ApplicationResponse`, `dto/ApplicationSummaryResponse`, `dto/JobApplicationResponse` |

### 2.3. Direção das dependências entre módulos [DECISÃO]

Sem ciclos. Direção atual:

- `administration` → `users`, `companies`, `universities`, `jobs` (o admin chama os serviços de domínio e grava a auditoria).
- `jobs` → `companies`, `skills` (e lê `User` em consultas JPQL para checar se a empresa está ativa).
- `authentication` → `users`, `universities` (o cadastro de universidade cria o perfil institucional).
- `universities` → `students`, `users`.
- `students` → `skills`.
- `matching` → `students`, `jobs`, `skills`, `universities` (só `AcademicEligibilityService`). `administration` → `matching` (rota de leitura do admin). Ninguém mais depende de `matching`.
- `applications` → `students`, `jobs`, `universities` (`AcademicEligibilityService` e os eventos de `universities.event`) e `matching` (`MatchingService` e `StudentSummary`, desde a US-30). `administration` → `applications` (rota de leitura do admin). `matching` **não** depende de `applications`: sem ciclo.
- `shared/domain` guarda os enums usados por mais de um módulo (`ProfessionalArea`, `WorkModel`), para `students` não depender de `jobs`.
- Todos → `shared`. `users` não depende de ninguém além de `shared`.

Consequência prática: **módulos de domínio não chamam `AuditLogService`**. Toda ação de admin tem rota própria em `/api/v1/admin/...`, no módulo `administration`, que chama o serviço de domínio e audita na mesma transação.

### 2.4. Camadas [DECISÃO]

- **Controller:** só HTTP, `@Valid`, obtém o usuário via `@AuthenticationPrincipal AuthenticatedUser` (record com `id` da conta e `role`). Nunca aceita `userId`, `role`, `status` etc. do payload como autoridade.
- **Service:** regras, transações (`@Transactional`), exceções de negócio (`ApiException`). Classes concretas, sem interface.
- **Repository:** Spring Data JPA.
- **Entity:** construtor de domínio + métodos de mudança explícitos (ex.: `changeStatus`, `recordDecision`); construtor sem argumentos `protected` via Lombok; sem setters genéricos; sem cascatas.
- **DTO:** `record` com Bean Validation; conversão explícita com método estático `from(...)`. Sem MapStruct.

### 2.5. Banco de dados — migrações Flyway [CONFIRMADO]

`ddl-auto: validate`; o esquema é 100% Flyway. Aplicadas no banco de desenvolvimento até a **V18**.

| Versão | Conteúdo |
|---|---|
| V1 | `users` (e-mail único e obrigatoriamente minúsculo; CHECK de `role` e `status`) |
| V2 | `admin_audit_logs` (admin, ação, tipo e id do alvo, motivo, data) |
| V3 | `students` (um por conta; nome, curso, instituição, localização, resumo) |
| V4 | `students.resume` JSONB (currículo-base) |
| V5 | `skills` (com `normalized_name` único) + `student_skills` + **57 competências iniciais** |
| V6 | `companies` (um por conta) |
| V7 | `universities` (um por conta) |
| V8 | Aprovação da universidade (`approval_status`, motivo, autor, data) + status de conta `REJECTED` |
| V9 | `university_affiliations` + índices únicos parciais (1 `PENDING` e 1 `APPROVED` por estudante) |
| V10 | Decisão do vínculo (`decided_by`, `decided_at`, `decision_reason`) |
| V11 | Revogação do vínculo (`revoked_by`, `revoked_at`, `revocation_reason`) |
| V12 | `jobs` e `job_skills` (CHECKs de `work_model`, `level`, `status` e `requirement`) |
| V13 | Exclusão lógica em `jobs` (`deleted_at` + índice parcial `idx_jobs_company_active`) |
| V14 | `jobs.area` restrita ao catálogo fixo (texto livre antigo vira `OTHER`; CHECK; índice parcial `idx_jobs_area_open`) |
| V15 | `applications` (único por estudante+vaga; CHECKs de `resume_type` e `status`; `PLATFORM` exige `resume_snapshot`) |
| V18 | `applications.suspended_from_status`, `suspension_reason`, `suspended_at`; CHECKs: estado anterior só `SUBMITTED`/`IN_REVIEW`, motivo só os dois códigos, e **toda candidatura `SUSPENDED` traz os três campos** |
| V17 | `applications.status_reason` (motivo da decisão, até 500) e, em `admin_audit_logs`, `previous_value` e `new_value` (opcionais) |
| V16 | Colunas do PDF (`storage_key`, `original_filename`, `file_size`, `content_type`); CHECK de exclusividade snapshot×PDF e de 1 a 5.242.880 bytes; índice único parcial em `storage_key` |

Todas as PKs são `uuid` (`gen_random_uuid()` no banco; o Hibernate gera o UUID na aplicação).

### 2.6. API implementada [CONFIRMADO]

Prefixo `/api/v1`. Autenticação por `Authorization: Bearer <JWT>`.

| Rota | Método | Quem | Função |
|---|---|---|---|
| `/auth/register` | POST | público | Autocadastro (universidade exige objeto `university`) |
| `/auth/login` | POST | público | Login; devolve `accessToken`, `tokenType`, `expiresIn`, `role` |
| `/users/me` | GET, PATCH | autenticado | Conta própria; PATCH altera e-mail e/ou senha e exige `currentPassword` |
| `/students/me` | GET, PUT | STUDENT | Perfil (PUT cria ou substitui) |
| `/students/me/resume` | GET, PUT | STUDENT | Currículo-base (substituição integral) |
| `/students/me/skills` | GET, PUT | STUDENT | Competências técnicas do catálogo |
| `/skills` | GET | autenticado | Catálogo paginado, com `search` |
| `/skills` | POST | STUDENT, COMPANY, ADMIN | Adiciona ao catálogo (201 novo, 200 se já existir) |
| `/companies/me` | GET, PUT | COMPANY | Perfil da empresa |
| `/universities` | GET | autenticado | Catálogo de universidades ativas |
| `/universities/me` | GET, PUT | UNIVERSITY | Perfil institucional |
| `/affiliations` | POST | STUDENT | Solicita vínculo |
| `/affiliations/me` | GET | STUDENT | Histórico próprio |
| `/affiliations/me/eligibility` | GET | STUDENT | `{"eligible": bool}` |
| `/affiliations/received` | GET | UNIVERSITY | Pedidos recebidos (`status` opcional) |
| `/affiliations/{id}/approve` `/reject` `/revoke` | POST | UNIVERSITY (dona) | Decisão e revogação |
| `/admin/users` | GET | ADMIN | Lista com filtros `role`, `status` |
| `/admin/users/{id}` | GET | ADMIN | Consulta |
| `/admin/users/{id}/suspend` `/reactivate` | POST | ADMIN | Exige `reason`; auditado |
| `/admin/audit-logs` | GET | ADMIN | Auditoria, mais recente primeiro |
| `/admin/companies/{id}` | GET, PUT | ADMIN | Intervenção auditada |
| `/admin/universities` | GET | ADMIN | Lista (`approvalStatus` opcional) |
| `/admin/universities/{id}` | GET | ADMIN | Consulta |
| `/admin/universities/{id}/approve` `/reject` | POST | ADMIN | Aprovação do cadastro; auditado |
| `/admin/affiliations` | GET | ADMIN | Todos os pedidos |
| `/admin/affiliations/{id}/approve` `/reject` `/revoke` | POST | ADMIN | Exige `reason`; auditado |
| `/areas` | GET | autenticado | Catálogo fixo de áreas (`code`, `label`) |
| `/matching/jobs` | GET | STUDENT | Vagas recomendadas, da mais para a menos compatível (paginado; cada item traz `job` e `match`) |
| `/matching/jobs/{id}` | GET | STUDENT | Score explicável do próprio estudante com a vaga |
| `/matching/jobs/{id}/students` | GET | COMPANY (dona) | Estudantes recomendados para a vaga: só validados academicamente e com conta ativa, do mais para o menos compatível (paginado; cada item traz `student` autorizado e `match`) |
| `/admin/jobs/{id}/matching/students` | GET | ADMIN | Mesma lista, para qualquer vaga não excluída (leitura, sem auditoria) |
| `/jobs/{id}/applications` | POST | STUDENT | **`multipart/form-data`**: `resumeType=PLATFORM` ou `PDF` (+ campo `file`, até 5 MiB). 201 com `id`, `jobId`, `jobTitle`, `companyName`, `resumeType`, `resumeFileName`, `resumeFileSize` (nulos em PLATFORM), `status` (`SUBMITTED`), `createdAt` |
| `/applications/{id}/reactivate` | POST | COMPANY (dona) | Sem corpo. Devolve `id`, `previousStatus` (`SUSPENDED`), `status` (o restaurado) e `updatedAt`. Regras na seção 4.6 |
| `/admin/applications/{id}/reactivate` | POST | ADMIN | Mesmas regras; **auditada** (`APPLICATION_REACTIVATED`, de `SUSPENDED` para o restaurado) na mesma transação |
| `/applications/{id}/status` | PATCH | COMPANY (dona) | Corpo `{status, reason?}`; devolve `id`, `previousStatus`, `status`, `reason`, `updatedAt` (nunca o currículo). Regras na seção 4.6 |
| `/admin/applications/{id}/status` | PATCH | ADMIN | Mesmas transições e regras; **auditada** (`APPLICATION_STATUS_CHANGED`, com status anterior e novo) na mesma transação |
| `/jobs/{id}/applications` | GET | COMPANY (dona) | Candidaturas recebidas, todos os status, `appliedAt` e `id` decrescentes; filtro `status`; `page` (0), `size` (padrão 10, máx. 50). Itens: `id`, `status`, `resumeType`, `appliedAt`, `updatedAt`, `student` (`StudentSummary`) e `matchScore` (nulo se não calculável). Vaga de terceiros/excluída → 404 `JOB_NOT_FOUND` |
| `/admin/jobs/{id}/applications` | GET | ADMIN | Mesma lista, para qualquer vaga não excluída (leitura, sem auditoria) |
| `/applications/me` | GET | STUDENT | Próprias candidaturas, de qualquer status, `appliedAt` e `id` decrescentes; filtro `status`; `page` (0), `size` (padrão 10, máx. 50). Itens: `id`, `jobId`, `jobTitle`, `companyName`, `status`, `resumeType`, `appliedAt`, `updatedAt` |
| `/applications/{id}/resume` | GET | STUDENT (dono), COMPANY (dona da vaga), ADMIN | Currículo enviado: **JSON do snapshot** (PLATFORM) ou **PDF** (`application/pdf`, `Content-Disposition: attachment`); outros → 404 `APPLICATION_NOT_FOUND`; UNIVERSITY → 403. Sem rota de edição (PUT → 405) |
| `/jobs` | GET | autenticado | Lista vagas abertas de empresas ativas (filtros `search`, `area` — código do catálogo —, `location`, `workModel`, `level`, `skillId`) |
| `/jobs/{id}` | GET | autenticado | Detalhe da vaga (fechadas só visíveis à empresa dona e ao admin; excluídas retornam 404) |
| `/jobs` | POST | COMPANY | Publica vaga da própria empresa (`status` padrão `OPEN`) |
| `/jobs/mine` | GET | COMPANY | Lista vagas não excluídas da própria empresa (`status` opcional) |
| `/jobs/{id}` | PUT | COMPANY (dona) | Edita integralmente dados e competências da vaga |
| `/jobs/{id}/close` `/reopen` | POST | COMPANY (dona) | Encerra (`CLOSED`) ou reabre (`OPEN`) a vaga |
| `/jobs/{id}` | DELETE | COMPANY (dona) | Exclusão lógica (`deleted_at` preenchido, linha preservada) |
| `/admin/jobs/{id}` | PUT, DELETE | ADMIN | Intervenção auditada em vaga (`JOB_UPDATED`, `JOB_DELETED`) |
| `/admin/jobs/{id}/close` `/reopen` | POST | ADMIN | Intervenção auditada no status da vaga (`JOB_CLOSED`, `JOB_REOPENED`) |

Swagger (só no perfil `dev`): `http://localhost:8080/swagger-ui.html` e `/v3/api-docs`.

---

## 3. Histórico consolidado do desenvolvimento

O backend foi feito história por história, na ordem do backlog: US-01 a US-22 em 2026-11-11; correção da US-22 e US-23 a US-25 em 2026-11-12; US-26 a US-33 em 2026-11-13.

| US | Entrega | Observação relevante |
|---|---|---|
| US-01 | Tabela `users`, entidade, repositório, banco de testes `nexora_test` | Migrações incrementais (uma por história), não o esquema inteiro de uma vez |
| US-02 | Erros padronizados em `ProblemDetail` com `code` estável | Já criou o `SecurityConfig` mínimo para padronizar 401/403 |
| US-03 | JWT, RBAC, bloqueio por status | Concluída só na US-23, quando o critério de propriedade de vaga foi coberto |
| US-04 | Swagger só em `dev` | — |
| US-05 | `POST /auth/register` | Estudante/empresa `ACTIVE`; universidade `PENDING_APPROVAL`; `ADMIN` proibido |
| US-06 | `POST /auth/login` | Conta não ativa não recebe token |
| US-07 | `GET`/`PATCH /users/me` | "Dados básicos" interpretado como e-mail e senha |
| US-08 | Admin inicial por variáveis de ambiente | Idempotente; senha mínima de 12 caracteres |
| US-09 | Gestão de contas + auditoria | Tabela `admin_audit_logs` |
| US-10 | Perfil de estudante | Criado no primeiro `PUT`, não no cadastro |
| US-11 | Currículo-base em JSONB | Um único tipo `Resume` para API e banco |
| US-12 | Catálogo de competências + competências do estudante e da vaga | Concluída após `job_skills` na US-20 |
| US-13 | Perfil de empresa + intervenção do admin | — |
| US-14 | Perfil de universidade | **Mudou o contrato do registro** (decisão do usuário) |
| US-15 | Aprovação/rejeição de universidade pelo admin | Novo status de conta `REJECTED` |
| US-16 | Pedido de vínculo + catálogo de universidades | Índices únicos parciais |
| US-17 | Análise do pedido pela universidade e pelo admin | Regra de titularidade (404 para terceiros) |
| US-18 | Revogação com histórico preservado | — |
| US-19 | Serviço central de elegibilidade | `AcademicEligibilityService` |
| US-20 | Publicação de vagas com competências obrigatórias/desejáveis | Tabelas `jobs` e `job_skills` (V12) |
| US-21 | Edição, encerramento/reabertura e exclusão lógica de vaga + intervenção admin | Coluna `deleted_at` (V13) |
| US-22 | Listagem e consulta de vagas abertas com filtros e paginação | `GET /api/v1/jobs` e `GET /api/v1/jobs/{id}`. Foi commitada e enviada **sem a fase Green**; um teste falhava (corrigido em 2026-11-12, ver problema 9) |
| US-23 | Regra única "vaga aceita candidatura" + cobertura de propriedade e empresa inativa | `JobService.requireAcceptingApplications`; fechou também a **US-03** |
| US-24 | Score explicável + catálogo fixo de áreas + preferência de modelo de trabalho | **Mudou o contrato da vaga** (área) por decisão do usuário; novo módulo `matching` |
| US-25 | Vagas recomendadas ao estudante, ordenadas por score | Ordenação e paginação em memória (score não é gravado); teto de 500 vagas avaliadas |
| US-26 | Estudantes recomendados para a empresa dona da vaga (e para o admin) | Só elegíveis + conta ativa; dados autorizados mínimos; mesma ordenação em memória da US-25, teto de 500 perfis |
| US-27 | Candidatura com snapshot imutável do currículo Nexora | Novo módulo `applications`; migração V15; primeira história da etapa 6 |
| US-33 | Reativação explícita de candidatura suspensa (empresa dona e admin, com auditoria) | Sem migração; `AcademicEligibilityService.lockEligibility`; `JobService.isAcceptingApplications`; trava vínculo → conta → candidatura; encerra a etapa 6 |
| US-32 | Suspensão automática de candidaturas em andamento | Eventos internos síncronos entre `universities` e `applications`; `UPDATE` em lote; trava `FOR SHARE` na candidatura nova; V18; 3 testes antigos adaptados à nova restrição do banco |
| US-31 | Mudança de andamento pela empresa dona e pelo admin (com auditoria) | Primeira escrita de status; **estendeu a tabela de auditoria** (`previous_value`/`new_value`); migração V17; `PESSIMISTIC_WRITE` na decisão |
| US-30 | Candidaturas recebidas por vaga (empresa dona e admin) com `StudentSummary` e `matchScore` atual | `applications` passou a depender de `matching`; `MatchingService.currentScore`; sem migração |
| US-29 | Lista paginada das próprias candidaturas + leitura do snapshot em JSON | `PageResponse.pageable` ganhou teto por chamada; consulta com `join fetch` (sem N+1, teste mede); **o `GET /resume` de PLATFORM deixou de ser 404** (decisão do usuário) |
| US-28 | Candidatura com PDF privado + download autorizado | **Mudou o contrato da US-27** (JSON → multipart) por decisão do usuário; migração V16; volume Docker `resumes_data` |

### Problemas encontrados e soluções (podem se repetir)

1. **Máquina sem memória durante os testes.** Host com 3,8 GB de RAM e swap cheio; uma execução travou por mais de 10 minutos e falhou com `UnknownHostException: postgres` (DNS do Docker sem recursos). Não era defeito do código. **Solução adotada:** parar o container do backend antes de rodar a suíte e limitar o Maven com `MAVEN_OPTS=-Xmx256m`; religar o backend depois. O maior consumidor de RAM é o servidor de linguagem Java da IDE Antigravity do usuário (não mexer em processos do usuário).
2. **Build da imagem falhou uma vez sem mensagem** (as chamadas HTTP devolveram `HTTP 000`). Repetir o `docker compose build backend` resolveu. Sempre conferir no log a linha `Started NexoraApiApplication` antes de testar via HTTP.
3. **Testes transacionais com `MockMvc`:** teste e requisição compartilham a transação e o contexto de persistência. Antes de ler via `JdbcTemplate`, chamar `entityManager.flush()`; depois de alterar via JDBC, chamar `entityManager.clear()`.
4. **PostgreSQL aborta a transação na primeira instrução que falha.** Um teste com várias inserções inválidas seguidas mascara as demais. Regra: **uma inserção inválida por teste**.
5. **`@CreationTimestamp` só é preenchido no `INSERT`** (o UUID é gerado na aplicação, então o `INSERT` é adiado). Campo de data que precisa aparecer na resposta de criação deve ser definido no construtor da entidade, truncado em microssegundos (`Instant.now().truncatedTo(ChronoUnit.MICROS)`), como em `UniversityAffiliation`.
6. **Bean Validation `@Email` rejeita espaços nas pontas.** O e-mail é normalizado para minúsculas, mas espaços devolvem 400.
7. **Springdoc:** a versão 3.2.0 é a compatível com Spring Boot 4.1.x (conferido no Maven Central).
8. **Starters do Spring Boot 4 têm nomes novos:** ex.: `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-webmvc`. As anotações de teste por fatia mudaram de pacote; por isso os testes usam `@SpringBootTest` e montam o `MockMvc` manualmente (`MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()`).
9. **História marcada como concluída sem a suíte ter rodado (US-22).** A sessão anterior commitou, marcou `[x]` e enviou a US-22 sem executar a fase Green. Ao rodar a suíte, `JobSearchTests.studentListsOnlyOpenNonDeletedJobsFromActiveCompanies` falhava (esperava 1 vaga, vinham 2). A causa era o próprio teste: `entityManager.clear()` sem `flush()` antes descartava a exclusão lógica pendente (armadilha 3). O código de produção estava correto. **Lição: nunca marcar `[x]`, commitar como concluída ou fazer push antes de ver a suíte completa verde.**

10. **Execução da suíte com 16 erros "Failed to load ApplicationContext" (US-33), não reproduzida.** Uma execução completa, feita logo depois de uma longa rodada de mutações e que passou de 10 minutos, terminou com 347 testes e 16 erros de carga de contexto (nenhum teste de regra falhou). O log completo não foi guardado nessa execução, então a causa **não foi identificada**; duas execuções seguintes, com log completo, passaram 347/347 em cerca de 1 minuto, sem erro de contexto e sem sobras no banco. Suspeita (não confirmada): pouca memória da máquina. Se reaparecer, **guardar o log completo** (`mvn test > arquivo.log`) antes de investigar. Dica prática: comandos em primeiro plano têm limite de 10 minutos; rodadas longas (mutações, suíte repetida) devem ir em segundo plano e ser esperadas sem polling.

---

## 4. Decisões técnicas e regras estabelecidas

### 4.1. Processo por história (obrigatório) [DECISÃO]

Para **cada US**, nesta ordem:

1. Ler a US, as regras relacionadas nos documentos de `context/` e o código existente.
2. **Red:** escrever os testes e rodá-los, confirmando que falham pela ausência da funcionalidade.
3. **Green:** implementar o mínimo.
4. **Refactor** proporcional.
5. Rodar a **suíte completa** no Docker.
6. Reconstruir o backend e **verificar o fluxo real por HTTP** (curl) no banco de desenvolvimento; depois **apagar os dados de verificação** (o banco de desenvolvimento deve ficar só com o admin e o catálogo de skills).
7. Marcar `[x]` no backlog **somente** se todos os critérios de aceite foram atendidos e verificados; caso contrário, deixar `[ ]` com uma nota de progresso.
8. Commits atômicos e relatório ao usuário, incluindo decisões tomadas que não estavam nos documentos.

Nunca declarar sucesso sem execução real. Nunca enfraquecer um teste para ele passar (corrigir a causa; se o teste estava errado, corrigir o dado e manter a asserção).

### 4.2. Git e commits [DECISÃO]

- Conventional Commits **em inglês**, imperativo, atômicos. Sequência usual por US: `feat(...)` (código), `test(...)` (testes), `docs(readme)` (se houver).
- Escopos em uso: `db`, `api`, `application`, `auth`, `security`, `user`, `admin`, `student`, `skill`, `company`, `university`, `affiliation`, `job`, `matching`, `deps`, `readme`.
- Mudança de contrato leva `!` e rodapé `BREAKING CHANGE:`.
- Todo commit feito por IA termina com `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>` (o nome do modelo deve refletir a IA que realmente fez o commit).
- **Branches por etapa:** `feat/etapa-0-foundation`, `feat/etapa-1-identity`, `feat/etapa-2-profiles`, `feat/etapa-3-affiliations`, `feat/etapa-4-jobs`, `feat/etapa-5-matching` (em uso).
- **Fluxo combinado com o usuário:** a cada US concluída, a IA apresenta o relatório e pergunta se deve mesclar; o usuário responde "Sim, mesclar e seguir". Só então: `git switch main` → `git merge --ff-only <branch>` → `git push origin main` → volta para a branch (ou cria a da nova etapa) e começa a próxima US. **Não fazer merge nem push sem essa confirmação.**

### 4.3. Testes [DECISÃO]

- Testes de integração com `@SpringBootTest` + `@Transactional` (rollback automático) + `MockMvc` com Spring Security real.
- Banco de testes separado: **`nexora_test`**, no mesmo container PostgreSQL (criado uma vez com `createdb`). Sem Testcontainers.
- Autenticação nos testes: criar o `User` pelo repositório e emitir o token com `tokenService.issue(user)`.
- Controllers só de teste em `src/test` (`ErrorContractTestController`, `SecurityTestController`).
- Segredo JWT dos testes em `src/test/resources/config/application.yaml`.
- Cada conjunto de testes cobre: caminho feliz, validação (400), papéis errados (403), sem token (401), isolamento entre donos e não vazamento de dados.
- **Estado atual: 347 testes, todos passando** [CONFIRMADO na última execução, US-33, em 2026-11-13]. Há **três classes sem `@Transactional`** (gravam de verdade, para provar rollback e concorrência) e se limpam por prefixo de e-mail: `AdminApplicationStatusRollbackTests` (`rollback31-`), `ApplicationSuspensionTransactionTests` (`suspension32-`) e `ApplicationReactivationTransactionTests` (`reactivation33-`); se uma execução for interrompida, conferir sobras em `nexora_test`. Padrão para falha injetada: `@MockitoSpyBean` + `doThrow`, com o stub preparado dentro de um `TransactionTemplate` (o método é `MANDATORY` e a chamada de preparo passa pelo proxy transacional). Padrão para concorrência: uma thread executa a operação num `TransactionTemplate` e **pausa antes do commit**; outra faz a requisição e o teste confere que ela fica bloqueada (`TimeoutException` em 1,5 s) e qual é o resultado depois da liberação. O bloqueio de 1,5 s pode passar por engano numa partida a frio; quem decide é o resultado depois da liberação. Os testes de PDF usam a pasta `${java.io.tmpdir}/nexora-test-resumes` (config de teste) e a esvaziam ao terminar.
- Regra de cálculo pura (sem banco) é testada por **teste unitário** sem Spring (`MatchCalculatorTests`); o restante continua em teste de integração.

Comando da suíte (com o backend parado para poupar memória):

```bash
cd ~/workspace/nexora-platform
docker compose stop backend
set -a && . ./.env && set +a
docker run --rm --network nexora_default -v "$PWD/nexora-api:/w" -v nexora_m2:/root/.m2 -w /w \
  -e MAVEN_OPTS="-Xmx256m" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/nexora_test \
  -e SPRING_DATASOURCE_USERNAME=$POSTGRES_USER -e SPRING_DATASOURCE_PASSWORD=$POSTGRES_PASSWORD \
  maven:3.9-eclipse-temurin-21 mvn -B test
```

Uma classe só: acrescentar `-Dtest=NomeDaClasse`. Religar o backend: `docker compose build backend && docker compose up -d backend`.

### 4.4. Contrato de erros [DECISÃO]

Toda resposta de erro é `application/problem+json` (`ProblemDetail`) com o campo extra **`code`** estável, usado pelo frontend. Erros de validação trazem `errors: [{field, message}]` (o caminho do campo inclui aninhamento, ex.: `university.name`, `experiences[0].company`).

- Regra de negócio violada → **409** (o 422 **não** é usado).
- Recurso de outro dono → **404** com o código "não encontrado" do recurso (não revela existência).
- Papel errado → 403 `ACCESS_DENIED`. Sem token ou token inválido → 401 `UNAUTHENTICATED`.
- Conta não ativa com token válido → 403 `ACCOUNT_SUSPENDED` / `ACCOUNT_PENDING_APPROVAL` / `ACCOUNT_REJECTED`.
- Erros padrão do Spring MVC recebem o nome do status como `code` (`BAD_REQUEST`, `NOT_FOUND`...).
- As mensagens `detail` são em português; as mensagens por campo do Bean Validation ainda saem em inglês (pendência cosmética, ver 5.3).
- Logs nunca contêm senha, token nem mensagem bruta do banco (que pode conter e-mail).

Códigos de negócio existentes: `VALIDATION_ERROR`, `DATA_CONFLICT`, `INTERNAL_ERROR`, `ROLE_NOT_ALLOWED`, `PASSWORD_TOO_LONG`, `EMAIL_ALREADY_REGISTERED`, `UNIVERSITY_DATA_REQUIRED`, `UNIVERSITY_DATA_NOT_ALLOWED`, `INVALID_CREDENTIALS`, `CURRENT_PASSWORD_INVALID`, `USER_NOT_FOUND`, `INVALID_STATUS_TRANSITION`, `CANNOT_SUSPEND_SELF`, `STUDENT_PROFILE_NOT_FOUND`, `SKILL_NOT_FOUND`, `COMPANY_PROFILE_NOT_FOUND`, `COMPANY_NOT_FOUND`, `UNIVERSITY_PROFILE_NOT_FOUND`, `UNIVERSITY_NOT_FOUND`, `UNIVERSITY_ALREADY_DECIDED`, `UNIVERSITY_NOT_ACTIVE`, `AFFILIATION_ALREADY_PENDING`, `AFFILIATION_ALREADY_APPROVED`, `AFFILIATION_NOT_FOUND`, `AFFILIATION_ALREADY_DECIDED`, `AFFILIATION_NOT_APPROVED`, `STUDENT_NOT_VALIDATED`, `JOB_NOT_FOUND`, `JOB_SKILL_CONFLICT`, `JOB_NOT_ACCEPTING_APPLICATIONS`, `APPLICATION_ALREADY_EXISTS`, `APPLICATION_NOT_FOUND`, `RESUME_FILE_REQUIRED`, `RESUME_FILE_NOT_ALLOWED`, `INVALID_RESUME_FILE`, `RESUME_FILE_TOO_LARGE`, `INVALID_APPLICATION_STATUS_TRANSITION`, `APPLICATION_SUSPENDED`, `REJECTION_REASON_REQUIRED`, `APPLICATION_NOT_SUSPENDED`, `INVALID_SUSPENSION_STATE`, `STUDENT_ACCOUNT_NOT_ACTIVE`, `JOB_NOT_AVAILABLE`.

### 4.5. Segurança [DECISÃO]

- JWT HS256, validade padrão de 60 min (`JWT_EXPIRATION`). **O token carrega só o `sub` (id da conta).** Papel e status são lidos do banco **a cada requisição** (`UserJwtAuthenticationConverter`), então suspensão vale mesmo com token válido.
- Não há refresh token nem revogação de token (fora do escopo). Após troca de senha, tokens antigos valem até expirar.
- RBAC fixo: `/api/v1/admin/**` exige `ADMIN` no `SecurityConfig`; o resto usa `@PreAuthorize` nos controllers. A titularidade é verificada nos services.
- Rotas públicas: `/api/v1/auth/**` e as do Swagger.
- Senhas com BCrypt; de 8 a 72 caracteres e no máximo 72 **bytes** (`PasswordPolicy`).
- Login: mesma resposta para e-mail inexistente e senha errada, com comparação contra um hash fictício; o status da conta só é revelado a quem acertou a senha.
- API stateless, CSRF desligado.
- Não há limite de tentativas de login (sugestão para a US-39).

### 4.6. Regras de negócio já implementadas [DECISÃO]

**Contas**
- Status de conta: `ACTIVE`, `PENDING_APPROVAL`, `SUSPENDED`, `REJECTED`. O `REJECTED` foi acrescentado na US-15 (o escopo listava só os três primeiros).
- Qualquer conta não `ACTIVE` é bloqueada em **todas** as rotas protegidas e no login. Universidade pendente **não autentica**.
- Suspender só de `ACTIVE`; reativar só de `SUSPENDED`. Contas `PENDING_APPROVAL` e `REJECTED` ficam fora dessas rotas, para a aprovação de universidade não ser contornada.
- O admin não pode suspender a própria conta.
- O admin só nasce pelo `AdminProvisioner` (variáveis `ADMIN_EMAIL`/`ADMIN_PASSWORD`), nunca pelo cadastro. Mudar a senha no `.env` depois de criado não tem efeito.

**Universidade** (decisão explícita do usuário na US-14)
- Os dados institucionais são informados **no cadastro**: `POST /auth/register` com `role: UNIVERSITY` exige o objeto `university` (nome obrigatório; sigla, localização, site, descrição opcionais). Outros papéis não podem enviá-lo.
- A alternativa descartada foi "login limitado enquanto pendente", por exigir reabrir a regra de segurança.
- Aprovação do cadastro: decisão única (só de `PENDING`), com motivo, autor e data gravados na universidade e na auditoria. Aprovar → conta `ACTIVE`. Rejeitar (motivo obrigatório) → conta `REJECTED`, definitivo (não há reanálise).
- Estado de aprovação da universidade (`approval_status`) e status da conta são campos distintos.
- "Universidade ativa" = conta `ACTIVE`.

**Vínculo acadêmico**
- Estados: `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`. Nenhuma linha é apagada.
- Só se pede vínculo a universidade ativa, e o estudante precisa ter perfil.
- No máximo um `PENDING` e um `APPROVED` por estudante (índices únicos parciais + checagem no serviço). Com `APPROVED`, não pode abrir novo pedido. Após rejeição ou revogação, pode.
- Só a universidade destinatária decide/revoga; pedido de outra universidade responde 404. O admin decide/revoga qualquer um pelas rotas `/admin/affiliations`, sempre com motivo e auditoria.
- Decisão só de `PENDING`; revogação só de `APPROVED`. Rejeição e revogação exigem motivo, que o estudante vê.
- Decisões e revogações bloqueiam a linha (`PESSIMISTIC_WRITE`).
- **Elegibilidade acadêmica:** vínculo `APPROVED` + universidade com conta `ACTIVE`. Regra única em `AcademicEligibilityService` (`isEligible(studentId)` e `requireEligible(studentId)`, que lança 403 `STUDENT_NOT_VALIDATED`). `studentId` é o id do **perfil** (`students.id`), não o da conta. Candidaturas, suspensão e reativação **devem usar esse serviço**, nunca repetir a regra.

**Perfis e currículo**
- Perfis de estudante e empresa são criados no primeiro `PUT` (não no cadastro) e substituídos por inteiro a cada `PUT`; campo opcional ausente ou em branco vira `null`.
- Currículo-base: documento `Resume` (formação, experiências, projetos, cursos, soft skills, interesses, links) gravado em JSONB. O mesmo tipo é contrato da API e formato persistido (exceção consciente à regra "DTO separado de entidade", por ser um documento de valor). Preferir **acrescentar campos opcionais** a renomear/remover, pois ele será a origem dos snapshots de candidatura.
- Competências técnicas **não** ficam no JSONB: vêm do catálogo (`skills`), via `student_skills`.
- O `Resume` tem dois campos que alimentam o matching, ambos opcionais e acrescentados na US-24: `interestAreas` (lista de `ProfessionalArea`) e `preferredWorkModels` (lista de `WorkModel`; vazia = sem preferência). O campo antigo `interests` (texto livre) foi **mantido como descritivo** e não entra no cálculo. Currículos gravados antes desses campos continuam carregando.
- Catálogo: deduplicação por nome normalizado (minúsculas, sem acentos, espaços simples). Estudante, empresa e admin podem adicionar. Não há edição nem remoção.
- A "instituição" do perfil do estudante é texto livre e não equivale ao vínculo validado.

**Vagas**
- **Área da vaga vem do catálogo fixo `ProfessionalArea`** (16 valores: `BACKEND`, `FRONTEND`, `FULLSTACK`, `MOBILE`, `DATA`, `ARTIFICIAL_INTELLIGENCE`, `DEVOPS_CLOUD`, `INFRASTRUCTURE`, `SECURITY`, `QA`, `UX_UI`, `EMBEDDED_IOT`, `GAMES`, `PRODUCT_MANAGEMENT`, `SUPPORT`, `OTHER`). Texto livre devolve 400. Os nomes das constantes são gravados no banco: acrescentar é livre; renomear/remover exige migração (e ajuste do CHECK da V14).
- Enums: status `OPEN`/`CLOSED`; modelo `ON_SITE`/`REMOTE`/`HYBRID`; nível `INTERNSHIP`/`TRAINEE`/`JUNIOR`/`MID`/`SENIOR`; competência `REQUIRED`/`DESIRED`. Os documentos não fixavam esses valores.
- A empresa dona vem do token e nunca muda; exige perfil de empresa. A mesma competência não pode ser obrigatória e desejável (400 `JOB_SKILL_CONFLICT`).
- Só a dona opera a vaga; vaga de outra empresa responde 404 `JOB_NOT_FOUND`. O admin intervém por `/admin/jobs`, com motivo e auditoria.
- Exclusão é lógica (`deleted_at`); vaga excluída some de todas as consultas, inclusive para a dona e o admin.
- Listagem e detalhe públicos mostram só vaga `OPEN`, não excluída, de empresa com conta `ACTIVE`. Vaga encerrada ou de empresa suspensa só é visível à dona e ao admin.
- **"Vaga aceita candidatura"**: regra única em `JobService.requireAcceptingApplications(jobId)`, que devolve a `Job` ou lança 404 `JOB_NOT_FOUND` (inexistente/excluída) ou 409 `JOB_NOT_ACCEPTING_APPLICATIONS` (encerrada ou empresa não ativa). O módulo de candidaturas **deve usar esse método**, nunca repetir a regra.
- Empresa suspensa não opera nenhuma rota de vagas (403 `ACCOUNT_SUSPENDED`), mesmo com token válido.

**Matching** (decisões explícitas do usuário na US-24)
- Score de 0 a 100, calculado na hora (nada é gravado), por `MatchCalculator`: função pura e determinística, sem IA. Versão do algoritmo: `v1` (constante `ALGORITHM_VERSION`; mudar ao alterar critérios).
- Pesos em `application.yaml` (`nexora.matching.weights`): obrigatórias **45**, desejáveis **20**, interesses **20**, modelo e localização **15**. Devem somar 100 ou a aplicação não sobe. São hipóteses de implementação, **não** validação científica.
- Obrigatórias e desejáveis: proporcional às atendidas. **Vaga sem desejáveis:** o critério fica fora (`applicable: false`) e a nota é renormalizada sobre os pesos aplicáveis.
- Interesses: pontua tudo se a área da vaga está em `interestAreas` do estudante; senão, zero. A alternativa descartada foi comparar textos livres.
- Modelo e localização: metade pelo modelo (vaga entre os aceitos, **ou** estudante sem preferência) e metade pela localização (vaga `REMOTE` sempre pontua; presencial/híbrida pontua se a cidade do perfil do estudante é a mesma da vaga). Cidade = trecho antes de vírgula, barra ou " - ", sem acentos e em minúsculas.
- Só o currículo-base, o perfil e as competências do catálogo entram no cálculo. **PDF de candidatura nunca entra.**
- A resposta traz nota, pontuação e explicação por critério, competências aderentes/ausentes e um resumo em texto.
- `MatchingService.calculate(Student, Job)` é o ponto único de cálculo para qualquer par; as US-25 e US-26 o usam.
- **Vagas recomendadas (US-25):** `GET /matching/jobs` avalia as vagas `OPEN`, não excluídas, de empresas ativas (via `JobService.listOpenJobs`), ordena por score decrescente e, no empate, pela mais recente e depois pelo id (ordem estável), e pagina **em memória** — o score não está no banco. Teto: as **500** vagas abertas mais recentes (`MAX_EVALUATED_JOBS`); acima disso, vagas mais antigas não entram no ranking (limitação aceita para o MVP). Exige perfil de estudante.

- **Estudantes recomendados (US-26, decisões do usuário em 2026-11-13):** `GET /matching/jobs/{jobId}/students` (COMPANY dona; vaga de terceiros, excluída ou inexistente → 404 `JOB_NOT_FOUND`; vaga encerrada da própria empresa é consultável) e `GET /admin/jobs/{jobId}/matching/students` (ADMIN, vaga não excluída; leitura, **sem auditoria**). Entram **somente** estudantes academicamente elegíveis (`AcademicEligibilityService.eligibleStudentIds()`) **e** com conta `ACTIVE`. A empresa vê só `StudentSummary`: `id` do perfil, `fullName`, `course`, `location`, `summary`, nomes das `skills` do catálogo e `interestAreas` — **nunca** e-mail, id da conta, `institution`, links ou o currículo (que só chega à empresa por candidatura, US-30). Ordem: score decrescente, depois perfil mais recente, depois id; paginação em memória, teto de 500 perfis elegíveis mais recentes (`MAX_EVALUATED_STUDENTS`). A regra de elegibilidade existe em duas consultas JPQL (`existsApprovedWithActiveUniversity` e `findEligibleStudentIds`); **mudem juntas**.

**Candidaturas (US-27)**
- `POST /jobs/{jobId}/applications`, só STUDENT. Ordem das checagens: perfil de estudante (404 `STUDENT_PROFILE_NOT_FOUND`) → `AcademicEligibilityService.requireEligible` (403 `STUDENT_NOT_VALIDATED`) → `JobService.requireAcceptingApplications` (404 `JOB_NOT_FOUND` / 409 `JOB_NOT_ACCEPTING_APPLICATIONS`) → duplicidade (409 `APPLICATION_ALREADY_EXISTS`; sob concorrência, a unicidade do banco devolve 409 `DATA_CONFLICT`). Status inicial `SUBMITTED`.
- **Snapshot** (`ResumeSnapshot`, JSONB, `updatable = false`, sem setter): `profile` (nome, curso, instituição, localização, resumo), `resume` (o `Resume` inteiro) e `skills` (`id` e `name` das competências do catálogo, ordem alfabética). **Nunca** inclui e-mail nem id da conta. Mudanças de estrutura afetam snapshots já gravados: só acrescentar campos opcionais.
- **Contrato (US-28, decisão do usuário):** o `POST` é `multipart/form-data`; JSON deixou de ser aceito. `resumeType` ausente ou desconhecido → 400 `VALIDATION_ERROR`. A escolha é exclusiva: `PDF` sem arquivo → 400 `RESUME_FILE_REQUIRED`; `PLATFORM` com arquivo → 400 `RESUME_FILE_NOT_ALLOWED`.
- **Validação do PDF** (`ApplicationService`, antes de gravar qualquer coisa): tamanho ≤ **5.242.880 bytes** (413 `RESUME_FILE_TOO_LARGE`, também pelo limite do Tomcat em `spring.servlet.multipart`, tratado em `GlobalExceptionHandler`), extensão `.pdf`, `Content-Type` `application/pdf` e assinatura `%PDF-` (400 `INVALID_RESUME_FILE`). Não há validação estrutural completa do PDF (decisão consciente do MVP).
- **Armazenamento** (`ResumeFileStorage`, classe concreta): pasta `nexora.storage.resumes-dir` (`RESUMES_STORAGE_DIR`, padrão `/data/resumes`), nome interno `UUID.pdf` gerado no servidor, só chaves nesse formato são resolvidas (sem path traversal). No Docker é o volume nomeado `resumes_data`, montado só no backend; o `Dockerfile` cria `/data/resumes` com dono `nexora`. Se a gravação no banco falha depois de o arquivo ser salvo, o arquivo é removido. Nenhuma rota pública serve a pasta.
- **Metadados** na candidatura: `storage_key` (nunca exposto), `original_filename` (saneado: só o último trecho do caminho, sem caracteres de controle, até 150), `file_size`, `content_type`. A candidatura `PDF` não tem snapshot.
- **Download** `GET /applications/{id}/resume`: estudante dono, empresa dona da vaga e ADMIN; os demais, 404 `APPLICATION_NOT_FOUND` (não revela existência); UNIVERSITY, 403. Continua disponível depois que o estudante perde a validação (documentos são preservados). Entrega como anexo, com `nosniff` e `no-store` do Spring Security.
- O **score nunca lê o PDF** (teste cobre). A resposta de criação não traz o snapshot.
- **Lista do estudante (US-29):** `GET /applications/me`, só STUDENT. Filtra por `student.userId` = conta do token (nenhum `studentId`/`userId` do cliente é lido); sem perfil, devolve página vazia (200). Mostra **todos** os status. Ordem fixa por `createdAt` (exposto como `appliedAt`) e `id` decrescentes; `page` mínimo 0, `size` padrão 10, máximo 50 (ajustado, não rejeitado, como nas demais listas; `PageResponse.pageable(page, size, maxSize, sort)`). `status` inválido → 400. Consulta com `join fetch` de vaga e empresa: o teste `listingLoadsJobsAndCompaniesWithoutOneQueryPerRow` falha sem ele (verificado por mutação). Sem `matchScore` e sem dependência de `matching` (decisão do usuário). Nota de nomes: a **criação** continua devolvendo `createdAt` (contrato da US-27/28); só a lista usa `appliedAt`.
- **Leitura do currículo enviado:** `GET /applications/{id}/resume` devolve o snapshot em JSON (PLATFORM) ou o PDF. A regra de acesso é uma só para os dois (estudante dono, empresa dona da vaga, ADMIN), então a empresa e o admin também leem o snapshot por esta rota; as listas da empresa, o score da candidatura e os status continuam sendo a US-30/US-31. O código `RESUME_FILE_NOT_FOUND` deixou de existir.

- **Candidaturas recebidas (US-30):** `GET /jobs/{jobId}/applications` (COMPANY dona, via `JobService.getOwnedActiveJob`; encerrada continua consultável, excluída é 404) e `GET /admin/jobs/{jobId}/applications` (ADMIN, `JobService.getActiveJobById`; leitura, **sem auditoria**, `POST`/`PUT`/`DELETE` → 405). Um só serviço (`JobApplicationService`) atende as duas. Mostra todos os status, inclusive de estudantes que perderam a validação. O item traz `StudentSummary` (US-26, sem e-mail, links, instituição ou currículo) e **nunca** o snapshot, o PDF ou o nome do arquivo.
- **`matchScore`:** compatibilidade **atual** (não a do envio), calculada por `MatchingService.currentScore`, que reutiliza `calculate` e só lê o currículo-base e as competências do catálogo (o PDF nunca entra). Não é gravada. **É nulo quando o currículo-base não tem nenhuma competência do catálogo nem área de interesse** — regra definida na implementação, porque o calculador é total e sempre devolveria um número (só os 15 pontos de modelo/localização); as listas das US-24/25/26 **não** usam essa regra e continuam devolvendo número. Se for preciso mudar o critério de "não calculável", é uma condição em um único método.
- **Sem N+1:** a consulta traz o estudante com `join fetch` e as competências dele e da vaga vêm em lote (`default_batch_fetch_size: 50`, igual ao tamanho máximo de página): **7 comandos SQL por página, com 1 ou com 6 candidaturas**. O teste `listingLoadsStudentsAndScoresWithoutOneQueryPerRow` falha se uma consulta por linha for introduzida (verificado por mutação: 7 → 12). Atenção: ele **não** falha ao remover só o `join fetch`, porque o lote já cobre a página; o `join fetch` poupa um SELECT, mas não é o que impede o N+1. Se `default_batch_fetch_size` for reduzido abaixo de 50, revisar.
- **Sem IA externa:** nenhuma chamada a Gemini ou outro provedor na listagem; a análise semântica (US-43) precisará de estratégia própria de reaproveitamento de resultados.

**Reativação explícita (US-33)**
- `POST /applications/{id}/reactivate` (COMPANY dona: `ApplicationReactivationService.reactivateAsCompany`; outra empresa ou inexistente → 404 `APPLICATION_NOT_FOUND`) e `POST /admin/applications/{id}/reactivate` (ADMIN: `AdminApplicationService.reactivate` → `reactivateById`, auditada na mesma transação como `APPLICATION_REACTIVATED`, `previous_value = SUSPENDED`, `new_value` = o estado restaurado, sem motivo). Sem corpo; nunca automática. Nenhum endpoint para estudante ou universidade.
- **Ordem das checagens:** 409 `APPLICATION_NOT_SUSPENDED` → 409 `INVALID_SUSPENSION_STATE` (estado guardado ausente ou fora de `SUBMITTED`/`IN_REVIEW`; defesa em profundidade, pois a V18 já o impede — o teste derruba as restrições dentro da transação de teste para exercitá-la) → 409 `STUDENT_ACCOUNT_NOT_ACTIVE` (**código definido na implementação**: a decisão não dava um) → 403 `STUDENT_NOT_VALIDATED` (elegibilidade acadêmica, regra única de `AcademicEligibilityService`) → 409 `JOB_NOT_AVAILABLE` (`JobService.isAcceptingApplications`: vaga existente, não excluída, `OPEN` e de empresa `ACTIVE`; **vaga excluída também é 409**, diferente do 404 do `apply`). Nada é alterado se qualquer uma falhar.
- **Efeito:** `Application.reactivate()` restaura **exatamente** `suspended_from_status` e limpa `suspended_from_status`, `suspension_reason` e `suspended_at` juntos (a restrição da V18 só exige os três quando `SUSPENDED`, então não há migração); `status_reason`, o snapshot e os dados do PDF não são tocados; `updated_at` é renovado. Uma nova revogação ou suspensão de universidade depois da reativação suspende a candidatura de novo (US-32), com dados novos.
- **Trava e concorrência (mesma ordem global: vínculo → conta → candidatura):** a reativação toma primeiro as travas compartilhadas de elegibilidade (`AcademicEligibilityService.lockEligibility`, que informa sem lançar erro para manter a precedência acima) e só depois lê a candidatura com `FOR UPDATE`, **sem ter carregado a entidade antes** (a titularidade vem de `ApplicationRepository.findParties`, uma projeção). Resultado: revogação ou suspensão de universidade em andamento vence a reativação (403); reativação concluída é vista e suspensa de novo pela revogação ou suspensão que vier depois; duas reativações simultâneas restauram uma única vez (a segunda recebe 409 `APPLICATION_NOT_SUSPENDED`); a mudança de status da empresa espera a reativação e age sobre o estado restaurado.

**Suspensão automática (US-32)**
- **Gatilhos:** (1) revogação de vínculo aprovado pela universidade ou pelo admin → `AffiliationDecisionService.revoke` publica `AcademicAffiliationRevoked(studentId)`; (2) suspensão da conta de uma universidade ativa → `AdminUserService.suspend` publica `UniversitySuspended(universityUserId)` quando o alvo é `UNIVERSITY`. **Qualquer novo caminho que suspenda uma conta de universidade ou revogue um vínculo precisa publicar o mesmo evento.**
- **Entrega:** `ApplicationSuspensionListener` usa `@EventListener` comum: síncrono, na thread e na transação de quem publicou, então uma falha desfaz a revogação ou a suspensão. **Proibido** `@Async`, `@TransactionalEventListener(AFTER_COMMIT)` e multicaster com executor (um teste confere que o multicaster não tem executor). `ApplicationSuspensionService.suspendInProgress` é `MANDATORY`: sem transação, falha em vez de seguir pela metade.
- **Efeito:** um único `UPDATE` em lote (`ApplicationRepository.suspendInProgress`) leva `SUBMITTED` e `IN_REVIEW` a `SUSPENDED`, guardando `suspended_from_status` (= o status de antes, no mesmo SET), `suspension_reason` (código, nunca texto livre) e `suspended_at` (e `updated_at`, que o lote não preenche sozinho). `APPROVED`/`REJECTED` e candidaturas já suspensas não casam com o WHERE e ficam intactas (idempotente; a primeira suspensão preserva seus dados). `status_reason` (a última decisão ordinária) não é tocado. O lote **não** usa `clearAutomatically`: se um dia houver `Application` carregada antes na mesma transação, ela ficará desatualizada.
- **Suspensão de universidade:** afeta só estudantes com vínculo `APPROVED` com aquela universidade (`AcademicEligibilityService.approvedStudentIdsOf`); os **vínculos não são revogados** (continuam `APPROVED`, porém inelegíveis enquanto a conta estiver suspensa, e voltam a valer na reativação); reativar a universidade **não** reativa candidaturas (a US-33 fará, de forma explícita).
- **Auditoria:** nenhuma entrada por candidatura; as ações de admin (`AFFILIATION_REVOKED`, `USER_SUSPENDED`) continuam auditadas como antes.
- **Concorrência e ordem de travas (vínculo → conta → candidaturas):** (a) `ApplicationService.apply` usa `AcademicEligibilityService.requireEligibleLocked`, que lê o vínculo aprovado e a conta da universidade com `FOR SHARE` até o fim da transação; a revogação (`FOR UPDATE` no vínculo) e a suspensão (`UPDATE` na conta) esperam essa candidatura terminar — e então a suspendem — ou terminam antes e a leitura falha com 403. Sem isso, uma candidatura criada durante a revogação escaparia do `UPDATE` em lote (fantasma). (b) A mudança de status (US-31) não precisa dessa trava: já trava a linha da candidatura (`FOR UPDATE`), e o `UPDATE` em lote espera essa trava e reavalia o WHERE (se a empresa decidiu antes, a candidatura final não é suspensa; se a suspensão veio antes, a empresa recebe 409 `APPLICATION_SUSPENDED`). Qualquer código novo que crie candidaturas em andamento ou mude a elegibilidade deve respeitar essa ordem para não gerar deadlock.
- **A regra de elegibilidade agora existe em quatro consultas** (`existsApprovedWithActiveUniversity`, `findEligibleStudentIds` e as duas leituras com trava `findApprovedByStudentIdForShare` + `UserRepository.findActiveByIdForShare`): **mudem juntas**.

**Andamento da candidatura (US-31)**
- `ApplicationStatus.canMoveTo` é a tabela única: `SUBMITTED` → `IN_REVIEW`/`APPROVED`/`REJECTED`; `IN_REVIEW` → `APPROVED`/`REJECTED`; todo o resto é inválido (`APPROVED`/`REJECTED` finais, inclusive para si mesmos; ninguém entra em `SUSPENDED` nem volta a `SUBMITTED` por aqui).
- Um só serviço (`ApplicationStatusService`) atende a empresa (`changeAsCompany`, ownership pelo token; de outra empresa ou inexistente → 404 `APPLICATION_NOT_FOUND`) e o admin (`changeById`, chamado por `AdminApplicationService`, que audita na mesma transação). A leitura usa `PESSIMISTIC_WRITE` (`findByIdForUpdate`) para duas decisões simultâneas não se sobreporem.
- **Ordem das checagens:** suspensa (409 `APPLICATION_SUSPENDED`) → transição (409 `INVALID_APPLICATION_STATUS_TRANSITION`) → motivo da rejeição (400 `REJECTION_REASON_REQUIRED`; branco ou só espaços conta como vazio) → elegibilidade acadêmica atual via `AcademicEligibilityService.requireEligible` (403 `STUDENT_NOT_VALIDATED`). `reason` tem até 500 caracteres (400 `VALIDATION_ERROR`), é aparado e guardado em `applications.status_reason`; em branco vira nulo. Só o motivo da **última** mudança fica gravado (sem histórico de transições, fora do escopo).
- **A elegibilidade vale para toda transição, inclusive `REJECTED`** (leitura literal da decisão): a empresa não consegue nem rejeitar a candidatura de quem perdeu o vínculo. Hoje isso é a única proteção, porque a suspensão automática é a US-32; depois dela, essas candidaturas ficarão `SUSPENDED`. Reavaliar com o usuário se rejeitar deveria ser permitido.
- **Auditoria:** só a rota do admin audita (ação `APPLICATION_STATUS_CHANGED`, alvo `APPLICATION`, motivo, `previous_value`, `new_value`, data). Mudanças feitas pela empresa e tentativas do admin que falham não geram registro. O teste de rollback prova que, se a auditoria falhar, a mudança de status é desfeita (verificado por mutação).
- A resposta é só a decisão (`id`, `previousStatus`, `status`, `reason`, `updatedAt`).

**Administração**
- Toda intervenção de admin exige motivo e grava `admin_audit_logs` na mesma transação (`AuditLogService.record`, com propagação `MANDATORY`).
- Ações auditadas hoje: `USER_SUSPENDED`, `USER_REACTIVATED`, `COMPANY_PROFILE_UPDATED`, `UNIVERSITY_APPROVED`, `UNIVERSITY_REJECTED`, `AFFILIATION_APPROVED`, `AFFILIATION_REJECTED`, `AFFILIATION_REVOKED`, `JOB_UPDATED`, `JOB_CLOSED`, `JOB_REOPENED`, `JOB_DELETED`, `APPLICATION_STATUS_CHANGED`, `APPLICATION_REACTIVATED`. Alvos: `USER`, `COMPANY`, `UNIVERSITY`, `AFFILIATION`, `JOB`, `APPLICATION`. As colunas opcionais `previous_value`/`new_value` guardam o estado antes e depois (hoje só a ação de candidatura as usa; `AuditLogService.record` ganhou uma sobrecarga, a antiga continua valendo).

**Paginação**
- Formato único `PageResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`. Parâmetros `page` e `size` (1 a 100) fora do intervalo são ajustados, não rejeitados.

### 4.7. Restrições e armadilhas técnicas [CONFIRMADO]

- Consultas JPQL com filtro opcional usam `(:param is null or campo = :param)` e funcionam para **enums**. Para **UUID** opcional isso não foi usado (risco de erro de tipo no PostgreSQL); a saída foi ter duas consultas separadas.
- Entidades referenciam contas por `UUID userId` simples (sem `@ManyToOne` para `User`). `UniversityAffiliation` usa `@ManyToOne(LAZY)` para `Student` e `University`; como `open-in-view` é `false`, o service devolve DTOs montados dentro da transação.
- `hibernate.default_batch_fetch_size: 50` (em `application.yaml`) carrega coleções e associações preguiçosas em lotes, para listagens não fazerem uma consulta por linha. **[NÃO VERIFICADO]** por medição: o log de SQL não está ligado; a redução do N+1 não foi conferida contando consultas.
- Não introduzir Redis, filas, microsserviços, MapStruct, Testcontainers nem novas dependências sem requisito concreto.
- Nunca usar `docker compose down -v` (apaga os dados) nem comandos destrutivos sem autorização.

### 4.8. Ambiente local [CONFIRMADO]

- `.env` local contém: `POSTGRES_*`, `BACKEND_PORT`, `SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, `JWT_EXPIRATION`, `ADMIN_EMAIL` (`admin@nexora.local`), `ADMIN_PASSWORD` (aleatória; ler no `.env`). `JWT_SECRET` é obrigatório (mínimo 32 caracteres); sem ele a aplicação não sobe.
- Containers: `nexora-postgres-1` e `nexora-backend-1` (rede `nexora_default`, volumes `nexora_postgres_data` e `nexora_m2`).
- Banco de desenvolvimento (`nexora`): migrado até a V14; contém **apenas** o usuário admin e as 57 competências do catálogo. Banco de testes: `nexora_test`.

---

## 5. Estado atual do projeto

### 5.1. Concluído e verificado [CONFIRMADO]

**US-01 a US-33, todas**, com testes e suíte verde (**347/347**, execução de 2026-11-13). As US-01 a US-19 e as US-23 a US-33 tiveram o fluxo real exercitado por HTTP no banco de desenvolvimento.

Marcos completos: **M1** (identidade), **M2** (perfis e competências), **M3** (vínculo e elegibilidade) e **M4** (vagas).

### 5.2. Implementado, mas com verificação incompleta [CONFIRMADO]

Nenhuma história está com critério de aceite pendente. Ressalva de verificação:

- **US-20, US-21 e US-22** foram feitas em outra sessão. Estão cobertas pela suíte (verde), mas **[NÃO VERIFICADO]** se o fluxo real por HTTP de cada rota (criação, edição, busca com filtros, intervenção do admin em `/admin/jobs`) foi exercitado no banco de desenvolvimento: a migração V13 ainda não estava aplicada nele até a US-23. A verificação HTTP da US-23 cobriu criação, edição/encerramento/reabertura/exclusão por terceiros, listagem e detalhe; **não cobriu** os filtros de busca nem as rotas `/admin/jobs`.

### 5.3. Pendente (ainda não iniciado) [PLANEJADO]

- **Etapa 6 — Candidaturas:** concluída (US-27 a US-33). Marco M6 atingido.
- **Etapa 7 — Frontend Vue:** US-34 a US-38 (nada existe; pasta `frontend/` não criada).
- **Etapa 8 — Publicação:** US-39 a US-41.
- **Etapa 9 — Opcionais (P1):** US-42 e US-43.

Pendências menores registradas ao longo do caminho (nenhuma bloqueia):

- Mensagens de validação por campo em inglês (`must not be null`); o usuário foi avisado e não pediu a tradução.
- Admin não tem listagem de empresas (precisa do `id` do perfil para intervir); avaliar na US-38.
- Catálogo de universidades sem busca por nome.
- Catálogo de skills sem edição/remoção.
- Leitura do **currículo completo** do estudante por terceiros (universidade, empresa, admin) ainda não existe; a empresa só vê o resumo autorizado (US-26) e o currículo chegará pela candidatura (US-30).
- (resolvida na US-32) Suspensão de universidade e revogação de vínculo agora suspendem as candidaturas em andamento; (resolvida na US-33) a reativação é explícita.
- `compose.dev.yaml` nunca foi testado.
- Sem healthcheck do backend e sem Actuator (necessário para a US-39/40).
- Sem limite de tentativas de login.

### 5.4. Bloqueios

Nenhum bloqueio de código. Único risco operacional: **pouca memória na máquina** (ver item 1 da seção 3); seguir o procedimento de parar o backend durante a suíte.

---

## 6. Ponto exato de interrupção

**Última tarefa executada:** US-33 — Reativar candidatura com nova validação, **que encerra a etapa 6 (marco M6)**. **Está terminada e verificada**: 347/347 testes passando (326 anteriores + 13 funcionais + 8 transacionais/concorrentes), duas execuções completas seguidas com log completo, sem erro de contexto e sem sobras no banco de testes. Fluxo real verificado no Docker (revogação → reativação recusada sem vínculo → nova validação → reativação exata de `IN_REVIEW` e de `SUBMITTED` → nova suspensão de universidade suspende de novo com dados novos; `status_reason` preservado; não suspensa → 409; vaga encerrada → 409; universidade suspensa → 403; outra empresa → 404; estudante, universidade e empresa na rota do admin → 403; sem token → 401; auditoria só da reativação do admin). Nenhuma migração nova (última: V18); restrições da V18 conferidas no banco real. Dados de verificação (inclusive auditoria) apagados; backlog marcado; commits feitos na branch. **Mesclada na `main` e enviada ao remoto** após a confirmação do usuário (2026-11-13).

**Testes executados nesta história:** `ApplicationReactivationTests` (13, `@Transactional`), `ApplicationReactivationTransactionTests` (8, **sem** transação de teste: rollback com falha de auditoria injetada, controle positivo e seis cenários de concorrência com threads) e a suíte completa (347).

**Verificações por mutação (cada uma restaurada depois):** (M1) leitura de elegibilidade sem trava → 4 testes de concorrência falham (reativação × revogação e × suspensão de universidade, nos dois sentidos); (M2) sem a trava/checagem da conta da universidade → falham exatamente os 2 testes da suspensão de universidade; (M3) leitura da candidatura sem `FOR UPDATE` → duas reativações simultâneas **ambas dão 200** (`esperado 409, obtido 200`); (M4) serviço admin sem `@Transactional` → com a auditoria falhando o status continua confirmado (`esperado SUSPENDED, obtido IN_REVIEW`).

**Limitações e decisões registradas:**
- `STUDENT_ACCOUNT_NOT_ACTIVE` (409) é um código **definido na implementação** (a decisão exigia conta ativa mas não dava o erro). Revisar se o usuário preferir outro (ex.: 403 `STUDENT_NOT_VALIDATED`).
- `INVALID_SUSPENSION_STATE` é inalcançável num banco consistente (a V18 impede estado anterior nulo/inválido); o teste derruba as restrições dentro da transação de teste para exercitar a defesa em profundidade.
- Vaga excluída responde `409 JOB_NOT_AVAILABLE` na reativação (diferente do 404 do `apply`), por decisão do usuário.
- A reativação restaura o estado sem rever se o currículo ou as competências mudaram: o score é sempre o atual, e o snapshot enviado é o original.
- Reativar não notifica ninguém; a candidatura reativada pode ser suspensa de novo a qualquer momento se a validação cair de novo (US-32).

**Estado do Git neste momento [CONFIRMADO em 2026-11-13]:**

- `main` local = `origin/main` = `cfd2b7b` (contém tudo até a US-33; suíte verde, 347/347).
- Branch atual: `main`, árvore limpa. A branch `feat/etapa-6-applications` está no mesmo commit e pode ser descartada.
- Commits da US-33: `2659463 feat(application): let companies and admins reactivate a suspended application` e `cfd2b7b test(application): cover reactivation rules, audit, atomicity, and concurrency`.
- **Não há nada pendente de commit, merge ou push.**

**Arquivos criados ou alterados na US-33:**
- `nexora-api/src/main/java/br/com/nexora/applications/**`: `service/ApplicationReactivationService`, `dto/ApplicationReactivationResponse`, `repository/ApplicationParties` (novos); `Application` (`reactivate`), `ApplicationStatus` (`isInProgress`), `ApplicationRepository` (`findParties`), `ApplicationController` (`POST /applications/{id}/reactivate`)
- `nexora-api/src/main/java/br/com/nexora/administration/**`: `AdminAction` (`APPLICATION_REACTIVATED`), `AdminApplicationService.reactivate`, `AdminApplicationStatusController` (`POST /admin/applications/{id}/reactivate`)
- `nexora-api/src/main/java/br/com/nexora/universities/service/AcademicEligibilityService.java` (`lockEligibility`, `notValidated` público) e `jobs/service/JobService.java` (`isAcceptingApplications`)
- `nexora-api/src/test/java/br/com/nexora/applications/ApplicationReactivationTests.java` e `ApplicationReactivationTransactionTests.java` (novos)
- `context/HISTORIAS_USUARIO_MVP_NEXORA.md` (US-33 marcada) e este documento.

**Ambiente neste momento [CONFIRMADO]:** containers `nexora-backend-1` e `nexora-postgres-1` no ar; o backend roda a imagem com a US-33; banco de desenvolvimento migrado até a **V18** e limpo (1 admin + 57 competências, auditoria vazia); volume `nexora_resumes_data` vazio.

**Problemas sem solução:** a execução de suíte com 16 erros de contexto (problema 10 da seção 3) não foi explicada, mas não se repetiu.

**Próxima ação concreta:** **nenhuma por iniciativa da IA.** O usuário pediu explicitamente para **não iniciar a US-34** e disse que dará as instruções do frontend depois. Aguardar essas instruções. Quando vierem: criar `feat/etapa-7-frontend` a partir da `main` e resolver as lacunas descritas em 7.3 (gerenciador de pacotes, cliente HTTP, UI, onde guardar o token, porta, e **CORS, que o backend ainda não tem**).

---

## 7. Próximos passos priorizados

### 7.1. Ação imediata

- [ ] Conferir `git status` e `git log -1` (esperado: `main`, árvore limpa, `cfd2b7b`) e **aguardar as instruções do usuário para o frontend**. Não iniciar a US-34 sem elas.

### 7.2. Etapas 5 (matching) e 6 (candidaturas) completas (marcos M5 e M6). Falta o frontend e a publicação.

### 7.3. Sequência seguinte

- [ ] **US-34 a US-38** — frontend Vue 3 + TS + Vite em `frontend/`, com Dockerfile e serviço no Compose. O cadastro de candidatura é `multipart/form-data` (`resumeType` + `file`). Contratos novos que o frontend consome: listas paginadas (`page`, `size`, `status`), `GET /applications/{id}/resume` (JSON do snapshot ou PDF), `PATCH .../status`, `POST .../reactivate` e as rotas `/admin/...`. Todos os erros trazem `code` estável (seção 4.4). A US-34 pode começar já, pois login e registro estão estáveis.
- [ ] **US-39 a US-41** — produção: HTTPS, **CORS restrito (ainda inexistente)**, healthcheck (sem Actuator hoje), **backup conjunto do banco e do volume de PDFs** (ou storage privado de objetos se o provedor não garantir volume durável), limite de tentativas de login, teste ponta a ponta.
- [ ] **US-42 e US-43** — opcionais (avaliação acadêmica e IA semântica). Se a IA for exigida pelo TCC, a US-43 sobe para P0 e precisa de estratégia própria de reaproveitamento de resultados (decisão da US-30: nunca chamar o provedor por linha numa listagem).

### 7.4. Dependências

- US-30 depende de US-27/28 (feitas) e usa `MatchingService.calculate` para o score em separado.
- US-32 depende de US-18 (feito), US-19 (feito) e US-31.

### 7.5. Critério de conclusão de cada história

Critérios de aceite da US atendidos; testes escritos antes e passando; suíte completa verde no Docker; fluxo real verificado por HTTP e dados de verificação apagados; Swagger refletindo os novos endpoints; backlog atualizado; commits atômicos; relatório ao usuário com as decisões tomadas.

---

## 8. Instruções para a próxima IA

1. **Leia este documento inteiro antes de alterar qualquer coisa.** Depois leia a US em questão em `context/HISTORIAS_USUARIO_MVP_NEXORA.md` e as regras relacionadas em `context/ESCOPO_MVP_NEXORA.md`, `context/DIRETRIZES_ENGENHARIA_IA_NEXORA.md` e `context/SYSTEM_DESIGN_NEXORA.md`.
2. **Confirme o estado real no repositório** (`git status`, `git log`, árvore de `nexora-api/src`) — este documento reflete o momento em que foi escrito.
3. **Preserve as decisões das seções 2 e 4.** Em especial: contrato de erros, direção das dependências entre módulos, auditoria só no módulo `administration`, papel e status lidos do banco a cada requisição, regra única de elegibilidade.
4. **Não reimplemente o que está concluído** nem crie caminhos paralelos (ex.: outro handler de erros, outra forma de paginar, outra checagem de elegibilidade).
5. **Não presuma que algo planejado existe.** As seções 5.3 e 7 descrevem intenções; o frontend não existe e o módulo `applications` cobre o envio (PLATFORM ou PDF), a lista do estudante, a leitura do currículo enviado e a lista da empresa e do admin, a mudança de andamento, a suspensão automática e a reativação explícita (US-27 a US-33); a etapa 6 está completa.
6. **Siga o processo da seção 4.1** (Red → Green → suíte → verificação por HTTP → limpeza → backlog → commits → relatório) e o fluxo de Git da seção 4.2. Não faça merge nem push sem a confirmação do usuário.
7. **Não há tarefa em aberto** na seção 6: tudo até a US-33 está na `main`. A próxima etapa é a 7 (frontend, US-34), mas **só começa quando o usuário der as instruções** (ele pediu para esperar).
8. **Pergunte ao usuário apenas quando houver lacuna de regra de negócio que mude contrato ou comportamento** e que os documentos não resolvam (como foi feito na US-14). Decisões pequenas: tome a mais simples, implemente e **relate** no fechamento da história.
9. **Seja honesto sobre verificação:** se um comando não rodou ou um teste falhou, diga. Se um critério de aceite não pôde ser verificado, não marque a história como concluída. **Nunca marque `[x]`, commite como concluída ou faça push sem ter visto a suíte completa verde** (foi o que aconteceu na US-22).
10. **Cuidado com a memória da máquina:** pare o backend antes da suíte; se uma execução travar, verifique a carga antes de suspeitar do código.
11. **O usuário se comunica em português (pt-BR).** Código, nomes de classes e commits em inglês; comentários de código e mensagens de erro ao usuário em português.
12. **Atualize este documento** ao concluir cada etapa relevante: seções 3 (histórico), 5 (estado), 6 (ponto de interrupção) e 7 (próximos passos), mantendo o que continuar válido.
