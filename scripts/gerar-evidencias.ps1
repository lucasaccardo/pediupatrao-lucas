# =====================================================================
#  Gera as evidências de teste do Dashboard Administrativo
#  Uso (na pasta do projeto, com o sistema ligado em http://localhost:8080):
#     powershell -ExecutionPolicy Bypass -File scripts\gerar-evidencias.ps1
#  Resultado: docs\evidencias\01-testes-automatizados.txt
#             docs\evidencias\02-testes-api.md  +  docs\evidencias\api\*.json
# =====================================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$pasta = Join-Path $raiz 'docs\evidencias'
$pastaApi = Join-Path $pasta 'api'
New-Item -ItemType Directory -Force -Path $pastaApi | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Salvar($caminho, $texto) { [System.IO.File]::WriteAllText($caminho, $texto, $utf8) }

# ---------- Java 21 (mesmo esquema do INICIAR) ----------
$jdk = Get-ChildItem -Directory "$env:USERPROFILE\jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName; $env:Path = "$($jdk.FullName)\bin;$env:Path" }

# ---------- 1) Testes automatizados ----------
Write-Host '1/2  Rodando os testes automatizados (mvnw test)... aguarde.' -ForegroundColor Cyan
$saida = & cmd /c "mvnw.cmd -B test 2>&1"
$cabecalho = "Evidência: testes automatizados (JUnit 5 + Mockito + MockMvc)`r`nExecutado em: $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss')`r`nComando: mvnw -B test`r`n`r`n"
Salvar (Join-Path $pasta '01-testes-automatizados.txt') ($cabecalho + ($saida -join "`r`n"))
$saida | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE' | ForEach-Object { Write-Host "   $($_.Line)" }

# ---------- 2) Testes da API no sistema rodando ----------
Write-Host '2/2  Testando a API em' $base '...' -ForegroundColor Cyan

function Ler-Corpo($resposta) {
    return [System.Text.Encoding]::UTF8.GetString($resposta.RawContentStream.ToArray())
}

function Nova-Sessao($usuario, $senha) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $pagina = Invoke-WebRequest "$base/login" -WebSession $s -UseBasicParsing
    $m = [regex]::Match($pagina.Content, 'name="_csrf"\s+value="([^"]+)"')
    if (-not $m.Success) { throw 'Não encontrei o token CSRF na página de login. O sistema está ligado?' }
    $null = Invoke-WebRequest "$base/login" -Method Post -WebSession $s -UseBasicParsing `
        -Body @{ username = $usuario; password = $senha; _csrf = $m.Groups[1].Value }
    return $s
}

function Token-Csrf($sessao) {
    $pagina = Invoke-WebRequest "$base/dashboard" -WebSession $sessao -UseBasicParsing
    $token = [regex]::Match($pagina.Content, 'name="_csrf"\s+content="([^"]+)"').Groups[1].Value
    $header = [regex]::Match($pagina.Content, 'name="_csrf_header"\s+content="([^"]+)"').Groups[1].Value
    return @{ $header = $token }
}

function Chamar($sessao, $metodo, $caminho, $cabecalhos) {
    $h = @{ Accept = 'application/json' }
    if ($cabecalhos) { foreach ($k in $cabecalhos.Keys) { $h[$k] = $cabecalhos[$k] } }
    $parametros = @{ Uri = "$base$caminho"; Method = $metodo; UseBasicParsing = $true; Headers = $h }
    if ($sessao) { $parametros.WebSession = $sessao }
    try {
        $r = Invoke-WebRequest @parametros
        return @{ codigo = [int]$r.StatusCode; corpo = (Ler-Corpo $r) }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ codigo = 0; corpo = $_.Exception.Message } }
        $corpo = ''
        if ($PSVersionTable.PSVersion.Major -ge 6) {
            if ($_.ErrorDetails) { $corpo = $_.ErrorDetails.Message }
        } else {
            try {
                $leitor = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
                $corpo = $leitor.ReadToEnd()
            } catch { }
        }
        return @{ codigo = [int]$resp.StatusCode; corpo = $corpo }
    }
}

function Formatar($json) {
    if ([string]::IsNullOrWhiteSpace($json)) { return '(corpo vazio)' }
    try { return ($json | ConvertFrom-Json | ConvertTo-Json -Depth 12) } catch { return $json }
}

$gerente = Nova-Sessao 'gerente' 'gerente123'
$admin = Nova-Sessao 'admin' 'teste123'
$atendente = Nova-Sessao 'atendente' 'atendente123'
$csrfGerente = Token-Csrf $gerente

$hoje = Get-Date
$h = $hoje.ToString('yyyy-MM-dd')
$ini7 = $hoje.AddDays(-6).ToString('yyyy-MM-dd')
$ini90 = $hoje.AddDays(-89).ToString('yyyy-MM-dd')

# ID, cenário, sessão, método, caminho, código esperado, cabeçalhos extras
$casos = @(
    @('API-01', 'Sem login: indicadores', $null, 'GET', '/api/dashboard/indicadores', 401, $null),
    @('API-02', 'Atendente: indicadores (perfil sem acesso)', $atendente, 'GET', '/api/dashboard/indicadores', 403, $null),
    @('API-03', 'Gerente: opções de filtro', $gerente, 'GET', '/api/dashboard/filtros', 200, $null),
    @('API-04', 'Gerente: indicadores com período padrão (30 dias)', $gerente, 'GET', '/api/dashboard/indicadores', 200, $null),
    @('API-05', 'Gerente: indicadores dos últimos 7 dias', $gerente, 'GET', "/api/dashboard/indicadores?inicio=$ini7&fim=$h", 200, $null),
    @('API-06', 'Gerente: vendas por dia', $gerente, 'GET', "/api/dashboard/vendas?inicio=$ini7&fim=$h&agrupamento=DIA", 200, $null),
    @('API-07', 'Gerente: vendas por semana', $gerente, 'GET', '/api/dashboard/vendas?agrupamento=SEMANA', 200, $null),
    @('API-08', 'Gerente: vendas por mês (90 dias)', $gerente, 'GET', "/api/dashboard/vendas?inicio=$ini90&fim=$h&agrupamento=MES", 200, $null),
    @('API-09', 'Gerente: pedidos por hora do dia', $gerente, 'GET', '/api/dashboard/vendas/por-hora', 200, $null),
    @('API-10', 'Gerente: situação dos pedidos', $gerente, 'GET', '/api/dashboard/pedidos/status', 200, $null),
    @('API-11', 'Gerente: 5 pedidos mais recentes', $gerente, 'GET', '/api/dashboard/pedidos/recentes?limite=5', 200, $null),
    @('API-12', 'Gerente: ranking por quantidade', $gerente, 'GET', '/api/dashboard/produtos/mais-vendidos', 200, $null),
    @('API-13', 'Gerente: ranking por faturamento (top 3)', $gerente, 'GET', '/api/dashboard/produtos/mais-vendidos?criterio=FATURAMENTO&limite=3', 200, $null),
    @('API-14', 'Gerente: filtro status=CANCELADO', $gerente, 'GET', '/api/dashboard/indicadores?status=CANCELADO', 200, $null),
    @('API-15', 'Gerente: filtro modalidade=RETIRADA', $gerente, 'GET', '/api/dashboard/indicadores?modalidade=RETIRADA', 200, $null),
    @('API-16', 'Admin: indicadores', $admin, 'GET', '/api/dashboard/indicadores', 200, $null),
    @('API-17', 'Data em formato inválido', $gerente, 'GET', '/api/dashboard/indicadores?inicio=2026-13-01', 400, $null),
    @('API-18', 'Data inicial maior que a final', $gerente, 'GET', '/api/dashboard/vendas?inicio=2026-11-10&fim=2026-11-01', 400, $null),
    @('API-19', 'Período maior que 366 dias', $gerente, 'GET', '/api/dashboard/indicadores?inicio=2024-01-01&fim=2026-11-10', 400, $null),
    @('API-20', 'Status inexistente', $gerente, 'GET', '/api/dashboard/pedidos/status?status=ENTREGUE', 400, $null),
    @('API-21', 'Limite não numérico', $gerente, 'GET', '/api/dashboard/produtos/mais-vendidos?limite=abc', 400, $null),
    @('API-22', 'Agrupamento inválido', $gerente, 'GET', '/api/dashboard/vendas?agrupamento=ANO', 400, $null),
    @('API-23', 'POST na API (somente leitura), com token CSRF', $gerente, 'POST', '/api/dashboard/indicadores', 403, $csrfGerente),
    @('API-24', 'Atendente abrindo a página /dashboard', $atendente, 'GET', '/dashboard', 403, $null)
)

$linhas = @()
$ok = 0
foreach ($c in $casos) {
    $res = Chamar $c[2] $c[3] $c[4] $c[6]
    $passou = ($res.codigo -eq $c[5])
    if ($passou) { $ok++ }
    $arquivo = "$($c[0]).json"
    $conteudo = "// $($c[0]) - $($c[1])`n// $($c[3]) $($c[4])`n// HTTP $($res.codigo) (esperado $($c[5]))`n" + (Formatar $res.corpo)
    if ($c[4] -eq '/dashboard') { $conteudo = "// $($c[0]) - $($c[1])`n// HTTP $($res.codigo) (esperado $($c[5]))`n(página HTML de erro)" }
    Salvar (Join-Path $pastaApi $arquivo) $conteudo
    $marca = if ($passou) { 'OK' } else { 'FALHOU' }
    $linhas += "| $($c[0]) | $($c[1]) | ``$($c[3]) $($c[4])`` | $($c[5]) | $($res.codigo) | $marca | [ver](api/$arquivo) |"
    $cor = if ($passou) { 'Green' } else { 'Red' }
    Write-Host ("   {0}  {1,-48} esperado {2}  obtido {3}  {4}" -f $c[0], $c[1], $c[5], $res.codigo, $marca) -ForegroundColor $cor
}

$relatorio = @"
# Evidência: testes da API no sistema rodando

- Executado em: $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss')
- Servidor: $base (MongoDB local com pedidos de demonstração)
- Usuários: gerente, admin e atendente (sessão real, com login e token CSRF)
- Resultado: **$ok de $($casos.Count) casos com o código HTTP esperado**

| ID | Cenário | Requisição | Esperado | Obtido | Resultado | Resposta |
|---|---|---|---|---|---|---|
$($linhas -join "`n")
"@
Salvar (Join-Path $pasta '02-testes-api.md') $relatorio

Write-Host ''
Write-Host "Pronto: $ok de $($casos.Count) casos de API OK." -ForegroundColor Cyan
Write-Host 'Arquivos gerados em docs\evidencias' -ForegroundColor Cyan
