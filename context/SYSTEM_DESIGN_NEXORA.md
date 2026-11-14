# Nexora — System Design do Backend (MVP 1.0)

> **Status:** aprovado para orientar a implementação do MVP.  
> **Aplicação:** `nexora-api` — Java 21, Spring Boot, PostgreSQL 18.  
> **Objetivo:** entregar um backend seguro, legível, testável e funcional em poucos dias, sem complexidade especulativa.

## 1. Princípio arquitetural central

> **Complexidade só deve ser adicionada quando resolver um problema real do domínio ou um requisito concreto do projeto.**

Este documento orienta o desenvolvimento por IA e por pessoas. Em caso de conflito, respeitar primeiro as regras de negócio e os requisitos aprovados do MVP e sinalizar a divergência; não alterar o escopo silenciosamente.

**Prioridades:** correção das regras de negócio e segurança → conclusão do fluxo ponta a ponta → testes críticos → simplicidade → refinamentos. Aplicar TDD (Red → Green → Refactor) nas regras críticas, KISS, YAGNI, SOLID com pragmatismo, alta coesão, baixo acoplamento e refatoração incremental.

## 2. Tecnologias e limites do MVP

| Área | Decisão |
|---|---|
| Arquitetura | **Monólito modular**, uma aplicação backend |
| Linguagem e framework | Java 21 e versão de Spring Boot efetivamente configurada no `pom.xml` |
| API | REST, JSON, prefixo `/api/v1` |
| Banco | PostgreSQL 18, Spring Data JPA/Hibernate |
| Migração | Flyway; esquema versionado por SQL |
| Autenticação/autorização | Spring Security + JWT; RBAC e verificação de propriedade de recursos |
| Validação | Jakarta Bean Validation nos DTOs e regras de domínio nos services |
| Erros | `@RestControllerAdvice` com `ProblemDetail` e código estável de negócio |
| Documentação | OpenAPI/Swagger, compatível com a versão real do Spring Boot |
| Infraestrutura | Docker + Docker Compose, executados via Ubuntu WSL2 |
| Frontend | Vue 3 + TypeScript + Vite, projeto separado, consumidor da API |
| IA | Matching próprio e explicável; integração semântica por um provedor atrás de abstração pequena quando implementada |

**Fora de escopo:** microsserviços, API Gateway, CQRS, event sourcing, DDD formal em todas as camadas, mensageria, Redis, Kubernetes, bibliotecas de mapeamento genéricas sem necessidade, abstrações para cada classe e ferramentas introduzidas apenas por antecipação de escala.

## 3. Arquitetura de execução e camadas

```text
Vue 3 / TypeScript
       |
   HTTP REST/JSON
       v
Spring Security (autenticação e autorização)
       |
   Controller (HTTP + DTOs)
       |
   Service (casos de uso, regras, transações)
       |
   Repository (Spring Data JPA)
       |
   PostgreSQL 18

Matching Service -> AIProvider (opcional, somente quando necessário)
```

### 3.1 Controller — fronteira HTTP

**Responsabilidades:** declarar rotas, receber DTOs, aplicar `@Valid`/`@Validated`, identificar contexto autenticado, chamar service e devolver DTOs com status HTTP coerente.

**Não deve:** implementar regra de negócio, executar consultas JPA, manipular entidades como contrato público, determinar acesso a recursos com confiança exclusiva em dados fornecidos pelo cliente, montar regras de transação ou processar arquivos diretamente no banco.

Uma controller deve ser curta; sua complexidade principal é o contrato HTTP.

### 3.2 Service — casos de uso e domínio

**Responsabilidades:** coordenar operações, aplicar regras de negócio e autorização contextual (ex.: vaga pertence à empresa autenticada), validar estados, lidar com transações e lançar exceções de negócio claras.

Não depender de `HttpServletRequest`, `ResponseEntity` ou detalhes de apresentação. A lógica comum deve permanecer no módulo que a possui; evitar `SharedService` genérico. Services podem usar repositories e serviços específicos de outros módulos quando necessário, com dependências claras e sem ciclos.

### 3.3 Repository — persistência

Interfaces Spring Data JPA, apenas com consultas necessárias aos casos de uso. Não implementar regras de negócio em queries ou criar uma camada extra de DAO sem motivo. Evitar N+1 nas consultas relevantes usando projeções ou `EntityGraph` quando necessário e medido.

### 3.4 Entity — persistência e invariantes simples

Entidades representam o modelo persistido, relacionamentos e invariantes locais. Não usar entity em request/response HTTP. Evitar serialização direta de relações JPA e exposição involuntária de dados sensíveis. Não usar cascatas que possam apagar histórico de negócios.

### 3.5 DTO — contrato externo

DTOs de entrada e saída explícitos, preferencialmente `record` do Java 21 para objetos imutáveis. Separar `CreateXRequest`, `UpdateXRequest`, `XResponse` quando necessário. Usar constraints como `@NotBlank`, `@Email`, `@Size` e `@NotNull`. Não introduzir mappers genéricos por padrão: métodos simples de conversão explícita bastam até haver repetição real.

**Segurança:** nunca aceitar da requisição `ownerId`, `companyId`, `studentId`, `role`, `accountStatus` ou `approvedBy` como fonte de autoridade para ações autenticadas; derivar identidade e poderes do contexto de segurança, salvo endpoints administrativos específicos com validação de autorização.

## 4. Organização por módulos (feature-first)

```text
nexora-api/src/main/java/br/com/nexora/api/
├── NexoraApiApplication.java
├── authentication/
├── users/
├── students/
├── universities/
├── companies/
├── jobs/
├── applications/
├── matching/
├── administration/
└── shared/
    ├── config/
    ├── security/
    └── exception/
```

Criar subpastas **somente quando houver código**. Um módulo pode conter `controller/`, `dto/`, `service/`, `entity/` e `repository/`. Preferir APIs de service explícitas a dependências cruzadas de repositories. Não criar interfaces para cada service: usar classes concretas até surgir mais de uma implementação ou uma fronteira necessária. A interface `AIProvider` é uma exceção justificada pela possibilidade aprovada de comparar/trocar provedores de IA.

### Responsabilidades de domínio

| Módulo | Responsabilidade |
|---|---|
| `authentication` | registro, login, JWT, autenticação |
| `users` | usuário, status de conta, dados comuns |
| `students` | perfil e currículo-base estruturado; competências, projetos, interesses |
| `universities` | perfil, solicitação/decisão/revogação de vínculo, avaliação acadêmica opcional |
| `companies` | perfil institucional da empresa |
| `jobs` | vagas, requisitos, habilidades e propriedade da empresa |
| `applications` | candidatura, modalidade de currículo, snapshot/PDF e estados |
| `matching` | cálculo, justificativa e ordenação de recomendações em ambos os sentidos |
| `administration` | aprovação de universidades, suspensão, intervenção e auditoria |
| `shared` | configurações realmente transversais: autenticação, handler de erros, etc. |

## 5. Regras do domínio que nunca podem ser contornadas

1. **Papéis:** `STUDENT`, `COMPANY`, `UNIVERSITY`, `ADMIN`.
2. **Autocadastro:** estudantes e empresas ficam ativos; universidades entram em `PENDING_APPROVAL` e só atuam plenamente depois de aprovação por admin. `ADMIN` nunca é criado por registro público.
3. **Contas suspensas:** bloqueiam operações autenticadas, inclusive quando JWT ainda está dentro da validade; verificar status atual no servidor.
4. **Vínculo acadêmico:** universidade ativa recebe solicitação; decide aprovar/rejeitar; estudante pode ter no máximo um vínculo `APPROVED` vigente e uma solicitação `PENDING`; revogação preserva histórico e autoria.
5. **Candidatura:** apenas estudante com vínculo acadêmico aprovado vigente **e universidade ativa** pode enviar; vaga deve estar aberta e empresa ativa; impedir duplicidade estudante-vaga; empresa só gerencia candidaturas de suas próprias vagas.
6. **Revogação/suspensão da validação:** candidaturas em andamento passam a `SUSPENDED`, preservando histórico e currículo; finalizadas permanecem inalteradas. Reativação exige operação explícita e nova verificação de elegibilidade.
7. **Vagas:** CRUD pela empresa proprietária; exclusão lógica para preservar candidaturas e rastreabilidade; admin pode intervir de forma auditada.
8. **Currículo-base:** preenchido após cadastro; é a **única fonte** para o matching, mesmo que o estudante anexe outro PDF para a vaga.
9. **Documento da candidatura:** escolha **exclusiva** `PLATFORM` (snapshot imutável do currículo estruturado, por exemplo JSONB) ou `PDF` (arquivo PDF externo específico daquela candidatura). Alterar currículo-base não modifica candidaturas existentes.
10. **PDF:** tamanho máximo de **5 MiB = 5.242.880 bytes**; validar tamanho, tipo de arquivo e assinatura PDF no backend; arquivo privado, nome de armazenamento gerado pelo servidor; download apenas mediante autorização. Nunca usar diretório efêmero do container como armazenamento permanente. Garantir durabilidade no deploy, com volume persistente privado ou object storage adequado.
11. **Matching:** calcular score no backend com critérios transparentes; recomendar vagas para estudantes e estudantes para empresas; IA semântica não deve inventar score arbitrário. Registrar versão/critérios quando relevante para explicação.
12. **Administração:** ações críticas com autor, data e tipo de operação; nenhum papel recebe permissão global implicitamente por ser dono de um recurso diferente.

## 6. Segurança e autorização

- **JWT** com assinatura verificada, validade limitada e segredos em variáveis de ambiente; nunca guardar senhas ou tokens em logs.
- Senhas com hash seguro (BCrypt ou equivalente); não devolver `passwordHash` ou segredos em DTOs.
- Spring Security valida autenticação, papel e status atual de conta; services validam propriedade e transições de estados.
- Não confiar em `studentId`/`companyId` fornecidos pelo frontend para identificar o proprietário de uma ação.
- Respostas coerentes: `401` não autenticado, `403` sem privilégio, `404` recurso inexistente/inacessível quando adequado, `409` conflito de estado ou duplicidade, `422` quando regra de negócio invalidar ação (ou `409`, de modo consistente).
- Aplicar proteção contra upload abusivo: limite por arquivo e multipart, validação de conteúdo, controle de download, não executar arquivos, evitar path traversal e nunca servir uploads como recursos estáticos públicos.
- CORS somente para origens necessárias; HTTPS no deploy; configuração sensível fora do Git.
- Não adicionar permissões dinâmicas por usuário no MVP: **RBAC fixo + status + propriedade** atende ao escopo.

## 7. Tratamento de erros e API REST

Centralizar em `@RestControllerAdvice`, preferencialmente usando `ProblemDetail` (`application/problem+json`) com contrato consistente:

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "O estudante não possui vínculo acadêmico aprovado.",
  "instance": "/api/v1/applications",
  "code": "STUDENT_NOT_VALIDATED"
}
```

O campo `code` é estável e utilizado pelo frontend; `detail` é mensagem adequada para o usuário, sem stack trace ou dados internos. Normalizar erros de validação em estrutura previsível por campo quando aplicável. Capturar exceções específicas (recurso não encontrado, acesso negado, estado inválido, duplicidade, upload inválido); logar imprevistos no servidor e responder erro genérico 500 sem expor detalhes internos.

Padrões HTTP: `POST` criação (`201` quando adequado), `GET` leitura, `PUT` atualização integral ou `PATCH` parcial/status, `DELETE` para desativação/exclusão lógica conforme contrato. Paginar listagens; usar IDs estáveis; não mudar URLs/DTOs silenciosamente após integração frontend.

### Exemplos de DTOs

```java
public record CreateJobRequest(
    @NotBlank String title,
    @NotBlank String description
) {}
```

A empresa proprietária da nova vaga é **obtida do usuário autenticado**, não de um ID arbitrário enviado no JSON.

## 8. Persistência, integridade e transações

- Flyway é responsável pela evolução do esquema. Em ambientes compartilhados/deploy, não usar `ddl-auto=update`; usar `validate` quando compatível com o estado da aplicação.
- Constraints no PostgreSQL para e-mails únicos, duplicidade de candidatura e integridade referencial. Usar índices únicos parciais para uma solicitação pendente e um vínculo aprovado vigente por estudante, de acordo com a modelagem exata.
- Operações de decisão do vínculo, revogação e suspensão de candidaturas devem ser **atômicas** (`@Transactional`) e seguras contra concorrência; evitar aprovações simultâneas conflitantes.
- Preferir transações no service, não na controller. Operações de consulta podem ser `readOnly = true` quando adequado.
- Não armazenar arquivos PDF no repositório Git ou no filesystem efêmero da imagem. Guardar apenas metadados/referências no PostgreSQL; definir limpeza de arquivos órfãos quando necessário.
- Dados de produção têm persistência, backup e migrações previsíveis. Nunca apagar volumes durante diagnóstico sem autorização.

## 9. Matching: implementação mínima e explicável

- Recebe currículo-base **estruturado** e critérios da vaga; dados do PDF enviado à empresa não influenciam o score.
- Normaliza skills e compara requisitos obrigatórios/desejáveis e interesses/preferências com pesos explícitos, inicialmente propostos e **não validados cientificamente**.
- Retorna score, critérios atendidos, lacunas e justificativa baseada em evidências; ordenar resultados de forma estável.
- Um eventual provedor de IA pode enriquecer similaridade semântica ou explicação, com erro/timeout tratado sem impedir a operação estruturada.
- Interfaces apenas nas fronteiras que exigem substituição real, como `AIProvider`; não criar orquestração distribuída, filas ou banco vetorial apenas por antecipação.
- Escrever testes de casos-limite e consistência: sem skills, todas skills, requisitos obrigatórios ausentes, critérios iguais, score limitado ao intervalo definido.

## 10. Estratégia de testes (TDD pragmático)

**Prioridade máxima:** testes de regras de negócio e segurança antes ou junto da implementação.

1. **Unitários:** validação de vínculo, regras de candidatura, transições de status, score e explicação do matching.
2. **Integração:** repositories/constraints e operações transacionais no PostgreSQL; endpoints críticos com autenticação/autorização, quando o custo for viável.
3. **Contrato:** DTOs, códigos HTTP e estrutura de erros para os fluxos integrados ao frontend.
4. **Upload:** rejeição de extensão/assinatura incorreta, arquivos maiores que 5 MiB, acesso indevido e preservação do anexo/snapshot.

Evitar testes irrelevantes de getters/setters e mocks extensivos que só repetem a implementação. Executar testes no container ou ambiente Docker; não exigir Maven ou Java locais.

**Definição de pronto de uma história:** critérios de aceite implementados, testes críticos passando, API documentada se alterada, validação de segurança/regras de propriedade, migration aplicada quando houver, nenhuma regressão conhecida, status do backlog atualizado.

## 11. Observabilidade mínima e deploy

- Logs legíveis com contexto suficiente para diagnóstico, sem senhas/tokens/PDFs ou informações sensíveis.
- Healthcheck da API e PostgreSQL; não inventar endpoints de negócio para demonstrar saúde.
- Configuração via variáveis de ambiente; perfis de dev/prod somente quando ajudam a controlar diferenças reais.
- HTTPS público, CORS do domínio correto, volumes/armazenamento persistente e migrations no processo de deploy.
- Erros de serviços externos de IA devem ser tratados de forma controlada; não bloquear o MVP caso o score estruturado esteja operacional.

## 12. Fluxo de trabalho obrigatório para assistentes de IA

Para cada história de usuário:

1. Ler escopo, requisitos e dependências da história; inspecionar o código existente antes de alterar.
2. Identificar a menor mudança funcional necessária e regras de segurança afetadas.
3. Descrever sucintamente a abordagem, especialmente se houver migration ou alteração de contrato.
4. Escrever testes que falhem para as regras críticas (**Red**).
5. Implementar o mínimo para fazê-los passar (**Green**).
6. Refatorar com testes verdes, sem acrescentar camadas ou tecnologias especulativas (**Refactor**).
7. Executar verificações no Docker, validar endpoint/DTO/erro e requisitos de autorização.
8. Atualizar documentação e checklist da história; relatar o que foi testado, o que não foi e eventuais pendências.
9. Não declarar conclusão sem evidência de build/testes correspondentes.

**Proibido sem necessidade comprovada:** controller com regra de negócio, repository acessado pela controller, entity exposta em JSON, exceções engolidas, `catch (Exception)` usado para mascarar falhas, hardcode de segredos, falha silenciosa em uploads, abstração universal de service/repository, frameworks adicionais apenas por padrão de mercado.

## 13. Exemplo de fluxo de implementação

**Caso: estudante validado se candidata utilizando currículo da plataforma.**

```text
POST /api/v1/jobs/{jobId}/applications
  -> Spring Security identifica STUDENT ativo
  -> Controller valida CreateApplicationRequest(resumeType=PLATFORM)
  -> ApplicationService:
       confere vaga aberta e empresa ativa
       confere vínculo acadêmico APPROVED vigente e universidade ativa
       impede candidatura duplicada
       lê currículo-base e cria snapshot imutável
       persiste candidatura SUBMITTED
  -> Controller devolve 201 + ApplicationResponse
```

Se `resumeType=PDF`, a service valida anexo (até 5 MiB, PDF verdadeiro), armazena em local privado e persiste uma referência. **O matching continua usando o currículo-base.** A empresa somente consulta documentos de candidaturas às suas próprias vagas; a revogação do vínculo suspende candidaturas em andamento sem apagar o documento enviado.

## 14. Relação com os documentos de governança

Ler e respeitar, nesta ordem de contexto:

1. `docs/ESCOPO_MVP_NEXORA.md` — escopo, regras e requisitos aprovados.
2. `docs/HISTORIAS_USUARIO_MVP_NEXORA.md` — backlog e ordem de execução.
3. `docs/DIRETRIZES_ENGENHARIA_IA_NEXORA.md` — princípios de qualidade e atuação da IA.
4. **Este documento** — design técnico e convenções de implementação.

Se algum documento estiver desatualizado ou contraditório, sinalizar o conflito antes de fazer mudanças de escopo; preservar as decisões de negócio mais recentes explicitamente aprovadas.

---

**Regra final:** implementar o menor código correto, seguro e verificável que conclua a história atual. A arquitetura existe para sustentar a entrega do Nexora, não para competir com ela.
