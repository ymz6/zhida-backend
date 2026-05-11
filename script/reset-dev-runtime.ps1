<#
.SYNOPSIS
重置本地开发运行状态。

.DESCRIPTION
固定清理项目 tmp 目录下的运行残留，并清空 application.yaml 中配置的 Redis database。
tmp 目录和其一级子目录会保留，只删除一级子目录中的内容；tmp 根目录下的零散文件会被删除。
#>

$ErrorActionPreference = "Stop"

$script:ProjectRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
}
else {
    Split-Path -Parent $PSScriptRoot
}

$script:TmpPath = Join-Path $script:ProjectRoot "tmp"
$script:ConfigPath = Join-Path $script:ProjectRoot "app/src/main/resources/application.yaml"

function Get-RedisConfigFromYaml {
    $config = @{
        Host = "localhost"
        Username = $null
        Port = 6379
        Database = 0
        Password = $null
    }

    if (-not (Test-Path -LiteralPath $script:ConfigPath)) {
        Write-Warning "未找到配置文件：$script:ConfigPath，将使用默认 Redis 连接参数。"
        return $config
    }

    $lines = Get-Content -LiteralPath $script:ConfigPath
    $inSpring = $false
    $inData = $false
    $inRedis = $false

    foreach ($line in $lines) {
        if ($line -match "^\s*#") {
            continue
        }

        if ($line -match "^spring:\s*$") {
            $inSpring = $true
            $inData = $false
            $inRedis = $false
            continue
        }

        if ($line -match "^\S") {
            $inSpring = $false
            $inData = $false
            $inRedis = $false
        }

        if ($inSpring -and $line -match "^\s{2}data:\s*$") {
            $inData = $true
            $inRedis = $false
            continue
        }

        if ($inSpring -and $inData -and $line -match "^\s{4}redis:\s*$") {
            $inRedis = $true
            continue
        }

        if ($inRedis -and $line -match "^\s{6}([^:#]+):\s*(.*)\s*$") {
            $key = $Matches[1].Trim()
            $value = $Matches[2].Trim().Trim("'").Trim('"')

            switch ($key) {
                "host" { $config.Host = $value }
                "username" {
                    if (-not [string]::IsNullOrWhiteSpace($value)) {
                        $config.Username = $value
                    }
                }
                "port" { $config.Port = [int]$value }
                "database" { $config.Database = [int]$value }
                "password" {
                    if (-not [string]::IsNullOrWhiteSpace($value)) {
                        $config.Password = $value
                    }
                }
            }
        }
    }

    return $config
}

function Clear-TmpDirectory {
    $resolvedRoot = (Resolve-Path -LiteralPath $script:ProjectRoot).Path

    if (-not (Test-Path -LiteralPath $script:TmpPath)) {
        New-Item -ItemType Directory -Path $script:TmpPath | Out-Null
        Write-Host "tmp 目录不存在，已创建：$script:TmpPath"
        return
    }

    $resolvedTmp = (Resolve-Path -LiteralPath $script:TmpPath).Path

    # 防止误传根目录或仓库外路径，只允许清理当前仓库里的 tmp 内容。
    if ($resolvedTmp -ne (Join-Path $resolvedRoot "tmp")) {
        throw "拒绝清理非项目 tmp 目录：$resolvedTmp"
    }

    Get-ChildItem -LiteralPath $resolvedTmp -Force | ForEach-Object {
        if ($_.PSIsContainer) {
            # 保留 tmp 下的一级子目录，只删除这些子目录中的残留内容。
            Get-ChildItem -LiteralPath $_.FullName -Force | Remove-Item -Recurse -Force
        }
        else {
            Remove-Item -LiteralPath $_.FullName -Force
        }
    }

    Write-Host "已清空 tmp 子目录内容：$resolvedTmp"
}

function New-RedisCommandBytes {
    param([string[]]$Parts)

    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.Append("*$($Parts.Count)`r`n")

    foreach ($part in $Parts) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($part)
        [void]$builder.Append("`$$($bytes.Length)`r`n")
        [void]$builder.Append($part)
        [void]$builder.Append("`r`n")
    }

    return [System.Text.Encoding]::UTF8.GetBytes($builder.ToString())
}

function Invoke-RedisCommand {
    param(
        [System.Net.Sockets.NetworkStream]$Stream,
        [System.IO.StreamReader]$Reader,
        [string[]]$Parts
    )

    # Redis 使用 RESP 协议，这里只需要发送简单命令并读取单行状态响应。
    $bytes = New-RedisCommandBytes -Parts $Parts
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()

    $response = $Reader.ReadLine()
    if ($null -eq $response) {
        throw "Redis 连接已断开。"
    }

    if ($response.StartsWith("-")) {
        throw "Redis 命令失败：$($response.Substring(1))"
    }

    return $response
}

function Clear-RedisCache {
    $redisConfig = Get-RedisConfigFromYaml
    $client = [System.Net.Sockets.TcpClient]::new()
    $reader = $null

    try {
        $client.Connect($redisConfig.Host, $redisConfig.Port)
        $stream = $client.GetStream()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $false, 1024, $true)

        if (-not [string]::IsNullOrWhiteSpace($redisConfig.Password)) {
            if ([string]::IsNullOrWhiteSpace($redisConfig.Username)) {
                Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("AUTH", $redisConfig.Password) | Out-Null
            }
            else {
                Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("AUTH", $redisConfig.Username, $redisConfig.Password) | Out-Null
            }
        }

        Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("SELECT", "$($redisConfig.Database)") | Out-Null
        Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("FLUSHDB") | Out-Null
    }
    finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }

        $client.Dispose()
    }

    Write-Host "Redis 缓存已清空：$($redisConfig.Host):$($redisConfig.Port)，database $($redisConfig.Database)"
}

Write-Host "开始重置本地开发运行状态..."
Clear-TmpDirectory
Clear-RedisCache
Write-Host "重置完成。"
