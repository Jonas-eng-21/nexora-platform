# Guia de Commits — Conventional Commits v1.0.0 (Nexora Platform)

> **Propósito deste arquivo**: Servir como referência obrigatória para geração e execução de commits no projeto **Nexora Platform**. Sempre que solicitado a criar commits, siga rigorosamente as regras abaixo combinadas com o contexto da tarefa fornecido.

---

## 1. Diretrizes Gerais do Projeto Nexora
1. **Idioma**: Todas as mensagens de commit (título, corpo e rodapé) devem ser escritas em **inglês**.
2. **Atomicidade**: Prefira **commits atômicos** separados por responsabilidade lógica (ex.: separar configuração de ambiente, migrações/entidades, código de negócio, infraestrutura Docker e documentação), a menos que seja explicitamente solicitado um commit único.
3. **Modo Imperativo**: Escreva a descrição no presente do indicativo / imperativo em inglês (como se completasse a frase *"If applied, this commit will..."*):
   - ✅ `add`, `configure`, `fix`, `update`, `remove`, `refactor`
   - ❌ `added`, `adds`, `adding`, `configured`, `fixed`
4. **Formatação da Descrição**:
   - Letras minúsculas no `type` e no `scope`.
   - Comece a `<description>` com letra minúscula (exceto nomes próprios/acrônimos como `Docker`, `PostgreSQL`, `JWT`, `README`).
   - **Não** coloque ponto final (`.`) no fim da linha de título (`<description>`).
   - Mantenha a primeira linha concisa (idealmente até 72 caracteres).

---

## 2. Estrutura Formal (Conventional Commits v1.0.0)

```text
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Regras Sintáticas Obrigatórias
- **Dois-pontos e espaço (`: `)**: Deve haver imediatamente dois-pontos seguidos de um espaço após o tipo/escopo (`feat(api): add user endpoint`).
- **Linha em branco antes do corpo (`body`)**: O corpo deve começar exatamente após **uma linha em branco** depois da descrição.
- **Linha em branco antes do rodapé (`footer`)**: O rodapé deve começar após **uma linha em branco** depois do corpo (ou da descrição, se não houver corpo).
- **Formato de Rodapés (*Git Trailers*)**:
  - Usar `<token>: <value>` ou `<token> #<value>`.
  - Tokens de rodapé **não podem conter espaços** (usar hífen, ex.: `Reviewed-by: Name`, `Refs: #123`), com a única exceção de `BREAKING CHANGE: <description>` (ou `BREAKING-CHANGE:`).

---

## 3. Tipos Permitidos (`<type>`) e Impacto SemVer

| Tipo | Quando utilizar | Impacto SemVer |
| :--- | :--- | :--- |
| **`feat`** | Novo recurso ou funcionalidade para a aplicação/API/frontend | `MINOR` (`0.X.0`) |
| **`fix`** | Correção de um bug que afetava o comportamento | `PATCH` (`0.0.X`) |
| **`docs`** | Alterações exclusivamente em documentação (`README.md`, Javadoc, etc.) | Nenhum |
| **`style`** | Formatação, indentação, espaços, imports (sem alterar semântica/lógica) | Nenhum |
| **`refactor`** | Refatoração de código que não corrige bug nem adiciona feature | Nenhum |
| **`perf`** | Mudança de código voltada especificamente para ganho de performance | Nenhum |
| **`test`** | Adição, ajuste ou correção de testes automatizados (unitários/integração) | Nenhum |
| **`build`** | Sistema de build, dependências externas (`pom.xml`, `package.json`, `Dockerfile`, `compose.yaml`) | Nenhum |
| **`ci`** | Arquivos e pipelines de CI/CD (GitHub Actions, scripts de deploy) | Nenhum |
| **`chore`** | Configurações gerais, `.gitignore`, `.env.example`, manutenção sem tocar em código de produção/teste | Nenhum |
| **`revert`** | Reversão de um commit anterior (incluir `Refs: <hash>` no rodapé) | Variável |

---

## 4. Escopos Sugeridos (`<scope>`) no Nexora Platform

O escopo deve ser um substantivo entre parênteses indicando a área ou módulo afetado. Use quando agregar clareza:

- **`api`**: Configurações gerais ou código transversal do backend (`nexora-api`).
- **`auth` / `security`**: Autenticação, JWT, Spring Security, controle de acesso.
- **`db` / `flyway`**: Migrações SQL Flyway, esquemas e ajustes de banco de dados.
- **`docker`**: `Dockerfile`, `.dockerignore`, `compose.yaml`, `compose.dev.yaml`.
- **`deps`**: Atualização ou adição de dependências no `pom.xml` ou `package.json`.
- **`web` / `ui`**: Aplicação frontend (Vue 3 / TypeScript / Vite).
- **`readme`**: Documentação principal no `README.md`.
- **`<domínio>`**: Nome do módulo de negócio (ex.: `user`, `tenant`, `billing`, `customer`).

*(Se a mudança for global na raiz do repositório e não pertencer a um escopo específico, omita o escopo — ex.: `chore: add root gitignore and environment variables example`).*

---

## 5. Quebras de Compatibilidade (*Breaking Changes*)

Qualquer mudança que quebre contratos existentes (endpoints da API, variáveis de ambiente obrigatórias sem fallback, esquema incompatível) incrementa `MAJOR` (`X.0.0`) e deve ser indicada por:

1. **`!` antes de `:` no cabeçalho**:
   ```text
   feat(api)!: change authentication response payload structure
   ```
2. **Ou/e `BREAKING CHANGE:` no rodapé**:
   ```text
   refactor(auth)!: replace session auth with stateless JWT

   BREAKING CHANGE: session cookies are no longer accepted; clients must send Bearer tokens in the Authorization header.
   ```

---

## 6. Quando Usar o Corpo (`body`)

- **Commits simples e autoexplicativos** (ex.: `docs(readme): ...` ou ajustes pequenos): Apenas a linha de título é suficiente.
- **Commits com múltiplos arquivos ou decisões técnicas importantes**: Adicione um corpo explicando **o quê** e **por quê** (motivação e decisões técnicas do contexto), em formato de parágrafo curto ou lista com `- `.

---

## 7. Exemplos de Referência do Projeto

### Exemplo 1 — Configuração / Manutenção (`chore`)
```text
chore: add root gitignore and environment variables example

Ignore local .env files, IDE metadata, and local context notes while
providing .env.example with default database and backend configuration.
```

### Exemplo 2 — Nova configuração/funcionalidade na API (`feat`)
```text
feat(api): configure postgresql datasource, flyway, and dev profile

Set Hibernate ddl-auto to validate, disable open-in-view, enable Flyway
migrations, and configure debug logging for the dev profile.
```

### Exemplo 3 — Infraestrutura e Build (`build`)
```text
build(docker): containerize backend and add PostgreSQL 18 compose setup

- Add multi-stage Dockerfile for nexora-api using Maven 3.9 and Eclipse Temurin 21 JRE with non-root user
- Add .dockerignore to exclude build artifacts and sensitive files from build context
- Add compose.yaml orchestrating PostgreSQL 18 (with healthcheck and persistent volume) and backend service
- Add compose.dev.yaml override for bind-mounted development workflow
```

### Exemplo 4 — Documentação (`docs`)
```text
docs(readme): document Docker Compose development environment and commands
```

---

## 8. Checklist para Geração de Commits (Fluxo de Trabalho)

Sempre que o usuário pedir novos commits referenciando este guia:
1. Ler o arquivo de contexto da tarefa informado pelo usuário e inspecionar `git status` / `git diff`.
2. Verificar se arquivos sensíveis (`.env`, segredos) ou de rascunho estão devidamente ignorados pelo `.gitignore`. (A pasta `context/` é versionada: não colocar segredos nela.)
3. Agrupar os arquivos modificados/criados em **commits atômicos** lógicos (ou commit único, se solicitado).
4. Redigir as mensagens em **inglês** seguindo rigorosamente a estrutura `<type>[scope]: <description>` + `[body]` quando aplicável.
5. Apresentar a proposta de commits (arquivos + mensagem) ou executá-los conforme instruído pelo usuário.

