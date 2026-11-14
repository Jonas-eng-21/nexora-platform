# Nexora — Histórias de Usuário e Plano de Execução do MVP 1.0

**Status inicial:** todas as histórias ainda não iniciadas, até confirmação no repositório.  
**Objetivo:** entregar o fluxo integral estudante–universidade–empresa–admin, currículo-base, candidatura com escolha de documento, matching e publicação.  
**Princípio:** ordem por dependências; backend e frontend podem avançar em paralelo após contratos estabilizados.

## Como acompanhar o progresso

- Marque `[x]` apenas após implementar e validar os critérios de aceite de cada história.
- Marque `[-]` em acompanhamento externo (quadro/issue) para execução em andamento, se desejar; neste arquivo mantenha checkboxes padrão para compatibilidade Markdown.
- P0 = essencial; P1 = opcional ou condicionada à exigência acadêmica.
- **Definição de pronto (DoD) para cada US P0:** endpoint/fluxo funcional, autorização correta, validações, persistência, teste mínimo do caso crítico, documentação/contrato atualizado e commit.
- A numeração é **sequencial para execução**; não corresponde aos IDs dos requisitos funcionais. Indique dependências antes de começar uma US.

## Visão geral das etapas

| Etapa | US | Entregável verificável | Prioridade |
|---|---|---|---|
| 0. Base | US-01 a US-04 | API e banco preparados; contratos e segurança-base | P0 |
| 1. Identidade | US-05 a US-09 | Cadastros, login, contas e admin inicial | P0 |
| 2. Perfis e currículo | US-10 a US-14 | Perfis completos e currículo-base utilizável | P0 |
| 3. Vínculo | US-15 a US-19 | Aprovação institucional e validação acadêmica | P0 |
| 4. Vagas | US-20 a US-23 | Empresa publica e estudante consulta vagas | P0 |
| 5. Matching | US-24 a US-26 | Score e recomendações nos dois sentidos | P0 |
| 6. Candidaturas | US-27 a US-33 | Envio, documentos, gestão e suspensão | P0 |
| 7. Administração/UI | US-34 a US-38 | Interface usável pelos quatro perfis | P0 |
| 8. Publicação | US-39 a US-41 | Deploy HTTPS, teste ponta a ponta e entrega | P0 |
| 9. Evoluções | US-42 a US-43 | Avaliações e IA semântica | P1* |

**Observação de fluxo:** o matching pode ser desenvolvido antes do módulo de candidaturas porque usa currículo-base e vagas; para demonstrar primeiro a candidatura, US-24–26 e US-27–33 também podem ser implementadas em trilhas paralelas.

---

## Etapa 0 — Fundação do backend

- [x] **US-01 — Preparar migrações e entidades-base** · **P0** · RF-01/04, RNF-02  
  **Como** desenvolvedor, **quero** organizar o esquema inicial com Flyway e entidades essenciais, **para** garantir persistência reproduzível.  
  **Aceite:** migrações versionadas criam tabelas/restrições; aplicação inicia com banco vazio; nenhuma tabela é recriada automaticamente pelo Hibernate; chaves/índices essenciais existem.  
  **Dep.:** Docker existente.

- [x] **US-02 — Padronizar erros e validações da API** · **P0** · RNF-04/07  
  **Como** consumidor da API, **quero** receber erros HTTP consistentes, **para** tratar respostas no frontend.  
  **Aceite:** DTOs com Bean Validation; 400/401/403/404/409 padronizados; logs sem segredos.  
  **Dep.:** US-01.

- [x] **US-03 — Preparar segurança e papéis** · **P0** · RF-04, RNF-01  
  **Como** sistema, **quero** aplicar RBAC (`STUDENT`, `COMPANY`, `UNIVERSITY`, `ADMIN`), status e propriedade, **para** impedir acessos indevidos.  
  **Aceite:** endpoints restritos; status `SUSPENDED` bloqueia operações; matrícula/vaga de terceiros não podem ser alteradas.  
  **Dep.:** US-01.  
  **Progresso (2026-11-11):** RBAC, validação de JWT e bloqueio por status implementados e testados. Pendente: critério de propriedade (matrícula/vaga de terceiros), verificável apenas nas US-17 e US-23. Vínculo de terceiros coberto na US-17 (2026-11-11); vaga de terceiros coberta na US-23 (2026-11-12). **Concluída.**

- [x] **US-04 — Documentar contratos básicos com OpenAPI** · **P0** · RNF-05  
  **Como** desenvolvedor frontend, **quero** visualizar e testar os contratos da API, **para** integrar a interface sem adivinhação.  
  **Aceite:** Swagger no ambiente de desenvolvimento; autenticação documentada; contratos DTO principais descritos conforme implementados.  
  **Dep.:** US-02/03. **Contínua:** atualizar em cada entrega.

## Etapa 1 — Identidade e administração inicial

- [x] **US-05 — Autocadastro por perfil** · **P0** · RF-01  
  **Como** visitante, **quero** me cadastrar como estudante, empresa ou universidade, **para** acessar meu espaço.  
  **Aceite:** e-mail único; senha com hash; estudante e empresa `ACTIVE`; universidade `PENDING_APPROVAL`; `ADMIN` proibido no cadastro público.  
  **Dep.:** US-01/02/03.

- [x] **US-06 — Login e JWT** · **P0** · RF-02  
  **Como** usuário registrado e habilitado, **quero** autenticar com e-mail/senha, **para** acessar rotas protegidas.  
  **Aceite:** token expirável; credenciais inválidas negadas; universidade pendente/suspensa sem acesso operacional; backend confere estado ativo em cada operação protegida.  
  **Dep.:** US-05.

- [x] **US-07 — Consultar e editar conta própria** · **P0** · RF-03  
  **Como** usuário autenticado, **quero** consultar/atualizar meus dados básicos, **para** manter a conta correta.  
  **Aceite:** `/me` acessível; não troca papel/status por payload; não altera dados de terceiros.  
  **Dep.:** US-06.

- [x] **US-08 — Provisionar administrador inicial** · **P0** · RF-24  
  **Como** mantenedor, **quero** criar o primeiro admin de modo controlado, **para** aprovar instituições.  
  **Aceite:** provisionamento por comando/seed seguro e idempotente; sem admin público; sem senha hardcoded no Git.  
  **Dep.:** US-05/06.

- [x] **US-09 — Administrar contas e auditar ações** · **P0** · RF-24/25  
  **Como** admin, **quero** consultar, ativar/suspender usuários e registrar intervenções, **para** governar a plataforma.  
  **Aceite:** restrito a `ADMIN`; ação com responsável/data/alvo; suspensão bloqueia operações; logs persistidos.  
  **Dep.:** US-08.

## Etapa 2 — Perfis e currículo-base

- [x] **US-10 — Perfil de estudante** · **P0** · RF-05  
  **Como** estudante, **quero** cadastrar e atualizar meus dados acadêmicos/profissionais, **para** me apresentar.  
  **Aceite:** nome, curso, instituição, localização e resumo; atualiza só o próprio perfil; perfil consultável conforme regras de acesso.  
  **Dep.:** US-06/07.

- [x] **US-11 — Currículo-base estruturado** · **P0** · RF-06/26  
  **Como** estudante, **quero** manter um currículo com experiências, formação, projetos, cursos, competências e links, **para** alimentar o matching.  
  **Aceite:** criar/consultar/editar seções; validação; dados persistentes; estrutura estável para score e snapshot; PDF não substitui estes dados.  
  **Dep.:** US-10.

- [x] **US-12 — Catálogo de competências** · **P0** · RF-06/13/20  
  **Como** estudante ou empresa, **quero** selecionar competências padronizadas, **para** evitar divergência nos requisitos.  
  **Aceite:** consulta de skills; associação a estudante/vaga; sem duplicatas lógicas óbvias.  
  **Dep.:** US-01/11.

- [x] **US-13 — Perfil de empresa** · **P0** · RF-12  
  **Como** empresa, **quero** manter nome, setor, descrição e localização, **para** identificar minhas vagas.  
  **Aceite:** perfil criado/atualizado pela própria empresa; admin pode intervir.  
  **Dep.:** US-06/07.

- [x] **US-14 — Perfil de universidade** · **P0** · RF-07  
  **Decisão (2026-11-11):** os dados institucionais são informados no cadastro (`POST /auth/register` com `university`), pois a universidade pendente não autentica; depois de aprovada, edita em `/universities/me`.  
  **Como** universidade, **quero** cadastrar e manter os dados institucionais, **para** ser identificada no processo acadêmico.  
  **Aceite:** dados de instituição persistidos; alterações autorizadas; instituição não aprovada não consegue atuar como validadora.  
  **Dep.:** US-05/06.

## Etapa 3 — Aprovação e vínculo acadêmico

- [x] **US-15 — Aprovação do cadastro de universidade** · **P0** · RF-24  
  **Como** admin, **quero** aprovar ou rejeitar universidade pendente, **para** evitar validação por instituição não autorizada.  
  **Aceite:** somente admin decide; registra decisão/motivo/data; aprovadas ficam `ACTIVE`; rejeitadas não recebem poder de aprovação.  
  **Dep.:** US-08/09/14.

- [x] **US-16 — Solicitar vínculo acadêmico** · **P0** · RF-08  
  **Como** estudante, **quero** pedir vínculo com universidade ativa, **para** obter validação acadêmica.  
  **Aceite:** somente universidade ativa; no máximo um pedido `PENDING` e um aprovado não revogado; resposta com status.  
  **Dep.:** US-10/14/15.

- [x] **US-17 — Analisar solicitação de vínculo** · **P0** · RF-09  
  **Como** universidade, **quero** listar pedidos dirigidos a mim e aprovar/rejeitar, **para** validar estudantes.  
  **Aceite:** apenas universidade destinatária/admin; decisão transacional; histórico persistido; estudante não pode decidir.  
  **Dep.:** US-16.

- [x] **US-18 — Consultar e revogar vínculos** · **P0** · RF-10  
  **Como** universidade/admin, **quero** listar estudantes vinculados e revogar vínculo com motivo, **para** manter a validade acadêmica.  
  **Aceite:** apenas vínculo de sua instituição ou admin; data/autor/motivo; elegibilidade atualizada; histórico mantido.  
  **Dep.:** US-17.

- [x] **US-19 — Centralizar regra de elegibilidade acadêmica** · **P0** · RF-04/15/19  
  **Como** sistema, **quero** verificar vínculo `APPROVED` vigente com universidade `ACTIVE`, **para** autorizar candidaturas corretamente.  
  **Aceite:** serviço compartilhado testado para pendente, rejeitado, revogado, universidade suspensa e válido.  
  **Dep.:** US-18.

## Etapa 4 — Empresas e vagas

- [x] **US-20 — Criar vaga** · **P0** · RF-13  
  **Como** empresa ativa, **quero** publicar uma vaga com requisitos, **para** receber candidatos compatíveis.  
  **Aceite:** título, descrição, localização, modelo, nível, competências obrigatórias/desejáveis e status; empresa vinculada automaticamente.  
  **Dep.:** US-12/13.

- [x] **US-21 — Editar, encerrar e excluir logicamente vaga** · **P0** · RF-13  
  **Como** empresa, **quero** controlar o ciclo de vida das minhas vagas, **para** refletir oportunidades disponíveis.  
  **Aceite:** apenas dona/admin; edição e fechamento; exclusão lógica sem apagar candidaturas; vagas fechadas não recebem novas candidaturas.  
  **Dep.:** US-20.

- [x] **US-22 — Listar e consultar vagas** · **P0** · RF-14  
  **Como** estudante, **quero** pesquisar vagas abertas, **para** encontrar oportunidades.  
  **Aceite:** lista e detalhe; filtros básicos, paginação, empresa responsável e competências; ocultar vagas excluídas logicamente.  
  **Dep.:** US-20.

- [x] **US-23 — Proteger propriedade e estado da vaga** · **P0** · RF-04/13/15  
  **Como** sistema, **quero** impedir operações por empresas não proprietárias ou inativas, **para** manter integridade.  
  **Aceite:** tentativa entre empresas retorna 403/404 conforme contrato; somente vaga aberta de empresa ativa admite candidatura.  
  **Dep.:** US-21/22.

## Etapa 5 — Matching e recomendações

- [x] **US-24 — Calcular score explicável** · **P0** · RF-20  
  **Decisões (2026-11-12):** interesses medidos por **catálogo fixo de áreas** (a área da vaga deixou de ser texto livre; o currículo ganhou `interestAreas`); modalidade/localização com **preferência opcional** de modelo de trabalho no currículo (`preferredWorkModels`). O score para a empresa (estudantes de uma vaga) entra na US-26.  
  **Como** estudante/empresa, **quero** entender a compatibilidade entre currículo-base e vaga, **para** avaliar aderência.  
  **Aceite:** score 0–100, pesos configurados e testados; lista de competências aderentes/ausentes; texto explicativo baseado em critérios; nunca analisar PDF externo.  
  **Dep.:** US-11/12/20.

- [x] **US-25 — Recomendar vagas para estudante** · **P0** · RF-21  
  **Como** estudante, **quero** ver vagas ordenadas por score, **para** priorizar candidaturas.  
  **Aceite:** usa meu currículo-base; apenas vagas abertas/empresas ativas; paginação/limite; scores e justificativas.  
  **Dep.:** US-24.

- [x] **US-26 — Recomendar estudantes para empresa** · **P0** · RF-22  
  **Decisões (2026-11-13):** entram só estudantes **academicamente elegíveis** (`AcademicEligibilityService`) e com conta `ACTIVE`; a empresa vê nome, curso, localização, resumo, competências do catálogo e áreas de interesse — **sem** e-mail, instituição de texto livre, links nem currículo (este só chega por candidatura).  
  **Como** empresa, **quero** consultar estudantes ordenados por aderência às minhas vagas, **para** identificar talentos.  
  **Aceite:** apenas empresa proprietária/admin; perfis com informações autorizadas; score e justificativa; não divulgar dados privados indevidos.  
  **Dep.:** US-24/23.

## Etapa 6 — Candidaturas e currículos enviados

- [x] **US-27 — Candidatar usando currículo Nexora** · **P0** · RF-15/27/28  
  **Decisões (2026-11-13):** `POST /api/v1/jobs/{jobId}/applications` com `{"resumeType": "PLATFORM"}`; o snapshot (JSONB) guarda perfil (nome, curso, instituição, localização, resumo), currículo-base e competências do catálogo, **sem** e-mail nem id da conta; duplicidade responde 409 `APPLICATION_ALREADY_EXISTS`. Tabela `applications` (V15) já prevê `PDF` no CHECK; colunas e exclusividade do PDF ficam para a US-28.  
  **Como** estudante validado, **quero** me candidatar com meu currículo-base, **para** enviar minhas informações à empresa.  
  **Aceite:** modalidade `PLATFORM`; snapshot JSON imutável; exige elegibilidade e vaga aberta; única candidatura por estudante/vaga.  
  **Dep.:** US-11/19/23.

- [x] **US-28 — Candidatar usando PDF privado** · **P0** · RF-15/27/28  
  **Decisões (2026-11-13):** o mesmo `POST /api/v1/jobs/{jobId}/applications` passou a ser `multipart/form-data` (`resumeType=PLATFORM` ou `PDF` + campo `file`) — **muda o contrato da US-27**; limite de **5 MiB = 5.242.880 bytes** (o escopo manda; "5 MB" na decisão foi lido assim); PDFs em volume Docker nomeado montado só no backend, com nome interno gerado (UUID); metadados na candidatura (V16); download em `GET /api/v1/applications/{id}/resume` para o estudante dono, a empresa dona da vaga e o ADMIN (demais: 404; universidade: 403).  
  **Como** estudante validado, **quero** enviar um PDF próprio, **para** personalizar a candidatura.  
  **Aceite:** modalidade `PDF` exclusiva; rejeita arquivos acima de 5.242.880 bytes ou inválidos; armazena em destino persistente privado; nenhuma extração para matching.  
  **Dep.:** US-27 (ou serviço comum de candidaturas).

- [x] **US-29 — Consultar candidaturas do estudante** · **P0** · RF-16  
  **Decisões (2026-11-13):** `GET /api/v1/applications/me` (STUDENT) com `id`, `jobId`, `jobTitle`, `companyName`, `status`, `resumeType`, `appliedAt`, `updatedAt`; página 0, tamanho padrão 10 e máximo 50 (valores fora do intervalo são ajustados), ordem fixa `appliedAt` e `id` decrescentes, filtro opcional `status`; mostra todos os status (inclusive suspensas e finalizadas); sem snapshot nem PDF na lista. O currículo enviado é lido em `GET /applications/{id}/resume`, que agora devolve o **snapshot em JSON** para candidaturas `PLATFORM` (antes: 404) e continua entregando o PDF como anexo.  
  **Como** estudante, **quero** visualizar minhas candidaturas e seus estados, **para** acompanhar o processo.  
  **Aceite:** lista somente as próprias; mostra vaga, empresa, modalidade, data e status.  
  **Dep.:** US-27.

- [x] **US-30 — Empresa consultar candidaturas e documento** · **P0** · RF-17/28  
  **Decisões (2026-11-13):** `GET /api/v1/jobs/{jobId}/applications` (COMPANY dona; vaga de outra empresa, excluída ou inexistente → 404 `JOB_NOT_FOUND`) e `GET /api/v1/admin/jobs/{jobId}/applications` (ADMIN, leitura, sem auditoria), com a mesma resposta: `id`, `status`, `resumeType`, `appliedAt`, `updatedAt`, `student` (o `StudentSummary` da US-26, sem e-mail) e `matchScore` (compatibilidade **atual**, 0 a 100, pelo currículo-base e nunca pelo PDF; **nulo** quando o currículo-base não tem competências do catálogo nem áreas de interesse). Paginação padrão 10/máx. 50, ordem `appliedAt` e `id` decrescentes, filtro `status`, todos os status. O documento enviado é lido em `GET /applications/{id}/resume` (snapshot JSON ou PDF); a lista nunca o traz. Score não é gravado.  
  **Como** empresa, **quero** ver candidatos e o currículo que enviaram, **para** analisar cada candidatura.  
  **Aceite:** somente candidaturas de vagas próprias; snapshot ou PDF recuperável por endpoint autorizado; score consultável em separado com base no currículo-base.  
  **Dep.:** US-27/28.

- [x] **US-31 — Empresa atualizar andamento** · **P0** · RF-18  
  **Decisões (2026-11-13):** `PATCH /api/v1/applications/{id}/status` (COMPANY dona; outra empresa/inexistente → 404 `APPLICATION_NOT_FOUND`) e `PATCH /api/v1/admin/applications/{id}/status` (ADMIN, auditada na mesma transação). Corpo `{status, reason?}`. Transições: `SUBMITTED` → `IN_REVIEW`/`APPROVED`/`REJECTED` e `IN_REVIEW` → `APPROVED`/`REJECTED`; `APPROVED` e `REJECTED` são finais (nem para si mesmos); `SUSPENDED` nunca é atribuído e uma candidatura suspensa não avança (409 `APPLICATION_SUSPENDED`); transição inválida → 409 `INVALID_APPLICATION_STATUS_TRANSITION`. Rejeitar exige motivo (400 `REJECTION_REASON_REQUIRED`), máx. 500 caracteres. Toda transição exige o estudante academicamente elegível (403 `STUDENT_NOT_VALIDATED`). A reativação continua na US-33.  
  **Como** empresa, **quero** mover candidaturas entre `SUBMITTED`, `IN_REVIEW`, `APPROVED` e `REJECTED`, **para** acompanhar seleção.  
  **Aceite:** transições válidas; somente dona/admin; candidatura suspensa não avança; finalizadas preservadas.  
  **Dep.:** US-30.

- [x] **US-32 — Suspender candidaturas sem validação vigente** · **P0** · RF-19  
  **Decisões (2026-11-13):** disparada pela revogação do vínculo (universidade ou admin) e pela suspensão da conta de uma universidade ativa, por **eventos internos síncronos** (`AcademicAffiliationRevoked`, `UniversitySuspended`) publicados em `universities.event` e escutados por `applications` na mesma transação. Só `SUBMITTED`/`IN_REVIEW` viram `SUSPENDED`, em um `UPDATE` em lote idempotente que grava `suspended_from_status`, `suspension_reason` (`ACADEMIC_AFFILIATION_REVOKED` ou `UNIVERSITY_SUSPENDED`) e `suspended_at`; `status_reason` não é tocado e estados finais e já suspensos ficam intactos. A suspensão da universidade **não revoga** os vínculos. Sem auditoria por candidatura. Candidatura nova passa a travar o vínculo e a conta da universidade (`FOR SHARE`) para não escapar da suspensão. A reativação é a US-33.  
  **Como** sistema, **quero** suspender candidaturas abertas após revogação ou suspensão institucional, **para** cumprir elegibilidade.  
  **Aceite:** `SUBMITTED`/`IN_REVIEW` tornam-se `SUSPENDED`; registra motivo, instante e estado anterior; não altera encerradas; preserva documento; inclui suspensão de universidade.  
  **Dep.:** US-18/19/31. **Nota:** planejar consistência transacional na revogação e mecanismo consistente para suspensão da universidade.

- [x] **US-33 — Reativar candidatura com nova validação** · **P0** · RF-18/19  
  **Decisões (2026-11-13):** `POST /api/v1/applications/{id}/reactivate` (COMPANY dona; outra empresa/inexistente → 404 `APPLICATION_NOT_FOUND`) e `POST /api/v1/admin/applications/{id}/reactivate` (ADMIN, auditada com `APPLICATION_REACTIVATED`, de `SUSPENDED` para o estado restaurado), sem corpo. Só candidatura `SUSPENDED` (senão 409 `APPLICATION_NOT_SUSPENDED`); restaura **exatamente** `suspended_from_status` (`SUBMITTED` ou `IN_REVIEW`; ausente ou inválido → 409 `INVALID_SUSPENSION_STATE`, sem alterar nada) e limpa `suspended_from_status`, `suspension_reason` e `suspended_at`, preservando `status_reason` e o currículo. Exige estudante com conta ativa (409 `STUDENT_ACCOUNT_NOT_ACTIVE`, código definido na implementação), academicamente elegível (403 `STUDENT_NOT_VALIDATED`) e vaga aberta, não excluída e de empresa ativa (409 `JOB_NOT_AVAILABLE`). Nunca é automática. Sem migração nova.  
  **Como** empresa responsável/admin, **quero** reativar explicitamente candidatura suspensa após validação restaurada, **para** retomar a seleção.  
  **Aceite:** verifica elegibilidade atual e titularidade; restaura estado anterior apropriado; nunca reativa automaticamente; audita operação.  
  **Dep.:** US-32.

## Etapa 7 — Frontend e painéis mínimos (Vue 3 + TypeScript + Vite)

- [ ] **US-34 — Criar frontend base e fluxo de autenticação** · **P0** · RF-01/02/03  
  **Como** usuário, **quero** entrar/cadastrar e navegar com proteção de rotas, **para** acessar meu painel.  
  **Aceite:** Dockerfile frontend e Compose integrado, cliente HTTP, telas login/registro, estados de erro e redirecionamento por papel.  
  **Dep.:** US-05/06/07. **Pode iniciar em paralelo** à modelagem restante.

- [ ] **US-35 — Telas de estudante** · **P0** · RF-05/06/08/14/16/21/26/27  
  **Como** estudante, **quero** editar currículo, solicitar vínculo, navegar em vagas/matching e me candidatar com currículo Nexora ou PDF, **para** completar minha jornada.  
  **Aceite:** telas conectadas à API; estado de validação visível; bloqueio de candidatura não validada; upload com indicador de limite 5 MiB; histórico de candidaturas.  
  **Dep.:** US-11/16/22/25/27/28/29.

- [ ] **US-36 — Telas de empresa** · **P0** · RF-12/13/17/18/22  
  **Como** empresa, **quero** gerenciar vagas e consultar candidatos/candidaturas, **para** operar a seleção.  
  **Aceite:** perfil, CRUD vagas, candidatos recomendados, candidaturas e consulta segura do documento enviado.  
  **Dep.:** US-13/21/26/30/31.

- [ ] **US-37 — Telas de universidade** · **P0** · RF-07/09/10  
  **Como** universidade, **quero** aprovar, rejeitar e revogar vínculos, **para** confirmar estudantes.  
  **Aceite:** perfil institucional, lista de solicitações, decisões, estudantes vinculados e estados.  
  **Dep.:** US-14/17/18.

- [ ] **US-38 — Telas de administrador** · **P0** · RF-24/25  
  **Como** admin, **quero** aprovar universidades e controlar contas/recursos críticos, **para** operar a plataforma.  
  **Aceite:** lista e decisões de universidade, suspensão/reativação, intervenção básica, registros auditáveis.  
  **Dep.:** US-09/15/33.

## Etapa 8 — Publicação e validação

- [ ] **US-39 — Preparar configuração segura para produção** · **P0** · RNF-01/06/10/11/12/14  
  **Como** mantenedor, **quero** configurar ambiente e armazenamento persistente, **para** permitir acessos externos com segurança.  
  **Aceite:** segredos fora do Git; CORS restrito; HTTPS; banco privado; PDF em storage persistente privado; limites de upload; logs sem dados sensíveis.  
  **Dep.:** infraestrutura Docker e US-28. **Iniciar planejamento cedo**, não apenas no fim.

- [ ] **US-40 — Publicar Nexora acessível a terceiros** · **P0** · RNF-06  
  **Como** visitante, **quero** acessar a plataforma por URL pública, **para** realizar cadastros e operações.  
  **Aceite:** frontend, backend e banco disponíveis; domínio/URL HTTPS; volumes/storage persistentes; healthcheck operacional; sem expor PostgreSQL na internet.  
  **Dep.:** US-34 e US-39 (demais telas podem ser liberadas progressivamente).

- [ ] **US-41 — Testar cenário completo de demonstração** · **P0** · RNF-09  
  **Como** avaliador, **quero** percorrer todas as jornadas, **para** comprovar o produto.  
  **Aceite:** universidade cadastra → admin aprova → estudante cria currículo e solicita vínculo → universidade aprova → empresa publica vaga → recomendações aparecem → estudante envia currículo Nexora ou PDF → empresa consulta → universidade revoga → candidatura em andamento suspende → revalidação + reativação explícita; repetir teste de autorização e PDF >5 MiB.  
  **Dep.:** US-01 a US-40 conforme fluxo (excluídas opcionais).

## Etapa 9 — Evoluções opcionais

- [ ] **US-42 — Avaliação/recomendação acadêmica** · **P1** · RF-11  
  **Como** universidade, **quero** registrar avaliação sobre estudante vinculado, **para** atribuir crédito acadêmico adicional.  
  **Aceite:** universidade vinculada pode criar/editar/remover avaliação; estudante só consulta; revogação não apaga histórico; não influenciar score sem critério validado.  
  **Dep.:** US-18.

- [ ] **US-43 — Similaridade semântica com provedor de IA** · **P1*** · RF-23  
  **Como** plataforma, **quero** comparar semanticamente currículo-base e vaga por interface `AIProvider`, **para** complementar o matching estruturado.  
  **Aceite:** um provedor funcional (Gemini ou OpenAI), tratamento de falhas/custos, sem usar PDF, score final continua calculado pelo sistema, teste com pares de textos; **promover a P0 se necessário para o TCC**.  
  **Dep.:** US-24.

---

## Matriz de progresso e marcos

**Total:** 43 US = **41 P0 + 2 P1**. O número de histórias é operacional (granularidade técnica); não equivale ao número de RFs.

| Marco | Evidência de entrega | Histórias |
|---|---|---|
| M1 | Login e quatro papéis funcionais; admin provisionado | US-01–09 |
| M2 | Currículo-base, empresa e universidade com perfis | US-10–14 |
| M3 | Vínculo solicitado/aprovado/revogado; elegibilidade verificável | US-15–19 |
| M4 | CRUD e consulta de vagas | US-20–23 |
| M5 | Score e recomendações bidirecionais | US-24–26 |
| M6 | Candidatura com snapshot ou PDF, status/suspensão | US-27–33 |
| M7 | Quatro jornadas navegáveis no frontend | US-34–38 |
| M8 | Deploy público + teste integral aprovado | US-39–41 |
| M9 | Avaliação acadêmica / IA semântica | US-42–43 (opcionais) |

## Ordem prática otimizada para prazo curto

1. **Concluir M1**; abrir M7/US-34 em paralelo assim que login estiver estável.
2. **M2 + M3**, especialmente currículo-base e validação institucional; desenhar hospedagem/US-39 desde já.
3. **M4**, publicar vagas e conectar telas de empresa e estudante.
4. **M5 e M6 em paralelo** após currículo e vaga funcionarem; score e candidatura são relativamente independentes.
5. **M7**, integrar as telas dos quatro perfis sem sofisticar UI.
6. **M8**, validar no ambiente publicado e corrigir apenas bloqueios P0.
7. **M9** apenas com folga, exceto exigência acadêmica explícita de IA.

### Conjunto mínimo de testes que não pode ser cortado

- Cadastro de universidade fica pendente e não pode aprovar vínculos antes da aprovação.
- Estudante não pode se aprovar nem acessar vagas de outra empresa para editar.
- Estudante sem vínculo válido não consegue candidatar-se, inclusive chamando API diretamente.
- Segundo pedido pendente/segundo vínculo aprovado e segunda candidatura à mesma vaga são bloqueados.
- Upload > 5.242.880 bytes, extensão forjada ou PDF inválido são rejeitados; arquivo não é público.
- PDF externo não influencia o score; currículo Nexora enviado é snapshot estável.
- Universidade suspensa/revogação suspende candidaturas abertas e mantém encerradas e histórico.
- Admin consegue intervir e toda decisão crítica deixa auditoria.
- Deploy externo com HTTPS e persistência após reinício.

**Arquivo de referência de requisitos:** `ESCOPO_MVP_NEXORA.md`.
