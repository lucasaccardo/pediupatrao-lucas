# PediuPatrão — Sistema de Pedidos com Dashboard Administrativo

**Aluno:** Lucas Mateus Sureira
**Tecnologias:** Java 21 · Spring Boot 3.4 · Spring Security 6 · Spring Data MongoDB · Thymeleaf · Chart.js

Sistema de pedidos de uma pizzaria, evoluído em etapas:

| Versão | Atividade | O que entrou |
|---|---|---|
| `v1.0` | Prática M1 – Parte 2 | Três perfis de acesso, pedidos com itens e total calculado no servidor, máquina de estados, desconto e cancelamento do gerente, auditoria no MongoDB |
| `v1.1.0` | **Dashboard Administrativo (M2)** | API `/api/dashboard` com 7 endpoints e tela `/dashboard` com indicadores, gráficos, situação dos pedidos, ranking de produtos, filtros e atualização automática |
| `v1.1.1` | Correção (hotfix) | Filtro recusado pelo servidor não altera mais o painel; a atualização automática usa sempre o último filtro aceito |

## Dashboard Administrativo (M2)

| Entregável | Onde está |
|---|---|
| Requisitos funcionais e não funcionais, regras de cálculo, arquitetura e decisões de projeto | [docs/DOCUMENTACAO-DASHBOARD.md](docs/DOCUMENTACAO-DASHBOARD.md) |
| Documentação dos endpoints (parâmetros, exemplos, erros) | [docs/API-DASHBOARD.md](docs/API-DASHBOARD.md) |
| Evidências de testes | [docs/EVIDENCIAS-TESTES.md](docs/EVIDENCIAS-TESTES.md) |
| Dashboard funcional | `http://localhost:8080/dashboard` (perfis ADMIN e GERENTE) |
| Branch da implementação (gitflow) | [`feature/dashboard-api`](../../tree/feature/dashboard-api) → `develop` → `main` (tag `v1.1.0`) |

### Rodar o dashboard com dados de demonstração
```bat
:: com um MongoDB local ligado (mongod)
set MONGODB_URI=mongodb://localhost:27017
set MONGODB_DATABASE=pediupatrao_dashboard
set DEMO_PEDIDOS=true
mvnw spring-boot:run
```
Entre com `gerente / gerente123` e clique em **Dashboard** no menu. Com `DEMO_PEDIDOS=true` e o banco sem pedidos, o sistema cria 60 dias de pedidos de exemplo para os gráficos terem conteúdo.

### Gitflow usado
- `main`: versões entregues (tags `v1.1.0` e `v1.1.1`).
- `develop`: integração.
- `feature/dashboard-api`: implementação da API e da tela do dashboard, integrada em `develop` com `merge --no-ff`.
- `hotfix/1.1.1`: correção feita a partir da `main` depois da entrega, integrada na `main` (tag `v1.1.1`) e na `develop`.

---

# Parte 2 — Perfis, pedidos e auditoria

## 1. Como executar

### Pré-requisitos
- **JDK 21** (o Java 8 não compila o projeto). Confira com `java -version`.
- Acesso à internet (MongoDB Atlas + download das dependências na primeira execução).

### Passos
```bash
# na pasta do projeto (onde está o pom.xml)
mvnw spring-boot:run          # Windows
./mvnw spring-boot:run        # Linux/Mac
```
Acesse **http://localhost:8080**.

### Banco de dados
O projeto usa o cluster MongoDB Atlas da disciplina, mas em um **banco próprio** (`pediupatrao_lucas`) para não misturar usuários, pedidos e auditoria com os de outros alunos.
Pode ser alterado por variável de ambiente, sem mexer no código:

| Variável | Padrão |
|---|---|
| `MONGODB_URI` | URI do cluster da disciplina |
| `MONGODB_DATABASE` | `pediupatrao_lucas` |

#### Usando um MongoDB local (quando o IP não está liberado no Atlas)
Se aparecer `SSLException: Received fatal alert: internal_error`, o Atlas recusou o IP da sua rede. Rode um MongoDB local:
```bat
:: janela 1 - sobe o MongoDB (deixe aberta)
mongod --dbpath %USERPROFILE%\mongo-data

:: janela 2 - sobe a aplicação apontando para o banco local
set MONGODB_URI=mongodb://localhost:27017
mvnw spring-boot:run
```

### Usuários criados automaticamente (DataInitializer)
| Usuário | Senha | Perfil |
|---|---|---|
| admin | teste123 | ADMIN |
| gerente | gerente123 | GERENTE |
| atendente | atendente123 | ATENDENTE |

Também são criados 4 produtos de exemplo (pizzas e refrigerante) quando a coleção `produtos` está vazia.

### Testes automatizados
```bash
mvnw test
```
| Classe | O que prova |
|---|---|
| `StatusPedidoTest` | transições válidas/inválidas (ex.: não finaliza pedido em preparação; cancelado não muda mais) |
| `PedidoServiceTest` | total calculado no servidor, desconto ≤ 20%, desconto/cancelamento só antes da saída, justificativa obrigatória, registro de saída |
| `AuditoriaServiceTest` | senha nunca gravada, telefone mascarado, só campos alterados são registrados |
| `SegurancaApiTest` | chamadas diretas à API: 401 sem login, 403 para perfil sem permissão, 403 sem token CSRF |

---

## 2. Requisitos implementados

### RF01 — Controle de usuários e permissões
| Perfil | Pode |
|---|---|
| **ADMIN** | administrar usuários e perfis, consultar pedidos, ver auditoria |
| **GERENTE** | gerenciar pedidos (status), **aplicar descontos**, **cancelar pedidos**, consultar clientes, ver auditoria |
| **ATENDENTE** | cadastrar/atualizar clientes, registrar pedidos e acompanhar o andamento |

- `Perfil` (enum) define os 3 perfis; valores diferentes são rejeitados no backend.
- `UsuarioDetailsService` agora carrega o **perfil real** do banco (antes todos recebiam `USER`). Conta sem perfil válido fica bloqueada.
- **Duas camadas de autorização no backend**: regras por URL/método HTTP no `SecurityConfig` **e** `@PreAuthorize` em cada método dos services (`@EnableMethodSecurity`). Esconder botões é só conforto visual.
- **Somente o GERENTE** aplica desconto e cancela — inclusive o ADMIN recebe 403.
- `/api/usuarios` era **público** (qualquer pessoa criava um ADMIN). Agora é exclusivo do ADMIN.
- Proteção contra autoelevação: só ADMIN gerencia perfis; o ADMIN não pode alterar o próprio perfil nem se excluir; o sistema nunca fica sem ADMIN; o `id` vindo do formulário é ignorado (evita *mass assignment*).
- A senha usa BCrypt e **nunca** é devolvida no JSON (`@JsonProperty(WRITE_ONLY)`).
- **CSRF habilitado** (antes estava desativado): formulários Thymeleaf enviam o token automaticamente e as chamadas `fetch()` enviam o cabeçalho `X-CSRF-TOKEN` (meta tags no `layout.html`). Logout passou a ser via POST.
- APIs respondem **401/403 em JSON**; telas mostram a página "Acesso negado".

### RF02 — Evolução do cadastro de pedidos
- `Pedido` agora tem: itens (`ItemPedido`: produto, quantidade, preço unitário, subtotal), subtotal, percentual e valor de desconto, **valor total**, modalidade (entrega/retirada), responsável e data/hora de entrada.
- O preço vem do **cadastro de produtos no banco**; a requisição só informa `produtoId` e `quantidade` (`NovoPedidoRequest`). Campos como `valorTotal` enviados pelo cliente são ignorados.
- `Pedido.recalcularValores()` faz toda a conta com `BigDecimal` no servidor.
- Validações: cliente existente, pelo menos 1 item, quantidade 1–50, produto existente e ativo.

### RF03 — Entrada, saída e transições
```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) ─┐
                                  └→ RETIRADO (balcão) ───────────┴→ FINALIZADO
CANCELADO: só GERENTE, só antes da saída, com justificativa
```
- Máquina de estados no enum `StatusPedido` + validação no `PedidoService`.
- Bloqueia transições inválidas (ex.: finalizar pedido em preparação, pular etapas) e **qualquer alteração após CANCELADO/FINALIZADO**.
- Entrada (RECEBIDO): data/hora + responsável. Saída (SAIU_PARA_ENTREGA/RETIRADO): data/hora + responsável **obrigatórios**. Finalização também registrada.
- Linha do tempo (`HistoricoStatus`) guardada dentro do pedido.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)
- **Desconto** percentual, > 0% e **≤ 20%**, só antes da saída. O novo percentual substitui o anterior (não acumula), garantindo o teto.
- **Cancelamento** exige justificativa (mín. 5 caracteres), só antes da saída, **preserva itens e valores** e bloqueia alterações posteriores.
- Ambos geram registro de auditoria com usuário, data/hora e valores anteriores/novos.

### RF05 — Auditoria persistente
- Coleção própria `auditoria` no MongoDB: entidade `RegistroAuditoria`, repositório `RegistroAuditoriaRepository` e serviço `AuditoriaService`.
- Eventos auditados: inclusão de pedido, mudança de status, desconto, cancelamento, inclusão/alteração/exclusão de cliente (**endereço e telefone campo a campo**) e operações de usuários.
- Cada registro guarda: **usuário e perfil** (vindos da sessão autenticada), **data/hora**, **operação**, **entidade + id**, **campos alterados com valor anterior e novo** e **justificativa**.
- **Integridade**: o repositório estende `Repository` (não `MongoRepository`) e expõe só `save` e consultas — não existe método de update/delete. A entidade não tem setters. Qualquer `POST/PUT/DELETE` em `/auditoria/**` ou `/api/auditoria/**` é negado (`denyAll`).
- **Consulta** exclusiva de ADMIN e GERENTE: tela `/auditoria` (com filtros) e `GET /api/auditoria`.
- **Proteção de dados**: senhas/tokens viram `[PROTEGIDO]`; telefone é gravado mascarado (`(11) *****-4321`).

---

## 3. Principais alterações

| Arquivo | Alteração |
|---|---|
| `config/SecurityConfig` | regras por perfil, CSRF ligado, `@EnableMethodSecurity`, 401/403 em JSON para `/api/**` |
| `config/DataInitializer` | cria admin/gerente/atendente e produtos de exemplo; corrige admin antigo sem perfil |
| `config/MongoConversoesConfig` | lê status antigos (`EM_PREPARO`, `ENTREGUE`) como o novo enum |
| `entity/Perfil`, `StatusPedido`, `ModalidadeEntrega` | enums novos |
| `entity/Pedido`, `ItemPedido`, `HistoricoStatus` | modelo de pedido evoluído |
| `entity/RegistroAuditoria`, `AlteracaoCampo`, `TipoOperacaoAuditoria` | auditoria |
| `repository/RegistroAuditoriaRepository` | somente inclusão e consulta |
| `service/PedidoService` | regras RF02/RF03/RF04 + auditoria |
| `service/ClienteService` | validação + auditoria campo a campo |
| `service/UsuarioService` | validação de perfil, anti-autoelevação, auditoria sem senha |
| `service/UsuarioDetailsService` | usa o perfil real do usuário |
| `service/AuditoriaService` | registro, mascaramento e consulta restrita |
| `controller/PedidoWebController` | telas de pedidos (lista, novo, detalhe, status, desconto, cancelamento) |
| `controller/PedidoController` | API REST de pedidos |
| `controller/AuditoriaController`, `AuditoriaApiController` | consulta da auditoria |
| `controller/SegurancaController` | página "Teste de Permissões" para a demonstração |
| `exception/*` | `RegraNegocioException` (422), `RecursoNaoEncontradoException` (404) e handler JSON |
| `templates/*` | telas novas de pedidos e auditoria, menu por perfil, CSRF no `fetch`, proteção XSS na lista de clientes |

---

## 4. Endpoints da API

| Método | URL | Perfis |
|---|---|---|
| GET | `/api/pedidos`, `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE |
| POST | `/api/pedidos` `{clienteId, modalidade, itens:[{produtoId, quantidade}]}` | GERENTE, ATENDENTE |
| PUT | `/api/pedidos/{id}/status?status=EM_PREPARACAO` | GERENTE, ATENDENTE |
| POST | `/api/pedidos/{id}/desconto` `{percentual, motivo}` | **GERENTE** |
| POST | `/api/pedidos/{id}/cancelar` `{justificativa}` | **GERENTE** |
| GET | `/api/clientes` | GERENTE, ATENDENTE |
| POST/PUT/DELETE | `/api/clientes/**` | ATENDENTE |
| * | `/api/usuarios/**` | ADMIN |
| GET | `/api/auditoria` | ADMIN, GERENTE |
| POST/PUT/DELETE | `/api/auditoria/**` | ninguém |

Respostas de erro: `401` (sem login), `403` (perfil sem permissão ou sem token CSRF), `404` (não encontrado), `422` (regra de negócio violada).

---

## 5. Demonstração em vídeo
1. Login como **atendente** → cadastrar/alterar cliente (endereço/telefone) → registrar pedido.
2. Avançar status (RECEBIDO → EM PREPARAÇÃO → PRONTO) e tentar uma transição inválida.
3. Em **Teste de Permissões**, chamar a API de cancelamento/desconto como atendente → **403**.
4. Login como **gerente** → desconto de 25% (bloqueado) e de 10% (aceito) → cancelar outro pedido com justificativa → saída e finalização de um pedido.
5. Login como **admin** → tentar desconto pela API (**403**, mesmo sendo admin) → gerenciar usuários → consultar a **auditoria** com os registros da demonstração.
