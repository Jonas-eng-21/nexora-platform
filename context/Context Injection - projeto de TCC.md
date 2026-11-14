# CONTEXT INJECTION — PROJETO DE TCC

Você está recebendo este contexto para compreender um projeto de Trabalho de Conclusão de Curso (TCC) que está sendo desenvolvido. Considere todas as informações abaixo como contexto-base para as próximas interações desta conversa.

Não trate as decisões como absolutamente imutáveis: algumas tecnologias e aspectos metodológicos ainda podem ser refinados conforme o desenvolvimento, a pesquisa bibliográfica e a orientação do TCC. Entretanto, utilize as decisões descritas aqui como a direção tecnológica atual do projeto.

---

# 1. CONTEXTO E PROBLEMA

O projeto tem como foco Garanhuns, Pernambuco, Brasil, e municípios da região.

A problemática identificada é a dificuldade de aproximação entre estudantes de cursos relacionados à tecnologia e empresas de tecnologia da região.

A proposta parte da percepção de que existe uma lacuna local na conexão direta entre:

- estudantes de tecnologia, especialmente alunos em formação ou concluintes;
- empresas de tecnologia que precisam encontrar novos talentos.

Um problema particularmente relevante é a entrada de estudantes no mercado de trabalho.

Muitos desses estudantes ainda possuem pouca ou nenhuma experiência profissional formal. Apesar disso, podem possuir:

- conhecimentos técnicos;
- projetos acadêmicos;
- projetos pessoais;
- cursos;
- certificações;
- competências;
- experiências extracurriculares;
- interesses profissionais;
- conhecimentos em determinadas tecnologias.

Plataformas tradicionais de emprego nem sempre são orientadas especificamente para esse contexto local e para a valorização desse conjunto de informações.

Do outro lado, empresas podem ter dificuldade para identificar estudantes com determinado conjunto de competências, especialmente quando esses candidatos ainda não possuem um histórico profissional extenso.

O projeto pretende investigar e desenvolver uma solução tecnológica para aproximar esses dois públicos.

IMPORTANTE:
A afirmação de que "não existe nenhuma plataforma desse tipo em Garanhuns" ainda NÃO deve ser tratada como fato científico estabelecido. Isso faz parte da hipótese/problema percebido e precisa ser validado por meio do levantamento do estado da arte e da investigação de soluções existentes.

---

# 2. PROPOSTA DO PROJETO

A proposta é desenvolver uma plataforma digital especializada na conexão entre estudantes de tecnologia e empresas de tecnologia de Garanhuns e região.

A plataforma terá três perfis principais:

1. Administrador;
2. Estudante;
3. Empresa.

O sistema não deve ser entendido apenas como uma plataforma tradicional de vagas.

O principal diferencial proposto é a utilização de mecanismos de análise e inteligência artificial para realizar um matching entre:

- o que o estudante oferece;
- o que o estudante procura para sua carreira;
- o que a empresa oferece;
- o que a empresa procura em seus candidatos.

O objetivo é produzir recomendações nos dois sentidos:

- estudantes compatíveis para empresas;
- empresas e oportunidades compatíveis para estudantes.

---

# 3. PERFIL DO ESTUDANTE

O estudante poderá construir um perfil profissional contendo, entre outras informações:

- nome;
- formação acadêmica;
- curso;
- instituição de ensino;
- período;
- competências técnicas;
- competências comportamentais;
- tecnologias conhecidas;
- experiências profissionais;
- projetos acadêmicos;
- projetos pessoais;
- certificações;
- cursos;
- currículo;
- GitHub;
- LinkedIn;
- portfólio;
- interesses profissionais;
- áreas de interesse;
- objetivos de carreira;
- preferências relacionadas às oportunidades.

A plataforma deve valorizar informações além da experiência profissional formal, pois o público-alvo inclui estudantes que ainda estão ingressando no mercado.

---

# 4. PERFIL DA EMPRESA

As empresas poderão:

- criar seu perfil;
- informar sua área de atuação;
- cadastrar vagas;
- definir requisitos;
- definir competências desejadas;
- informar tecnologias utilizadas;
- definir características da oportunidade;
- pesquisar estudantes;
- visualizar perfis;
- receber recomendações de estudantes compatíveis.

---

# 5. VAGAS

Uma vaga poderá conter informações como:

- título;
- descrição;
- empresa;
- localização;
- modelo de trabalho;
- nível de experiência;
- tecnologias;
- competências obrigatórias;
- competências desejáveis;
- formação;
- características da oportunidade;
- demais requisitos.

Esses dados serão utilizados pelo mecanismo de matching.

---

# 6. MATCHING E RECOMENDAÇÃO

O matching é uma das principais partes do projeto.

A ideia atual NÃO é simplesmente enviar o perfil inteiro do aluno para uma LLM e perguntar:

"Qual candidato é melhor?"

O sistema deverá buscar uma abordagem mais controlada e explicável.

A ideia é combinar:

1. critérios estruturados;
2. similaridade semântica;
3. inteligência artificial generativa.

Um possível fluxo é:

Dados estruturados
→ filtros e regras
→ comparação de competências e requisitos
→ embeddings/similaridade semântica
→ cálculo de compatibilidade
→ IA generativa para interpretação/explicação.

O score de compatibilidade deve, preferencialmente, ser produzido pelo próprio sistema a partir de critérios definidos, e não ser simplesmente uma nota arbitrária fornecida por uma LLM.

Exemplo conceitual:

score =
    compatibilidade de competências
    + similaridade semântica
    + formação
    + experiência
    + interesses
    + preferências

Os pesos ainda podem ser definidos e validados durante o projeto.

---

# 7. INTELIGÊNCIA ARTIFICIAL

Neste momento, os principais candidatos considerados para integração são:

- Google Gemini;
- OpenAI.

A escolha definitiva do provedor ainda não está fechada.

A arquitetura deve evitar acoplamento direto do domínio da aplicação a um único fornecedor de IA.

A intenção é criar uma abstração conceitual semelhante a:

AIProvider

com implementações que possam futuramente representar diferentes provedores.

Conceitualmente:

AIProvider
├── GeminiProvider
└── OpenAIProvider

Isso permitirá trocar ou comparar provedores sem alterar a lógica principal da aplicação.

---

# 8. EMBEDDINGS E SIMILARIDADE SEMÂNTICA

Uma possibilidade considerada para o matching é utilizar embeddings.

Conceitualmente:

Perfil do estudante
→ embedding

Descrição/perfil da vaga
→ embedding

Depois:

embedding do estudante
↕
similaridade
↕
embedding da vaga

Isso permitirá identificar compatibilidade semântica mesmo quando o texto utilizado pelo estudante e pela empresa não for exatamente igual.

Exemplo conceitual:

Estudante:
"Desenvolvimento de APIs utilizando Spring Boot."

Vaga:
"Experiência com desenvolvimento backend Java e serviços web."

Mesmo que as palavras não sejam idênticas, os conteúdos podem apresentar proximidade semântica.

A utilização de embeddings ainda é uma decisão técnica em avaliação.

---

# 9. ARQUITETURA

A arquitetura inicial escolhida é um MONÓLITO MODULAR.

Não será adotada arquitetura de microsserviços no MVP.

A ideia é possuir uma única aplicação backend, porém organizada internamente em módulos bem definidos.

Possíveis módulos:

- authentication;
- users;
- students;
- companies;
- jobs;
- resumes;
- skills;
- matching;
- AI/recommendation.

Conceitualmente:

Frontend
↓
API REST
↓
Spring Boot
↓
módulos da aplicação
↓
PostgreSQL

E:

Spring Boot
↓
AIProvider
↓
Gemini / OpenAI

A arquitetura deve permitir evolução futura.

Caso determinados módulos apresentem necessidade real de escala ou processamento independente, eles poderão posteriormente ser extraídos para serviços separados.

A intenção é seguir uma evolução arquitetural gradual, em vez de introduzir complexidade de infraestrutura antes que ela seja necessária.

---

# 10. TECNOLOGIAS ESCOLHIDAS

## Backend

Java 21 + Spring Boot.

A escolha de Java 21 foi feita considerando:

- maturidade;
- estabilidade;
- suporte de longo prazo da versão;
- ecossistema Spring;
- organização arquitetural;
- facilidade de manutenção;
- possibilidade de evolução do sistema.

PHP também foi considerado durante a análise tecnológica, especialmente versões atuais da linguagem, mas a direção atual do projeto é Java 21 + Spring Boot.

Não tratar PHP como uma tecnologia incapaz de atender ao problema. A decisão por Java é uma decisão arquitetural e de projeto, não uma afirmação de que PHP não seria adequado.

---

## API

REST + JSON.

O frontend e futuros clientes, como uma possível aplicação mobile, deverão consumir a API através de HTTP.

---

## Banco de dados

PostgreSQL.

A versão atualmente considerada é PostgreSQL 18.

O banco relacional foi escolhido porque o domínio possui diversas relações estruturadas entre:

- usuários;
- estudantes;
- empresas;
- vagas;
- competências;
- formações;
- experiências;
- projetos;
- candidaturas;
- recomendações.

Existe também a possibilidade futura de utilizar pgvector para armazenamento e busca de embeddings dentro do próprio PostgreSQL, evitando a necessidade inicial de um banco vetorial separado.

---

## Frontend

Vue 3 + TypeScript + Vite.

O frontend será desenvolvido separadamente do backend.

A arquitetura deverá permitir que futuramente seja desenvolvido um aplicativo mobile consumindo a mesma API.

Tecnologias complementares consideradas:

- Vue Router;
- Pinia;
- cliente HTTP;
- TypeScript.

---

## Segurança

Spring Security + JWT.

Os principais papéis previstos são:

- ADMIN;
- STUDENT;
- COMPANY.

O backend será responsável por autenticação e autorização.

---

## Persistência

Spring Data JPA + Hibernate.

---

## Migrações

Flyway.

---

## Documentação da API

OpenAPI / Swagger.

A API deverá ser documentada desde o desenvolvimento do MVP.

---

## Containers

Docker + Docker Compose.

A ideia é utilizar containers para facilitar:

- ambiente de desenvolvimento;
- reprodução do ambiente;
- configuração do banco;
- execução da aplicação;
- futura implantação.

---

# 11. TECNOLOGIAS QUE NÃO FAZEM PARTE DO MVP INICIAL

Não adicionar automaticamente ao projeto apenas por serem tecnologias associadas à escalabilidade.

Inicialmente NÃO são requisitos:

- Redis;
- RabbitMQ;
- Kubernetes;
- Load Balancer;
- múltiplas instâncias da API;
- replicação de banco;
- microsserviços;
- API Gateway;
- arquitetura distribuída complexa.

Essas tecnologias podem fazer parte da evolução futura.

A lógica arquitetural é:

"começar simples, modular e sustentável, introduzindo complexidade conforme surgirem necessidades reais."

---

# 12. EVOLUÇÃO ARQUITETURAL FUTURA

A arquitetura foi pensada para evoluir.

Possível evolução:

FASE 1:
Vue
↓
Spring Boot
↓
PostgreSQL

FASE 2:
Load Balancer
↓
múltiplas instâncias da API
↓
PostgreSQL

FASE 3:
API
↓
Redis + PostgreSQL

FASE 4:
API
↓
RabbitMQ
↓
workers para processamento assíncrono, IA e matching

FASE 5:
Extração de módulos específicos para serviços independentes caso exista necessidade real.

A arquitetura inicial, portanto, não pretende resolver antecipadamente problemas de escala que ainda não existem.

---

# 13. ESTADO DA ARTE

O levantamento do estado da arte está EM ANDAMENTO.

Ainda NÃO existem conclusões definitivas.

A pesquisa deverá investigar pelo menos cinco eixos:

## 13.1 Plataformas tradicionais de emprego

Investigar como plataformas de emprego realizam:

- cadastro;
- divulgação de vagas;
- busca;
- candidatura;
- recomendação.

## 13.2 Plataformas de recrutamento tecnológico

Investigar soluções voltadas especificamente para profissionais e empresas de tecnologia.

## 13.3 Sistemas de recomendação

Investigar trabalhos acadêmicos relacionados à recomendação de:

- vagas para candidatos;
- candidatos para vagas;
- oportunidades profissionais.

## 13.4 Inteligência artificial aplicada ao recrutamento

Investigar trabalhos que utilizem:

- Machine Learning;
- NLP;
- embeddings;
- sistemas de recomendação;
- LLMs;
- análise semântica.

## 13.5 Contexto regional

Investigar trabalhos, plataformas, iniciativas e pesquisas relacionados à:

- empregabilidade em Garanhuns;
- Pernambuco;
- interiorização da tecnologia;
- conexão entre instituições de ensino e empresas;
- empregabilidade de estudantes de tecnologia.

IMPORTANTE:

Não inventar artigos, autores, plataformas, estatísticas ou resultados para preencher essa seção.

Qualquer afirmação sobre o estado da arte deve ser baseada em pesquisa bibliográfica ou fonte verificável.

---

# 14. POSSÍVEIS TÍTULOS DO TCC

Os títulos abaixo são preliminares e ainda poderão ser modificados após o estado da arte e a definição metodológica.

Possibilidades:

1. "Desenvolvimento de uma Plataforma Inteligente para Conexão entre Estudantes e Empresas de Tecnologia de Garanhuns e Região"

2. "Uma Plataforma de Matching Inteligente entre Estudantes de Tecnologia e Oportunidades Profissionais"

3. "Desenvolvimento de uma Plataforma de Recomendação para Aproximação entre Estudantes e Empresas de Tecnologia"

4. "Aplicação de Inteligência Artificial na Conexão entre Estudantes de Tecnologia e Oportunidades Profissionais"

5. "Plataforma Inteligente para Recomendação de Oportunidades Profissionais a Estudantes de Tecnologia"

6. "Sistema de Matching baseado em Inteligência Artificial para Conexão entre Estudantes e Empresas de Tecnologia"

Não escolher definitivamente um título sem considerar o estado da arte, o recorte metodológico e o objetivo geral do TCC.

---

# 15. OBJETIVO CONCEITUAL DO PROJETO

O projeto busca desenvolver e avaliar uma plataforma digital capaz de aproximar estudantes de tecnologia e empresas da região de Garanhuns por meio da organização de informações profissionais e da utilização de mecanismos de recomendação e inteligência artificial para identificar compatibilidades entre perfis de estudantes e oportunidades profissionais.

O foco não deve ser simplesmente "criar um site de vagas".

O foco é:

DADOS
+
RECOMENDAÇÃO
+
MATCHING
+
INTELIGÊNCIA ARTIFICIAL
+
CONTEXTO REGIONAL

---

# 16. PREMISSAS PARA AS PRÓXIMAS INTERAÇÕES

Ao discutir este projeto:

1. Não trate a plataforma como um simples portal de vagas.
2. Não assuma que a inexistência de concorrentes locais já foi comprovada.
3. Não invente resultados para o estado da arte.
4. Diferencie claramente fatos, hipóteses, decisões técnicas e possibilidades futuras.
5. Preserve a arquitetura de monólito modular como direção inicial.
6. Considere Java 21 + Spring Boot como backend atualmente escolhido.
7. Considere PostgreSQL como banco atualmente escolhido.
8. Considere Vue 3 + TypeScript + Vite como frontend atualmente escolhido.
9. Considere Docker + Docker Compose como infraestrutura inicial.
10. Considere Gemini e OpenAI como candidatos atuais para a integração de IA.
11. Mantenha a possibilidade de abstração entre diferentes provedores de IA.
12. Considere embeddings como possibilidade para o matching semântico.
13. Não introduza microsserviços, Redis, RabbitMQ ou infraestrutura distribuída no MVP sem justificar uma necessidade concreta.
14. Priorize uma solução que possa ser implementada e validada dentro de um MVP de aproximadamente quatro semanas.
15. As decisões tecnológicas devem ser justificadas por critérios técnicos, de manutenção, evolução, complexidade e adequação ao problema, e não por preferência pessoal.

Use todo este contexto como base para as próximas discussões relacionadas ao TCC.