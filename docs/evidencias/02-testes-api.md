# Evidência: testes da API no sistema rodando

- Executado em: 10/11/2026 17:34:08
- Servidor: http://localhost:8080 (MongoDB local com pedidos de demonstração)
- Usuários: gerente, admin e atendente (sessão real, com login e token CSRF)
- Resultado: **24 de 24 casos com o código HTTP esperado**

| ID | Cenário | Requisição | Esperado | Obtido | Resultado | Resposta |
|---|---|---|---|---|---|---|
| API-01 | Sem login: indicadores | `GET /api/dashboard/indicadores` | 401 | 401 | OK | [ver](api/API-01.json) |
| API-02 | Atendente: indicadores (perfil sem acesso) | `GET /api/dashboard/indicadores` | 403 | 403 | OK | [ver](api/API-02.json) |
| API-03 | Gerente: opções de filtro | `GET /api/dashboard/filtros` | 200 | 200 | OK | [ver](api/API-03.json) |
| API-04 | Gerente: indicadores com período padrão (30 dias) | `GET /api/dashboard/indicadores` | 200 | 200 | OK | [ver](api/API-04.json) |
| API-05 | Gerente: indicadores dos últimos 7 dias | `GET /api/dashboard/indicadores?inicio=2026-11-04&fim=2026-11-10` | 200 | 200 | OK | [ver](api/API-05.json) |
| API-06 | Gerente: vendas por dia | `GET /api/dashboard/vendas?inicio=2026-11-04&fim=2026-11-10&agrupamento=DIA` | 200 | 200 | OK | [ver](api/API-06.json) |
| API-07 | Gerente: vendas por semana | `GET /api/dashboard/vendas?agrupamento=SEMANA` | 200 | 200 | OK | [ver](api/API-07.json) |
| API-08 | Gerente: vendas por mês (90 dias) | `GET /api/dashboard/vendas?inicio=2026-08-13&fim=2026-11-10&agrupamento=MES` | 200 | 200 | OK | [ver](api/API-08.json) |
| API-09 | Gerente: pedidos por hora do dia | `GET /api/dashboard/vendas/por-hora` | 200 | 200 | OK | [ver](api/API-09.json) |
| API-10 | Gerente: situação dos pedidos | `GET /api/dashboard/pedidos/status` | 200 | 200 | OK | [ver](api/API-10.json) |
| API-11 | Gerente: 5 pedidos mais recentes | `GET /api/dashboard/pedidos/recentes?limite=5` | 200 | 200 | OK | [ver](api/API-11.json) |
| API-12 | Gerente: ranking por quantidade | `GET /api/dashboard/produtos/mais-vendidos` | 200 | 200 | OK | [ver](api/API-12.json) |
| API-13 | Gerente: ranking por faturamento (top 3) | `GET /api/dashboard/produtos/mais-vendidos?criterio=FATURAMENTO&limite=3` | 200 | 200 | OK | [ver](api/API-13.json) |
| API-14 | Gerente: filtro status=CANCELADO | `GET /api/dashboard/indicadores?status=CANCELADO` | 200 | 200 | OK | [ver](api/API-14.json) |
| API-15 | Gerente: filtro modalidade=RETIRADA | `GET /api/dashboard/indicadores?modalidade=RETIRADA` | 200 | 200 | OK | [ver](api/API-15.json) |
| API-16 | Admin: indicadores | `GET /api/dashboard/indicadores` | 200 | 200 | OK | [ver](api/API-16.json) |
| API-17 | Data em formato inválido | `GET /api/dashboard/indicadores?inicio=2026-13-01` | 400 | 400 | OK | [ver](api/API-17.json) |
| API-18 | Data inicial maior que a final | `GET /api/dashboard/vendas?inicio=2026-11-10&fim=2026-11-01` | 400 | 400 | OK | [ver](api/API-18.json) |
| API-19 | Período maior que 366 dias | `GET /api/dashboard/indicadores?inicio=2024-01-01&fim=2026-11-10` | 400 | 400 | OK | [ver](api/API-19.json) |
| API-20 | Status inexistente | `GET /api/dashboard/pedidos/status?status=ENTREGUE` | 400 | 400 | OK | [ver](api/API-20.json) |
| API-21 | Limite não numérico | `GET /api/dashboard/produtos/mais-vendidos?limite=abc` | 400 | 400 | OK | [ver](api/API-21.json) |
| API-22 | Agrupamento inválido | `GET /api/dashboard/vendas?agrupamento=ANO` | 400 | 400 | OK | [ver](api/API-22.json) |
| API-23 | POST na API (somente leitura), com token CSRF | `POST /api/dashboard/indicadores` | 403 | 403 | OK | [ver](api/API-23.json) |
| API-24 | Atendente abrindo a página /dashboard | `GET /dashboard` | 403 | 403 | OK | [ver](api/API-24.json) |