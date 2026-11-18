<#
.SYNOPSIS
    Khoi chay nhanh ung dung DMT Gym (v1) tren Windows PowerShell.
.EXAMPLE
    .\start-dmt-gym.ps1
.EXAMPLE
    .\start-dmt-gym.ps1 -Port 8081 -BuildFromSource
#>
param(
    [string]$DbUrl = "jdbc:mysql://localhost:3306/gym_exam_demo?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=Asia/Ho_Chi_Minh&characterEncoding=utf8",
    [string]$DbUser = "root",
    [string]$DbPassword = "",
    [string]$DemoPassword = "Gym@12345",
    [int]$Port = 8080,
    [switch]$BuildFromSource
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Join-Path $RootDir "app"
$ReleaseJarPath = Join-Path $AppDir "gym-management-1.0.0.jar"
$TargetJarPath = Join-Path $AppDir "target\gym-management-1.0.0.jar"

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $DbPassword = $env:GYM_DB_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $secure = Read-Host -Prompt "Nhap mat khau MySQL cho user '$DbUser'" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $DbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$javaBin = (Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
if (-not $javaBin) {
    $javaBin = Get-ChildItem "C:\Program Files\Java\jdk*\bin\java.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $javaBin) {
    throw "Khong tim thay Java (can JDK 21+). Vui long cai JDK 21+ va them vao PATH."
}

$env:GYM_DB_URL = $DbUrl
$env:GYM_DB_USER = $DbUser
$env:GYM_DB_PASSWORD = $DbPassword
$env:GYM_DEMO_PASSWORD = $DemoPassword
$env:GYM_PORT = "$Port"

Write-Host "Dang khoi dong DMT Gym (v1) tai http://localhost:$Port (Asia/Ho_Chi_Minh)..." -ForegroundColor Cyan
Set-Location $AppDir

$jarToRun = $null
if (-not $BuildFromSource) {
    if (Test-Path $ReleaseJarPath) {
        $jarToRun = $ReleaseJarPath
    } elseif (Test-Path $TargetJarPath) {
        $jarToRun = $TargetJarPath
    }
}

if ($jarToRun) {
    & $javaBin "-Duser.timezone=Asia/Ho_Chi_Minh" -jar $jarToRun
} else {
    $jdkHome = Split-Path -Parent (Split-Path -Parent $javaBin)
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = $jdkHome }
    & .\mvnw.cmd spring-boot:run
}
