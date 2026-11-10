# API do Dashboard Administrativo

Base: `http://localhost:8080/api/dashboard` · Formato: JSON (UTF-8) · Método: somente `GET`

## Visão geral

| Endpoint | Requisito | Para que serve |
|---|---|---|
| [`GET /indicadores`](#1-get-apidashboardindicadores) | RF01 | Faturamento, pedidos, ticket médio, cancelamentos, descontos, tempo médio e variação vs. período anterior |
| [`GET /vendas`](#2-get-apidashboardvendas) | RF02 | Série de vendas por dia, semana ou mês |
| [`GET /vendas/por-hora`](#3-get-apidashboardvendaspor-hora) | RF02 | Pedidos e faturamento em cada hora do dia |
| [`GET /pedidos/status`](#4-get-apidashboardpedidosstatus) | RF03 | Quantidade e percentual de pedidos por status |
| [`GET /pedidos/recentes`](#5-get-apidashboardpedidosrecentes) | RF03 | Últimos pedidos registrados |
| [`GET /produtos/mais-vendidos`](#6-get-apidashboardprodutosmais-vendidos) | RF04 | Ranking de produtos |
| [`GET /filtros`](#7-get-apidashboardfiltros) | RF05 | Valores aceitos pelos filtros |

## Autenticação e permissão

A API usa a **sessão do sistema** (login em `/login`). Perfis permitidos: **ADMIN** e **GERENTE**.

| Situação | Resposta |
|---|---|
| Sem login | `401 Unauthorized` |
| Logado como ATENDENTE | `403 Forbidden` |
| `POST`, `PUT` ou `DELETE` em qualquer `/api/dashboard/**` | `403 Forbidden` (a API é somente leitura) |

Exemplo com `curl` (Git Bash ou Linux), fazendo login e guardando o cookie da sessão:

```bash
# 1) pega a página de login para obter o token CSRF e o cookie
TOKEN=$(curl -s -c cookies.txt http://localhost:8080/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"//')
# 2) faz o login como gerente
curl -s -b cookies.txt -c cookies.txt -d "username=gerente&password=gerente123&_csrf=$TOKEN" http://localhost:8080/login -o /dev/null
# 3) chama a API com o cookie
curl -s -b cookies.txt "http://localhost:8080/api/dashboard/indicadores?inicio=2026-11-01&fim=2026-11-10"
```

## Filtros comuns (RF05)

Todos os endpoints de dados (1 a 6) aceitam:

| Parâmetro | Tipo | Obrigatório | Padrão | Regras |
|---|---|---|---|---|
| `inicio` | data `AAAA-MM-DD` | não | `fim` − 29 dias | Não pode ser maior que `fim`. |
| `fim` | data `AAAA-MM-DD` | não | hoje | Período máximo: 366 dias. |
| `status` | texto | não | todos | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` (maiúsculas ou minúsculas). |
| `modalidade` | texto | não | todas | `ENTREGA` ou `RETIRADA`. |

O período considera a **data de entrada** do pedido: `inicio` 00:00 até `fim` 23:59:59.

## Envelope de resposta

Todas as respostas de dados (1 a 6) seguem o mesmo formato:

```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.481",
  "dados": { }
}
```

| Campo | Descrição |
|---|---|
| `filtros` | Filtros **efetivamente aplicados**, já com os valores padrão. |
| `geradoEm` | Data e hora em que os dados foram calculados (mostrado na tela como "Atualizado às"). |
| `dados` | Conteúdo do endpoint (objeto ou lista). |

## Erros

Todos os erros voltam em JSON:

```json
{
  "timestamp": "2026-11-10T17:06:40.112",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Data inválida em 'inicio': 2026-13-01. Use o formato AAAA-MM-DD (ex.: 2026-11-10).",
  "campo": "inicio"
}
```

| Código | Quando acontece | Exemplo de `mensagem` |
|---|---|---|
| `400` | Data em formato errado | `Data inválida em 'inicio': 10/11/2026. Use o formato AAAA-MM-DD (ex.: 2026-11-10).` |
| `400` | `inicio` maior que `fim` | `A data inicial não pode ser maior que a data final.` |
| `400` | Período acima de 366 dias | `O período máximo de consulta é de 366 dias.` |
| `400` | Status inexistente | `Status inválido: ENTREGUE. Valores aceitos: RECEBIDO, EM_PREPARACAO, ...` |
| `400` | Modalidade inexistente | `Modalidade inválida: DRIVE. Valores aceitos: ENTREGA, RETIRADA.` |
| `400` | `agrupamento` ou `criterio` inválido | `Agrupamento inválido: ANO. Valores aceitos: DIA, SEMANA, MES.` |
| `400` | `limite` não numérico ou fora de 1–50 | `O limite deve estar entre 1 e 50.` |
| `401` | Sem login | `{"status":401, ...}` (corpo vazio ou padrão do servidor) |
| `403` | Perfil sem permissão ou método diferente de GET | `Acesso negado: seu perfil não tem permissão para esta operação.` |
| `503` | MongoDB fora do ar | `Não foi possível consultar o banco de dados no momento. Tente novamente em instantes.` |

---

## 1. `GET /api/dashboard/indicadores`

**RF01** — Indicadores gerais do período e comparação com o período anterior de mesmo tamanho.

Parâmetros: apenas os [filtros comuns](#filtros-comuns-rf05).

**Requisição**
```
GET /api/dashboard/indicadores?inicio=2026-11-01&fim=2026-11-10
```

**Resposta 200**
```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.481",
  "dados": {
    "totalPedidos": 4,
    "pedidosValidos": 3,
    "pedidosCancelados": 1,
    "pedidosEmAndamento": 1,
    "pedidosFinalizados": 2,
    "faturamento": 163.30,
    "ticketMedio": 54.43,
    "totalDescontos": 5.70,
    "itensVendidos": 5,
    "taxaCancelamentoPercentual": 25.0,
    "tempoMedioAteSaidaMinutos": 35.0,
    "comparativoPeriodoAnterior": {
      "inicio": "2026-10-22",
      "fim": "2026-10-31",
      "faturamento": 81.65,
      "pedidosValidos": 1,
      "ticketMedio": 81.65,
      "variacaoFaturamentoPercentual": 100.0,
      "variacaoPedidosPercentual": 200.0,
      "variacaoTicketMedioPercentual": -33.3
    }
  }
}
```

| Campo | Descrição |
|---|---|
| `faturamento` | Soma do total dos pedidos não cancelados (R$). |
| `ticketMedio` | `faturamento / pedidosValidos`. |
| `taxaCancelamentoPercentual` | `pedidosCancelados / totalPedidos × 100`. |
| `tempoMedioAteSaidaMinutos` | Média entre entrada e saída; `null` se nenhum pedido saiu. |
| `variacao...Percentual` | Variação em relação ao período anterior; `null` quando o anterior é zero. |

> Os valores deste exemplo são os mesmos do teste `DashboardServiceTest`, onde cada conta está explicada.

---

## 2. `GET /api/dashboard/vendas`

**RF02** — Série de vendas no tempo. Intervalos sem venda vêm com zero.

| Parâmetro | Tipo | Padrão | Valores |
|---|---|---|---|
| filtros comuns | | | |
| `agrupamento` | texto | `DIA` | `DIA`, `SEMANA` (começa na segunda-feira), `MES` |

**Requisição**
```
GET /api/dashboard/vendas?inicio=2026-11-01&fim=2026-11-10&agrupamento=SEMANA
```

**Resposta 200**
```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.503",
  "dados": [
    { "chave": "2026-10-26", "rotulo": "Sem. 26/10", "pedidos": 0, "cancelados": 0, "faturamento": 0.00 },
    { "chave": "2026-11-02", "rotulo": "Sem. 02/11", "pedidos": 2, "cancelados": 1, "faturamento": 151.30 },
    { "chave": "2026-11-09", "rotulo": "Sem. 09/11", "pedidos": 1, "cancelados": 0, "faturamento": 12.00 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `chave` | Identificador ordenável: data do dia, segunda-feira da semana ou `AAAA-MM`. |
| `rotulo` | Texto pronto para o eixo: `03/11`, `Sem. 02/11`, `nov/2026`. |
| `pedidos` | Pedidos válidos no intervalo. |
| `cancelados` | Pedidos cancelados no intervalo. |
| `faturamento` | Faturamento dos pedidos válidos. |

---

## 3. `GET /api/dashboard/vendas/por-hora`

**RF02** — Distribuição dos pedidos nas 24 horas do dia (horário de pico). Sempre devolve 24 itens (0h a 23h).

**Requisição**
```
GET /api/dashboard/vendas/por-hora?inicio=2026-11-01&fim=2026-11-10&modalidade=ENTREGA
```

**Resposta 200** (trecho)
```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": "ENTREGA" },
  "geradoEm": "2026-11-10T17:05:12.517",
  "dados": [
    { "chave": "0", "rotulo": "0h", "pedidos": 0, "cancelados": 0, "faturamento": 0.00 },
    { "chave": "19", "rotulo": "19h", "pedidos": 2, "cancelados": 0, "faturamento": 112.00 },
    { "chave": "21", "rotulo": "21h", "pedidos": 0, "cancelados": 1, "faturamento": 0.00 }
  ]
}
```

---

## 4. `GET /api/dashboard/pedidos/status`

**RF03** — Situação dos pedidos do período. Todos os 7 status aparecem, inclusive com zero.

**Requisição**
```
GET /api/dashboard/pedidos/status?inicio=2026-11-01&fim=2026-11-10
```

**Resposta 200**
```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.530",
  "dados": {
    "total": 4,
    "emAndamento": 1,
    "porStatus": [
      { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 0, "percentual": 0.0 },
      { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 1, "percentual": 25.0 },
      { "status": "PRONTO", "descricao": "Pronto", "quantidade": 0, "percentual": 0.0 },
      { "status": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega", "quantidade": 0, "percentual": 0.0 },
      { "status": "RETIRADO", "descricao": "Retirado", "quantidade": 0, "percentual": 0.0 },
      { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 2, "percentual": 50.0 },
      { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 1, "percentual": 25.0 }
    ]
  }
}
```

---

## 5. `GET /api/dashboard/pedidos/recentes`

**RF03** — Últimos pedidos (mais novo primeiro).

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| filtros comuns | | | |
| `limite` | inteiro | `10` | de 1 a 50 |

**Requisição**
```
GET /api/dashboard/pedidos/recentes?limite=2
```

**Resposta 200**
```json
{
  "filtros": { "inicio": "2026-10-12", "fim": "2026-11-10", "dias": 30, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.544",
  "dados": [
    {
      "id": "6730f1c2a8b4e51d2c9f0a11",
      "codigo": "9F0A11",
      "cliente": "Ana Souza",
      "status": "EM_PREPARACAO",
      "statusDescricao": "Em preparação",
      "modalidade": "ENTREGA",
      "itens": 3,
      "valorTotal": 111.80,
      "dataHoraEntrada": "2026-11-10T16:48:00",
      "responsavelEntrada": "atendente"
    },
    {
      "id": "6730f1c2a8b4e51d2c9f0a10",
      "codigo": "9F0A10",
      "cliente": "Bruno Lima",
      "status": "RECEBIDO",
      "statusDescricao": "Recebido",
      "modalidade": "RETIRADA",
      "itens": 1,
      "valorTotal": 45.00,
      "dataHoraEntrada": "2026-11-10T17:02:00",
      "responsavelEntrada": "gerente"
    }
  ]
}
```

---

## 6. `GET /api/dashboard/produtos/mais-vendidos`

**RF04** — Ranking de produtos dos pedidos **não cancelados**.

| Parâmetro | Tipo | Padrão | Valores |
|---|---|---|---|
| filtros comuns | | | |
| `criterio` | texto | `QUANTIDADE` | `QUANTIDADE` ou `FATURAMENTO` |
| `limite` | inteiro | `5` | de 1 a 50 |

Empate: desempata pelo nome do produto em ordem alfabética.

**Requisição**
```
GET /api/dashboard/produtos/mais-vendidos?inicio=2026-11-01&fim=2026-11-10&criterio=FATURAMENTO&limite=3
```

**Resposta 200**
```json
{
  "filtros": { "inicio": "2026-11-01", "fim": "2026-11-10", "dias": 10, "status": null, "modalidade": null },
  "geradoEm": "2026-11-10T17:05:12.561",
  "dados": [
    { "posicao": 1, "produtoId": "calabresa", "nome": "Pizza Calabresa", "quantidade": 2, "faturamento": 100.00, "participacaoPercentual": 59.2 },
    { "posicao": 2, "produtoId": "margherita", "nome": "Pizza Margherita", "quantidade": 1, "faturamento": 45.00, "participacaoPercentual": 26.6 },
    { "posicao": 3, "produtoId": "refri", "nome": "Refrigerante 2L", "quantidade": 2, "faturamento": 24.00, "participacaoPercentual": 14.2 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `faturamento` | Soma de preço × quantidade dos itens (antes do desconto do pedido). |
| `participacaoPercentual` | Fatia do produto no total do critério escolhido. |

---

## 7. `GET /api/dashboard/filtros`

**RF05** — Valores aceitos pelos filtros. A tela monta os campos a partir desta resposta. Não usa o envelope (não depende de período).

**Resposta 200**
```json
{
  "status": [
    { "valor": "RECEBIDO", "descricao": "Recebido" },
    { "valor": "EM_PREPARACAO", "descricao": "Em preparação" },
    { "valor": "PRONTO", "descricao": "Pronto" },
    { "valor": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega" },
    { "valor": "RETIRADO", "descricao": "Retirado" },
    { "valor": "FINALIZADO", "descricao": "Finalizado" },
    { "valor": "CANCELADO", "descricao": "Cancelado" }
  ],
  "modalidades": [
    { "valor": "ENTREGA", "descricao": "Entrega" },
    { "valor": "RETIRADA", "descricao": "Retirada no balcão" }
  ],
  "agrupamentos": [
    { "valor": "DIA", "descricao": "Por dia" },
    { "valor": "SEMANA", "descricao": "Por semana" },
    { "valor": "MES", "descricao": "Por mês" }
  ],
  "criteriosRanking": [
    { "valor": "QUANTIDADE", "descricao": "Quantidade vendida" },
    { "valor": "FATURAMENTO", "descricao": "Faturamento" }
  ],
  "periodoPadraoDias": 30,
  "periodoMaximoDias": 366
}
```

---

Respostas reais capturadas do sistema rodando estão em [`evidencias/api/`](evidencias/api/) e resumidas em [EVIDENCIAS-TESTES.md](EVIDENCIAS-TESTES.md).
