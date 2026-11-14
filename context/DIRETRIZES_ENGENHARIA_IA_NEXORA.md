# Nexora — Diretrizes obrigatórias de engenharia de software para desenvolvimento assistido por IA

> **Status:** norma de desenvolvimento do projeto  
> **Aplicação:** Claude Code e qualquer outro agente/assistente de IA que crie, altere, revise ou teste código do Nexora  
> **Prioridade:** estas diretrizes orientam a implementação do escopo aprovado do MVP; não autorizam alterar requisitos de negócio  
> **Objetivo:** entregar rapidamente um MVP funcional, seguro, compreensível e sustentável, sem trocar qualidade crítica por volume de código.

## 1. Princípio arquitetural central — inegociável

**A complexidade só deve ser adicionada quando resolver um problema real do domínio ou um requisito concreto do projeto.**

A velocidade da geração automática de código **não** justifica arquitetura excessiva, dependências adicionais, camadas artificiais ou funcionalidades não solicitadas. A solução preferida é sempre **a menor solução correta, testável e segura que satisfaça os critérios de aceite**.

Na dúvida entre uma solução simples que atende o requisito e uma abstrata que apenas antecipa possibilidades, **escolha a simples**. Não confunda simplicidade com ausência de validação, segurança ou testes essenciais.

## 2. Fonte de verdade e limites do agente

1. Antes de implementar uma tarefa, leia o escopo do MVP, a história de usuário correspondente, suas regras de negócio e o código já existente.
2. Trate os documentos de requisitos e US aprovados como **fonte de verdade funcional**. Se houver contradição, lacuna ou requisito que inviabilize a implementação, exponha o problema e apresente a decisão mínima necessária; **não invente uma regra**.
3. Inspecione nomes de pacotes, entidades, migrações, perfis Spring, configuração Docker e padrões já empregados. **Reutilize o que funciona**; não gere uma segunda implementação paralela.
4. Implemente exclusivamente o escopo da história atual e as dependências estritamente necessárias. Não acrescente recursos “aproveitando que já está mexendo”.
5. Preserve a arquitetura existente: **monólito modular** com backend **Java 21 + Spring Boot**, **PostgreSQL + Flyway**, frontend **Vue 3 + TypeScript + Vite**, containers gerenciados por **Docker Compose**.
6. Java, Maven, PostgreSQL e Node.js devem executar nos containers, não ser exigidos como instalações no Windows/Ubuntu WSL2.
7. Não introduza microsserviços, Redis, RabbitMQ, Kubernetes, gateways, filas, caches ou frameworks extras sem necessidade comprovada e aprovação para mudança de escopo.
8. Nunca execute comandos destrutivos, apague dados/volumes, faça migrações irreversíveis, altere segredos ou force push sem autorização explícita.

## 3. TDD — Test-Driven Development como bússola

**Para regras de negócio, segurança, autorização, estados e matching, adote obrigatoriamente o ciclo Red → Green → Refactor.**

### Red — teste primeiro

- Traduza o requisito e os critérios de aceite em cenários verificáveis.
- Escreva o menor teste que **falhe pela ausência da funcionalidade**, não por erro de infraestrutura ou configuração.
- Inclua pelo menos um cenário positivo e os casos negativos que expressem restrições importantes.
- Não crie testes artificiais apenas para aumentar cobertura.

### Green — implementação mínima

- Escreva o menor código de produção capaz de fazer o teste passar.
- Evite adicionar interfaces, heranças, fábricas, eventos ou generalizações sem necessidade presente.
- Corrija a causa do problema; **não enfraqueça o teste** para fazê-lo passar.

### Refactor — melhoria com segurança

- Elimine duplicação relevante, melhore nomes, coesão e limites de responsabilidade.
- Mantenha os testes passando durante a refatoração.
- Não misture refatorações amplas e desconexas com uma pequena feature.

**Exceção pragmática:** arquivos simples de configuração, código gerado e interfaces triviais podem ser implementados diretamente, mas devem ser validados pelo procedimento mais adequado (build, lint, testes de integração, análise estática ou verificação manual). Não invente um teste unitário sem valor para cumprir formalidade.

## 4. KISS — Keep It Simple

- Prefira implementações diretas, nomes explícitos e funções pequenas com intenção clara.
- Faça tratamento de erros compreensível e consistente.
- Não crie “framework dentro do framework”, helpers genéricos para um único uso ou configurações difíceis de rastrear.
- Comentários devem explicar **por que** determinada decisão existe, não narrar linha por linha o que o código já mostra.
- Escolha legibilidade e previsibilidade acima de soluções engenhosas.

## 5. YAGNI — You Aren't Gonna Need It

- **Não implemente funcionalidades futuras** só porque estão previstas como possíveis evoluções.
- Não crie abstrações por hipóteses vagas de troca de banco, escala, novos perfis, integrações ou fornecedores.
- Crie uma interface quando houver uma fronteira arquitetural real, mais de uma implementação necessária, isolamento útil para teste ou requisito explícito de substituição.
- **Exceção fundamentada no escopo Nexora:** a integração de IA pode utilizar a abstração `AIProvider`, porque evitar dependência direta de um fornecedor é uma decisão aprovada; não é necessário implementar vários provedores no MVP.

## 6. SOLID, separação de responsabilidades, coesão e acoplamento

Aplique SOLID **com pragmatismo**, sem transformar cada classe em várias interfaces e adaptadores desnecessários.

- **Responsabilidade única:** controllers tratam HTTP e validação de entrada; serviços/casos de uso aplicam regras; repositórios persistem; integrações externas ficam isoladas da regra central.
- **Alta coesão:** código que representa a mesma regra de negócio permanece junto ao respectivo módulo.
- **Baixo acoplamento:** evite dependências circulares e acesso direto e indiscriminado a detalhes internos de outros módulos.
- **Inversão de dependência onde houver ganho real:** principalmente ao integrar provedores externos e ao isolar regras difíceis de testar.
- **Não duplique regras críticas:** elegibilidade acadêmica para candidatura, autorização por propriedade e transições de estado devem ter um ponto de decisão consistente.
- Prefira composição a herança quando ela simplificar o modelo.

### Organização do backend

Respeite o monólito modular por domínio (por exemplo: `authentication`, `users`, `students`, `universities`, `companies`, `jobs`, `applications`, `matching`, `administration` e `shared`). Dentro de cada módulo, crie apenas as camadas efetivamente necessárias. **Não converta o MVP em uma arquitetura distribuída ou Clean Architecture cerimonial.**

### Organização do frontend

Componentes Vue devem ter responsabilidade clara; lógica HTTP e regras de apresentação não devem ser copiadas entre telas. Tipos TypeScript devem representar os contratos reais da API. Não replique no frontend as regras de autorização como se fossem garantia de segurança: **o backend é a autoridade**.

## 7. Refatoração contínua, porém proporcional

- Ao concluir um comportamento, remova duplicações e inconsistências introduzidas pela mudança.
- Refatore somente o entorno necessário para manter a solução clara.
- Mantenha cada mudança pequena, revisável e de fácil reversão.
- Não proponha uma “reescrita completa” ou reorganização geral sem uma falha concreta que a justifique.
- Se uma refatoração elevar significativamente o risco de regressão perto do prazo, priorize primeiro a estabilidade e registre a melhoria como pendência.

## 8. Testes prioritários — proteger o coração do Nexora

A prioridade de testes não é percentual bruto de cobertura: é **reduzir o risco nos fluxos essenciais**.

| Prioridade | Áreas e cenários obrigatórios |
|---|---|
| Crítica | Cadastro/login, senha segura, JWT, conta suspensa e autorização por papel/propriedade |
| Crítica | Aprovação/rejeição/revogação de vínculos acadêmicos; apenas um vínculo aprovado ativo por estudante |
| Crítica | Impedir candidatura sem vínculo acadêmico elegível; impedir candidatura duplicada |
| Crítica | Suspender candidaturas em andamento após perda de validação, preservando histórico e documentos |
| Crítica | CRUD de vagas limitado à empresa proprietária |
| Crítica | Escolha exclusiva entre currículo Nexora (snapshot) e PDF; validação de PDF de até 5 MiB e download autorizado |
| Crítica | Score de matching determinístico, justificável, limites de pontuação e recomendações nos dois sentidos |
| Alta | Migrações Flyway, persistência JPA, contratos REST e integração com PostgreSQL |
| Alta | Fluxo ponta a ponta do MVP e deploy público com HTTPS |
| Proporcional | Componentes visuais, getters/setters e apresentação simples, usando o teste mais econômico que tenha valor |

Utilize testes unitários para regras isoladas e testes de integração onde comportamento depende de segurança, banco, transações ou infraestrutura. Teste situações de falha e limites, não somente o caminho feliz.

## 9. Regras de domínio que a IA não pode violar

1. Existem quatro perfis: `STUDENT`, `COMPANY`, `UNIVERSITY` e `ADMIN`.
2. Universidades fazem autocadastro, mas só podem operar como instituição após aprovação do administrador.
3. Um estudante pode ter **no máximo um vínculo acadêmico aprovado e ativo**. A universidade decide aceitar ou rejeitar a solicitação.
4. Um estudante só pode se candidatar se estiver validado por vínculo acadêmico elegível com universidade ativa.
5. A perda de validação **suspende candidaturas em andamento** e preserva o histórico; candidaturas concluídas não são alteradas automaticamente.
6. Empresas gerenciam exclusivamente suas vagas e candidaturas autorizadas; universidades gerenciam vínculos e avaliações, **não editam livremente o currículo do estudante**.
7. O administrador gerencia os quatro perfis e suas permissões no escopo aprovado; não exponha segredos e não crie um sistema de permissões arbitrárias sem requisito.
8. O estudante cria um **currículo-base estruturado** no Nexora. **Somente esse currículo-base alimenta o matching**.
9. Em cada candidatura, o estudante escolhe **exatamente uma opção**: snapshot do currículo da plataforma **ou** PDF externo.
10. O PDF deve ser válido, protegido contra acesso indevido e limitado a **5 MiB (5.242.880 bytes)**; não confiar apenas na extensão ou no `Content-Type` informado pelo cliente.
11. Alterações posteriores no currículo-base não alteram a versão enviada em candidaturas anteriores.
12. O score de compatibilidade deve ser calculado por critérios definidos e testáveis. **Não aceite uma nota arbitrária da LLM como score oficial**.
13. A análise semântica por IA é uma camada adicional quando implementada; se indisponível, não simule uso real de IA nem apresente resultados fictícios como reais.

## 10. Segurança, dados e implantação são requisitos, não enfeites

- Valide entradas, ownership e permissões **no servidor**.
- Use hash de senha apropriado, tokens com expiração e configuração segura de segredos.
- Nunca registre senhas, tokens, currículos integrais ou dados pessoais sensíveis em logs.
- Restrinja acesso aos documentos enviados; use armazenamento persistente privado e downloads autorizados.
- Preserve integridade transacional nas operações de vínculo, candidatura e suspensão.
- Use Flyway para mudanças no esquema: migrations versionadas, previsíveis e revisáveis; não apague dados existentes.
- Configure CORS para origens conhecidas, HTTPS na publicação e segredos por variáveis de ambiente.
- Considere privacidade e minimização de dados nos endpoints públicos e nas integrações com IA.
- Não faça exposição pública de serviços administrativos, Swagger ou banco sem necessidade e controle adequado.

## 11. Fluxo obrigatório de execução por história de usuário

Para **cada US**, o agente deve seguir esta sequência:

1. **Entender:** ler US, critérios de aceite, regras relacionadas e implementação existente.
2. **Delimitar:** registrar brevemente o que será alterado e o que fica fora de escopo.
3. **Planejar o mínimo:** identificar módulos, endpoint(s), DTOs, entidade(s), migration(s) e testes estritamente necessários.
4. **Red:** escrever e executar testes para regras críticas, confirmando a falha esperada.
5. **Green:** implementar o mínimo para os testes passarem.
6. **Refactor:** melhorar legibilidade e eliminar duplicações relevantes sem adicionar arquitetura especulativa.
7. **Verificar:** executar testes relevantes e, quando possível, build, lint e validações de integração **dentro do Docker**.
8. **Revisar:** confirmar segurança, autorização, estados, contratos, migration e ausência de segredos ou alterações não relacionadas.
9. **Documentar:** atualizar Swagger/OpenAPI e documentação somente quando houver mudanças de contrato ou comportamento relevante.
10. **Reportar:** listar arquivos alterados, US atendida, comandos executados, testes aprovados/falhos, pendências e limitações. **Nunca declarar sucesso sem validar.**

Caso não consiga executar um comando, reporte precisamente o impedimento. **Não invente resultados de testes, deploys, ferramentas, consultas ou benchmarks.**

## 12. Critérios de conclusão (*Definition of Done*)

Uma história de usuário **só pode ser marcada como concluída** quando:

- [ ] O comportamento atende integralmente aos critérios de aceite P0 da US.
- [ ] Os casos críticos foram cobertos por testes significativos, preferencialmente escritos antes da implementação.
- [ ] Os testes relevantes foram executados e passaram; limitações foram explicitadas.
- [ ] O código respeita KISS, YAGNI, separação de responsabilidades, coesão e baixo acoplamento.
- [ ] Não houve introdução de dependências, serviços ou abstrações sem justificativa real.
- [ ] Autorização, validações, tratamento de erros e integridade de dados foram revisados.
- [ ] Migrations e contratos REST afetados estão consistentes.
- [ ] A aplicação continua compilando e executando no ambiente Docker quando a tarefa exigir integração.
- [ ] Nenhum dado sensível, credencial ou artefato local foi versionado.
- [ ] O progresso foi registrado no backlog de US e as pendências foram relatadas.

**Sem validação, classifique a US como “implementada, não verificada”, nunca como “concluída”.**

## 13. Política para mudanças de escopo e decisões arquiteturais

Antes de adicionar uma dependência, módulo, abstração ou componente de infraestrutura, responda:

1. **Qual requisito aprovado ou problema concreto exige isso agora?**
2. **Existe alternativa mais simples que preserve correção, segurança e testes?**
3. **Qual é o custo adicional de manutenção e deploy?**
4. **Como verificaremos que a decisão resolveu o problema?**

Se não houver resposta objetiva, **não adicione**.

Se identificar uma oportunidade futura, registre-a como sugestão fora do MVP e continue com a tarefa atual. Nunca transforme sugestões em código sem aprovação.

## 14. Diretriz final para qualquer IA de desenvolvimento

> **Implemente apenas o necessário, teste primeiro as regras críticas, mantenha os módulos coesos e fracamente acoplados, refatore em pequenos passos e só declare uma tarefa concluída após verificá-la.**
>
> **O objetivo não é gerar mais código; é entregar o Nexora funcionando, com o menor custo cognitivo e arquitetural possível.**

Estas diretrizes se aplicam a **todas as histórias de usuário e tarefas técnicas**, inclusive código criado automaticamente por agentes de IA. Prazo curto justifica **priorização rigorosa**, nunca a eliminação de controles essenciais de segurança, integridade ou honestidade sobre testes.
