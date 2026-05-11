$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$scriptPath = Join-Path $repoRoot "script/run-nginx.ps1"

if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Script not found: $scriptPath"
}

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)

if ($parseErrors.Count -gt 0) {
    throw "Script has parse errors: $($parseErrors[0].Message)"
}

if ($ast.ParamBlock -ne $null) {
    throw "run-nginx.ps1 should use fixed project defaults and must not expose top-level parameters."
}

$scriptText = [System.IO.File]::ReadAllText($scriptPath)
foreach ($requiredText in @(
    "nginx/nginx.exe",
    "nginx/conf/nginx.conf",
    "Start-Process",
    '[Console]::ReadKey($true)',
    "D",
    "Control",
    '$nginxStarted = $false',
    '$nginxStarted = $true',
    'if ($nginxStarted)',
    "-s",
    "quit",
    "finally"
)) {
    if (-not $scriptText.Contains($requiredText)) {
        throw "Missing required nginx lifecycle behavior: $requiredText"
    }
}

foreach ($forbiddenText in @("[Console]::In.ReadToEnd()", "& $nginxExe -p $nginxRoot -c $nginxConf`r`n")) {
    if ($scriptText.Contains($forbiddenText)) {
        throw "Script still contains blocking behavior: $forbiddenText"
    }
}

Write-Host "run-nginx.Tests.ps1 passed."
