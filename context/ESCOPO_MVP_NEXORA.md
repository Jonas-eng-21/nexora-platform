# Nexora — Especificação do MVP 1.0

**Status:** escopo funcional aprovado para implementação  
**Objetivo:** entregar uma plataforma web pública que conecte estudantes de tecnologia a empresas, com validação de vínculo acadêmico por universidades, candidaturas e matching explicável.  
**Público inicial:** Garanhuns (PE) e região.  
**Diretriz:** prazo extremamente curto; implementar primeiro os fluxos ponta a ponta, sem funcionalidades periféricas.

## 1. Proposta e limites

O Nexora não é apenas um mural de vagas. Seu diferencial é combinar **currículo-base estruturado**, **validação acadêmica**, **oportunidades profissionais** e **recomendação bidirecional estudante ↔ vaga**.

### 1.1 Dentro do MVP

- Autocadastro e autenticação de estudante, empresa e universidade; administrador provisionado de forma segura.
- Quatro perfis, controle de acesso por papel, situação da conta e titularidade dos recursos.
- Perfil institucional e currículo-base estruturado do estudante.
- Solicitação, aprovação, rejeição e revogação de vínculo acadêmico.
- Autocadastro de universidade sujeito à aprovação do administrador.
- CRUD de vagas pela empresa, com exclusão lógica.
- Candidaturas exclusivamente por estudantes academicamente validados.
- Opção de enviar **snapshot do currículo-base Nexora** ou **PDF externo de até 5 MiB**.
- Empresa consulta currículo e score da candidatura e atualiza o andamento.
- Suspensão das candidaturas em andamento quando a validação acadêmica deixa de ser válida, com histórico preservado.
- Matching determinístico/explicável e recomendações nos dois sentidos.
- Frontend mínimo para os quatro perfis e implantação pública por HTTPS.

### 1.2 Fora do MVP (salvo exigência acadêmica)

OAuth, recuperação de senha automatizada, refresh token, chat, notificações, upload de outros formatos, candidatura com os dois tipos de currículo simultaneamente, múltiplos vínculos acadêmicos aprovados por estudante, painel analítico avançado, permissões dinâmicas arbitrárias, processamento de PDFs para matching, múltiplos provedores de IA, mensageria, Redis, Kubernetes e microsserviços.

**P1 opcionais:** avaliações/recomendações acadêmicas detalhadas e análise semântica integrada a provedor de IA. **Ressalva:** se a apresentação do TCC exigir IA efetivamente operante, a análise semântica deverá ser promovida a P0 antes da entrega, sem sacrificar validação, candidaturas ou publicação.

## 2. Perfis e responsabilidades

| Perfil / papel | Entrada | Permissões principais | Restrições |
|---|---|---|---|
| **Estudante (`STUDENT`)** | Autocadastro com e-mail e senha | Preencher currículo-base; indicar competências, formação, projetos, experiência e interesses; solicitar vínculo; visualizar vagas e matching; candidatar-se; consultar candidaturas | Só se candidata com vínculo acadêmico aprovado, vigente e universidade ativa; só altera seu currículo e suas informações |
| **Empresa (`COMPANY`)** | Autocadastro com e-mail e senha | Gerenciar perfil; CRUD completo das **próprias** vagas; consultar/gerenciar candidaturas das suas vagas; receber recomendações de candidatos | Não pode gerenciar vagas ou candidaturas de outras empresas |
| **Universidade (`UNIVERSITY`)** | Autocadastro; conta começa em `PENDING_APPROVAL` e depende de aprovação do admin | Gerenciar perfil; listar pedidos de vínculo; aprovar/rejeitar; consultar estudantes vinculados; revogar vínculo; opcionalmente registrar recomendações acadêmicas | Não pode alterar livremente currículo, senha ou dados particulares do estudante; só administra vínculos endereçados a ela |
| **Administrador (`ADMIN`)** | Provisionamento seguro, nunca via cadastro público | Aprovar/rejeitar universidade; ativar/suspender contas; gerir e intervir em perfis, vínculos, vagas e candidaturas; auditar decisões | Não precisa conhecer senhas/tokens; decisões críticas devem deixar rastros de auditoria |

**Simplificação de organização:** uma conta operacional por empresa/universidade no MVP; suporte a múltiplos operadores por organização fica para evolução.

## 3. Fluxos de negócio e estados

### 3.1 Cadastro e situação da conta

- Estudantes e empresas se cadastram e ficam `ACTIVE`.
- Universidades se cadastram como `PENDING_APPROVAL`; somente o admin aprova (`ACTIVE`) ou rejeita o pedido; rejeições devem registrar decisão/motivo.
- Contas podem ser `SUSPENDED` pelo admin. Suspensão deve impedir operações autenticadas mesmo com JWT ainda não expirado (validar status no backend).
- Uma universidade suspensa não pode aprovar vínculos; seus vínculos não conferem elegibilidade enquanto a suspensão durar.
- `ADMIN` não é elegível no registro público.

### 3.2 Vínculo acadêmico

Estados de solicitação: `PENDING`, `APPROVED`, `REJECTED`, `REVOKED`.

1. Estudante ativo escolhe universidade `ACTIVE` e solicita vínculo.
2. Universidade correspondente aprova ou rejeita; estudante não decide o próprio pedido.
3. No máximo **uma solicitação pendente** e **um vínculo aprovado/não revogado** por estudante no MVP.
4. Após rejeição, nova solicitação é permitida, preservando histórico.
5. Universidade responsável ou administrador pode revogar vínculo aprovado, registrando autor, motivo e data.
6. Elegibilidade acadêmica exige vínculo `APPROVED` não revogado **e** universidade ativa.
7. Para evitar condições de corrida, operações de aprovação e revogação serão transacionais, com restrições no banco (índices únicos parciais quando apropriado).

### 3.3 Currículo-base e matching

- Cadastro inicial de estudante exige e-mail e senha; preenchimento do currículo-base é posterior.
- Currículo-base estruturado contempla nome, localização, resumo, formação, competências técnicas/comportamentais, experiência, projetos, cursos/certificações, interesses, GitHub/LinkedIn/portfólio. O MVP pode modelar seções repetíveis como JSONB validado para reduzir esforço.
- **Somente os dados do currículo-base Nexora alimentam o cálculo de compatibilidade**; PDF enviado numa candidatura não é ingerido pelo matching.
- Score calculado pelo backend com critérios e pesos explícitos (inicialmente: competências obrigatórias 45%, desejáveis 20%, interesses 20%, modalidade/localização 15%). Pesos são **hipóteses de implementação, não validação científica**.
- Recomendações em ambos os sentidos: vagas para estudante e estudantes para a empresa em suas vagas, retornando percentual e justificativa com competências correspondentes/ausentes.
- Análise semântica com embeddings/`AIProvider` é evolução P1, promovível a P0 se obrigatória ao TCC; nenhuma pontuação arbitrária deve ser delegada a uma LLM.

### 3.4 Vagas

- Empresa ativa cria, consulta, altera, encerra/reabre e exclui logicamente apenas suas vagas.
- Vaga inclui título, descrição, empresa, localização, modelo de trabalho, nível, competências obrigatórias e desejáveis, área e status.
- Apenas vagas `OPEN` de empresa ativa aceitam novas candidaturas.
- A listagem pública/autenticada pode ter filtros e paginação básicos.

### 3.5 Candidaturas e documentos

- **Pré-condição:** estudante ativo, com vínculo acadêmico válido, e vaga `OPEN` de empresa ativa.
- Uma candidatura por par estudante–vaga (unicidade no banco).
- Modalidade obrigatória e exclusiva:
  - `PLATFORM`: persistir **snapshot imutável** dos dados estruturados do currículo-base na data da candidatura.
  - `PDF`: anexar exatamente **um arquivo PDF próprio**, associado apenas àquela candidatura; tamanho máximo **5 MiB = 5.242.880 bytes**.
- A escolha do documento da candidatura **não altera o score**, que sempre usa o currículo-base da plataforma.
- Alterações posteriores no currículo-base não modificam snapshots já enviados.
- Empresa responsável e estudante proprietário podem visualizar o currículo da candidatura; admin autorizado também. Não disponibilizar PDF por URL pública permanente.
- Candidaturas possuem estados de andamento `SUBMITTED`, `IN_REVIEW`, `APPROVED`, `REJECTED` e `SUSPENDED`.
- Perda da validação acadêmica suspende candidaturas ainda em andamento (`SUBMITTED`/`IN_REVIEW`), preservando documentos e histórico; finalizadas (`APPROVED`/`REJECTED`) não são alteradas automaticamente.
- Candidatura `SUSPENDED` não avança no processo. Nova validação não a reativa automaticamente: empresa responsável ou admin realiza reativação explícita após nova verificação de elegibilidade; restaurar o estado anterior quando apropriado.

## 4. Requisitos funcionais — catálogo aprovado

**Legenda:** P0 = obrigatório no MVP; P1 = opcional, conforme tempo ou exigência do TCC.

| ID | Requisito | Prioridade |
|---|---|---|
| RF-01 | Autocadastro de estudante, empresa e universidade | P0 |
| RF-02 | Login e emissão de JWT | P0 |
| RF-03 | Consultar e editar dados básicos da própria conta | P0 |
| RF-04 | Autorizar operações por papel, status e propriedade | P0 |
| RF-05 | Criar, consultar e atualizar perfil profissional do estudante | P0 |
| RF-06 | Cadastrar competências, projetos e interesses profissionais | P0 |
| RF-07 | Criar, consultar e atualizar perfil institucional da universidade | P0 |
| RF-08 | Solicitar vínculo acadêmico com universidade | P0 |
| RF-09 | Listar e decidir solicitações de vínculo recebidas | P0 |
| RF-10 | Consultar e revogar vínculos acadêmicos | P0 |
| RF-11 | Registrar e gerenciar avaliações acadêmicas de estudantes vinculados | P1 |
| RF-12 | Criar, consultar e atualizar perfil empresarial | P0 |
| RF-13 | CRUD completo de vagas pertencentes à empresa | P0 |
| RF-14 | Listar e consultar vagas abertas, com filtros básicos | P0 |
| RF-15 | Registrar candidatura de estudante academicamente validado | P0 |
| RF-16 | Consultar candidaturas do estudante autenticado | P0 |
| RF-17 | Consultar candidaturas recebidas pela empresa | P0 |
| RF-18 | Atualizar o status das candidaturas recebidas | P0 |
| RF-19 | Suspender candidaturas em andamento quando a validação acadêmica deixar de ser válida | P0 |
| RF-20 | Calcular score de compatibilidade explicável entre estudante e vaga | P0 |
| RF-21 | Recomendar vagas compatíveis para estudantes | P0 |
| RF-22 | Recomendar estudantes compatíveis para empresas | P0 |
| RF-23 | Integrar análise semântica por meio de um provedor de IA | P1* |
| RF-24 | Aprovar/rejeitar universidades e gerenciar contas e recursos da plataforma | P0 |
| RF-25 | Registrar intervenções administrativas críticas | P0 |
| RF-26 | Criar, consultar e atualizar currículo-base estruturado do estudante | P0 |
| RF-27 | Escolher entre currículo Nexora e PDF (máx. 5 MiB) durante a candidatura | P0 |
| RF-28 | Preservar e disponibilizar o currículo efetivamente enviado, com acesso autorizado | P0 |

**Total: 28 RFs (26 P0, 2 P1).**  
**Nota:** RF-05/06 e RF-26 compartilham a mesma área funcional; RF-15 e RF-27/28 compartilham o fluxo transacional de candidatura. Não criar implementações redundantes por causa da separação documental dos requisitos.

## 5. Requisitos não funcionais

| ID | Requisito | Critério mínimo verificável |
|---|---|---|
| RNF-01 | Segurança e autorização | Spring Security + JWT; hash forte de senha; verificar papel, status e propriedade de cada recurso; não permitir escalada de privilégio |
| RNF-02 | Persistência e integridade | PostgreSQL 18, JPA/Hibernate, Flyway; FKs, unicidades e operações transacionais em decisões de vínculo/candidatura; dados persistentes após reinício |
| RNF-03 | Arquitetura e manutenção | Java 21 + Spring Boot como monólito modular; módulos de negócio isolados; REST/JSON; sem microsserviços |
| RNF-04 | Contratos da API | Validação de DTOs, status HTTP adequados, paginação básica, erros padronizados; endpoints versionados (`/api/v1`) |
| RNF-05 | Documentação | OpenAPI/Swagger disponível no ambiente de desenvolvimento e documentação breve dos fluxos críticos |
| RNF-06 | Deploy e disponibilidade | Containers Docker, configuração por ambiente, HTTPS público, banco e armazenamento persistentes, reinício e healthcheck |
| RNF-07 | Observabilidade | Logs úteis sem senhas/tokens/dados sensíveis; endpoint de health; tratamento centralizado de exceções |
| RNF-08 | Desempenho | Meta inicial (não garantia): operações comuns em menos de 1 s sob carga leve de demonstração, excluindo IA externa e upload/download |
| RNF-09 | Qualidade e testes | Testes automatizados de autenticação, autorização, aprovação/revogação, elegibilidade, candidatura e score; teste ponta a ponta demonstrável |
| RNF-10 | Privacidade | Minimização e controle de acesso a dados pessoais; currículo e candidaturas acessíveis apenas a atores autorizados; política básica de privacidade |
| RNF-11 | Segurança do upload | Somente PDF válido (extensão, MIME e assinatura), limite 5 MiB no backend, nome/chave gerada pelo servidor, proteção contra path traversal, acesso autenticado, rejeição de arquivos inválidos |
| RNF-12 | Armazenamento de arquivos | PDFs persistidos em object storage privado (preferível) ou volume privado persistente; não salvar em filesystem efêmero do container; backups conforme capacidade do provedor |
| RNF-13 | Consistência histórica | Snapshot imutável do currículo `PLATFORM`; manter documento e estado anterior em suspensões e auditoria; não sobrescrever candidatura histórica |
| RNF-14 | Configuração e segredos | Segredos por variáveis/secret manager, `.env` ignorado no Git, CORS restrito ao domínio publicado, evitar exposição pública do banco |

## 6. Modelo de dados mínimo

| Tabela | Responsabilidade |
|---|---|
| `users` | credenciais, papel e status |
| `students` | usuário e currículo-base estruturado (campos/JSONB validado) |
| `companies` | dados institucionais da empresa |
| `universities` | dados institucionais e análise de aprovação |
| `university_affiliations` | histórico de pedidos/vínculos, status, autor, motivo e datas |
| `skills` | catálogo padronizado de competências |
| `student_skills` | competências do currículo-base |
| `jobs` | vaga, empresa, status e requisitos |
| `job_skills` | competências obrigatórias/desejáveis de vaga |
| `applications` | candidatura, estado, modalidade, snapshot ou chave privada do PDF, histórico mínimo/suspensão |
| `admin_audit_logs` | ações administrativas críticas |

**Opcional P1:** `academic_reviews` para avaliações/recomendações da universidade.

**Índices/restrições essenciais:** `users.email` único; `applications(student_id, job_id)` único; no máximo um vínculo `PENDING` e um `APPROVED` não revogado por estudante; chave estrangeira para entidades relacionadas; checagem de exclusividade entre snapshot e PDF na candidatura.

**Atenção ao projeto do banco:** os estados de empresa/universidade e os estados de vínculo devem ser distintos. A implementação não deve depender apenas de validação na interface; regras são aplicadas no backend e, quando cabível, no PostgreSQL.

## 7. API — agrupamento sugerido (não contrato fechado)

- `/api/v1/auth`: registro, login
- `/api/v1/users/me`: conta autenticada
- `/api/v1/students/me` e `/api/v1/students/me/resume`: perfil/currículo-base
- `/api/v1/companies/me`: perfil institucional
- `/api/v1/universities`: catálogo e perfil; solicitações do vínculo
- `/api/v1/affiliations`: pedido, decisão e revogação
- `/api/v1/jobs`: CRUD, consulta e status
- `/api/v1/applications`: candidatar, listar, consultar, atualizar andamento e acessar documento
- `/api/v1/matching`: vagas recomendadas e candidatos recomendados
- `/api/v1/admin`: aprovações institucionais, usuários, auditoria e intervenções

Preferir contratos simples e estáveis para permitir frontend em paralelo. Não criar endpoints separados quando um endpoint de atualização de perfil resolver a necessidade.

## 8. Fluxo final esperado do MVP (demonstração ponta a ponta)

1. Universidade realiza autocadastro e fica `PENDING_APPROVAL`.
2. Administrador aprova a universidade; a conta fica `ACTIVE`.
3. Estudante cria conta com e-mail e senha e monta seu currículo-base Nexora.
4. Estudante solicita vínculo com universidade; universidade aprova.
5. Empresa se cadastra e cria vaga com requisitos/competências.
6. Nexora calcula score explicável com base no **currículo-base** e recomenda vaga ao estudante e estudante à empresa.
7. Estudante validado se candidata escolhendo **currículo Nexora (snapshot)** **ou** **PDF externo válido de até 5 MiB**.
8. Empresa consulta a candidatura, o documento escolhido e o score, podendo alterar o status.
9. Universidade revoga o vínculo; candidaturas em andamento são suspensas, sem apagar documentos/histórico.
10. Após nova validação, a empresa ou o admin pode reativar explicitamente a candidatura elegível.
11. Fluxo é acessível em ambiente publicado com HTTPS, com controle de acesso e dados persistidos.

## 9. Condições de aceite e corte de escopo

**MVP aceito se:** o fluxo acima puder ser demonstrado com contas distintas, permissões reais, dados persistentes, matching calculado pelo backend e upload/download protegido.

**Ordem de corte se o prazo apertar ainda mais:** eliminar primeiro RF-11 (avaliações), depois detalhes não essenciais da interface, depois RF-23 **somente se** a exigência acadêmica permitir. Não cortar validação universitária, elegibilidade de candidatura, escolha de currículo, score explicável ou deploy.

**Critério acadêmico:** pesos do matching, resultados e desempenho devem ser apresentados como proposta/experimento, não como precisão ou efetividade comprovada sem avaliação empírica.

## 10. Tecnologias confirmadas

| Camada | Escolha |
|---|---|
| Backend | Java 21, Spring Boot, Maven, Spring Security/JWT |
| Persistência | PostgreSQL 18, JPA/Hibernate, Flyway |
| Frontend | Vue 3, TypeScript, Vite |
| Infra | Docker + Docker Compose no Ubuntu WSL2 (desenvolvimento), hospedagem pública com HTTPS |
| Documentação | OpenAPI/Swagger |
| Matching | Score determinístico e explicável; IA semântica opcional/condicional |

**Documentos relacionados:** `HISTORIAS_USUARIO_MVP_NEXORA.md` contém o backlog em ordem de implementação, critérios de aceite e checklist de progresso.
