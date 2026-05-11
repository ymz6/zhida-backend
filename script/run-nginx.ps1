<#
.SYNOPSIS
启动项目内置 nginx，并在标准输入结束后关闭 nginx。

.DESCRIPTION
运行脚本后会后台启动 nginx/nginx.exe，然后等待按键。
按 Ctrl+D 后，脚本会执行 nginx -s quit，让 nginx 优雅退出。
#>

$ErrorActionPreference = "Stop"

$projectRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
}
else {
    Split-Path -Parent $PSScriptRoot
}

$nginxRoot = Join-Path $projectRoot "nginx"
$nginxExe = Join-Path $projectRoot "nginx/nginx.exe"
$nginxConf = Join-Path $projectRoot "nginx/conf/nginx.conf"

if (-not (Test-Path -LiteralPath $nginxExe)) {
    throw "未找到 nginx 可执行文件：$nginxExe"
}

if (-not (Test-Path -LiteralPath $nginxConf)) {
    throw "未找到 nginx 配置文件：$nginxConf"
}

$nginxStarted = $false

try {
    Write-Host "正在启动 nginx..."
    $process = Start-Process -FilePath $nginxExe -ArgumentList @("-p", $nginxRoot, "-c", $nginxConf) -PassThru -WindowStyle Hidden
    Start-Sleep -Milliseconds 300

    if ($process.HasExited -and $process.ExitCode -ne 0) {
        throw "nginx 启动失败，退出码：$($process.ExitCode)"
    }

    $nginxStarted = $true
    Write-Host "nginx 已启动。按 Ctrl+D 结束并关闭 nginx。"

    while ($true) {
        $key = [Console]::ReadKey($true)
        if ($key.Key -eq "D" -and ($key.Modifiers -band [ConsoleModifiers]::Control)) {
            break
        }
    }
}
finally {
    if ($nginxStarted) {
        Write-Host "正在关闭 nginx..."
        # 使用 quit 优雅退出，避免直接终止进程导致请求被硬切断。
        & $nginxExe -p $nginxRoot -c $nginxConf -s quit
        Write-Host "nginx 已关闭。"
    }
}
