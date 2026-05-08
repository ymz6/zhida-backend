[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$TmpPath,
    [string]$ConfigPath,
    [string]$RedisHost,
    [string]$RedisUsername,
    [int]$RedisPort = 0,
    [int]$RedisDatabase = -1,
    [string]$RedisPassword,
    [switch]$FlushAll,
    [switch]$SkipTmp,
    [switch]$SkipRedis
)

$ErrorActionPreference = "Stop"

$script:ProjectRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}
else {
    $PSScriptRoot
}

if ([string]::IsNullOrWhiteSpace($TmpPath)) {
    $TmpPath = Join-Path $script:ProjectRoot "tmp"
}

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $script:ProjectRoot "app/src/main/resources/application.yaml"
}

function Get-RedisConfigFromYaml {
    param([string]$Path)

    $config = @{
        Host = "localhost"
        Username = $null
        Port = 6379
        Database = 0
        Password = $null
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Warning "未找到配置文件：$Path，将使用默认 Redis 连接参数。"
        return $config
    }

    $lines = Get-Content -LiteralPath $Path
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
    param([string]$Path)

    $resolvedRoot = (Resolve-Path -LiteralPath $script:ProjectRoot).Path

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
        Write-Host "tmp 目录不存在，已创建：$Path"
        return
    }

    $resolvedTmp = (Resolve-Path -LiteralPath $Path).Path

    # 防止误传根目录或仓库外路径，只允许清理当前仓库里的 tmp 内容。
    if ($resolvedTmp -ne (Join-Path $resolvedRoot "tmp")) {
        throw "拒绝清理非项目 tmp 目录：$resolvedTmp"
    }

    if ($PSCmdlet.ShouldProcess($resolvedTmp, "清空目录内容")) {
        Get-ChildItem -LiteralPath $resolvedTmp -Force | Remove-Item -Recurse -Force
        Write-Host "已清空 tmp 目录内容：$resolvedTmp"
    }
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
    param(
        [string]$Server,
        [string]$Username,
        [int]$Port,
        [int]$Database,
        [string]$Password,
        [bool]$UseFlushAll
    )

    if ($UseFlushAll) {
        # FlushAll 会清空 Redis 实例全部库，默认不使用，避免误删其它开发数据。
        $command = @("FLUSHALL")
        $action = "清空 Redis 全部数据库"
    }
    else {
        $command = @("FLUSHDB")
        $action = "清空 Redis database $Database"
    }

    if ($PSCmdlet.ShouldProcess("$Server`:$Port", $action)) {
        $client = [System.Net.Sockets.TcpClient]::new()
        $reader = $null

        try {
            $client.Connect($Server, $Port)
            $stream = $client.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $false, 1024, $true)

            if (-not [string]::IsNullOrWhiteSpace($Password)) {
                if ([string]::IsNullOrWhiteSpace($Username)) {
                    Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("AUTH", $Password) | Out-Null
                }
                else {
                    Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("AUTH", $Username, $Password) | Out-Null
                }
            }

            if (-not $UseFlushAll) {
                Invoke-RedisCommand -Stream $stream -Reader $reader -Parts @("SELECT", "$Database") | Out-Null
            }

            Invoke-RedisCommand -Stream $stream -Reader $reader -Parts $command | Out-Null
        }
        finally {
            if ($null -ne $reader) {
                $reader.Dispose()
            }

            $client.Dispose()
        }

        Write-Host "Redis 缓存已清空：$Server`:$Port"
    }
}

$redisConfig = Get-RedisConfigFromYaml -Path $ConfigPath

if ([string]::IsNullOrWhiteSpace($RedisHost)) {
    $RedisHost = $redisConfig.Host
}

if ([string]::IsNullOrWhiteSpace($RedisUsername)) {
    $RedisUsername = $redisConfig.Username
}

if ($RedisPort -le 0) {
    $RedisPort = $redisConfig.Port
}

if ($RedisDatabase -lt 0) {
    $RedisDatabase = $redisConfig.Database
}

if ($null -eq $RedisPassword) {
    $RedisPassword = $redisConfig.Password
}

Write-Host "开始清理开发环境状态..."

if (-not $SkipTmp) {
    Clear-TmpDirectory -Path $TmpPath
}

if (-not $SkipRedis) {
    Clear-RedisCache `
        -Server $RedisHost `
        -Username $RedisUsername `
        -Port $RedisPort `
        -Database $RedisDatabase `
        -Password $RedisPassword `
        -UseFlushAll:$FlushAll
}

Write-Host "清理完成。"
