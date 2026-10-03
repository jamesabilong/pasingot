[CmdletBinding()]
param(
    [string]$AdbPath,
    [string]$PhoneSerial,
    [string]$WatchSerial,
    [switch]$AllowEmulators,
    [ValidatePattern('^[A-Za-z0-9_-]{1,64}$')]
    [string]$Label = 'baseline',
    [switch]$IncludeInstalledApk
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'app.personal.workouttracker'
if (-not $AdbPath) {
    $sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $AdbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "adb not found: $AdbPath" }
if (($PhoneSerial -and -not $WatchSerial) -or ($WatchSerial -and -not $PhoneSerial)) {
    throw 'Supply both PhoneSerial and WatchSerial, or omit both for unambiguous discovery.'
}
if ($PhoneSerial -and $PhoneSerial -eq $WatchSerial) { throw 'Phone and watch serials must differ.' }

function Invoke-AdbRead {
    param([string[]]$AdbArguments)
    $output = & $AdbPath @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb read failed: $($AdbArguments -join ' '): $output" }
    return ($output -join "`n").Trim()
}

function Read-Property {
    param([string]$Serial, [string]$Name)
    Invoke-AdbRead -AdbArguments @('-s', $Serial, 'shell', 'getprop', $Name)
}

$inventory = Invoke-AdbRead -AdbArguments @('devices', '-l')
$gitHead = (& git -C $workspaceRoot rev-parse HEAD) -join ''
if ($LASTEXITCODE -ne 0) { throw 'Cannot read repository checkpoint.' }
$gitStatus = @(& git -C $workspaceRoot status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read repository status.' }
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$reportRoot = Join-Path $workspaceRoot "output\device-validation\$stamp-$Label"
New-Item -ItemType Directory -Path $reportRoot | Out-Null
$inventory | Set-Content -LiteralPath (Join-Path $reportRoot 'adb-devices.txt')
$devices = @()
$index = 0
foreach ($line in ($inventory -split '\r?\n')) {
    if ($line -notmatch '^(\S+)\s+(device|offline|unauthorized|no permissions)\b') { continue }
    $serial = $Matches[1]
    $state = $Matches[2]
    $index += 1
    $device = [ordered]@{ serial = $serial; adbState = $state; eligible = $false }
    if ($state -eq 'device') {
        $device.model = Read-Property $serial 'ro.product.model'
        $device.manufacturer = Read-Property $serial 'ro.product.manufacturer'
        $device.hardware = Read-Property $serial 'ro.hardware'
        $device.characteristics = Read-Property $serial 'ro.build.characteristics'
        $device.osRelease = Read-Property $serial 'ro.build.version.release'
        $device.apiLevel = Read-Property $serial 'ro.build.version.sdk'
        $device.securityPatch = Read-Property $serial 'ro.build.version.security_patch'
        $device.buildFingerprint = Read-Property $serial 'ro.build.fingerprint'
        $qemuKernel = Read-Property $serial 'ro.kernel.qemu'
        $qemuBoot = Read-Property $serial 'ro.boot.qemu'
        $device.isEmulator = $serial.StartsWith('emulator-') -or $qemuKernel -eq '1' -or
            $qemuBoot -eq '1' -or $device.hardware -in @('ranchu', 'goldfish')
        $device.role = if (($device.characteristics -split ',') -contains 'watch') { 'watch' }
            elseif (($device.characteristics -split ',') -contains 'tv') { 'other' } else { 'phone' }
        $device.eligible = ($AllowEmulators -or -not $device.isEmulator) -and $device.role -ne 'other'
        $deviceDir = Join-Path $reportRoot "device-$index"
        New-Item -ItemType Directory -Path $deviceDir | Out-Null
        $device.evidenceDirectory = "device-$index"
        $battery = Invoke-AdbRead -AdbArguments @('-s', $serial, 'shell', 'dumpsys', 'battery')
        $battery | Set-Content -LiteralPath (Join-Path $deviceDir 'battery.txt')
        $device.batteryLevel = if ($battery -match '(?m)^\s*level:\s*(\d+)') { [int]$Matches[1] } else { $null }
        $device.batteryScale = if ($battery -match '(?m)^\s*scale:\s*(\d+)') { [int]$Matches[1] } else { $null }
        $package = Invoke-AdbRead -AdbArguments @('-s', $serial, 'shell', 'dumpsys', 'package', $packageName)
        $package | Set-Content -LiteralPath (Join-Path $deviceDir 'package.txt')
        $device.appInstalled = $package -match 'versionCode='
        $device.versionCode = if ($package -match 'versionCode=(\d+)') { $Matches[1] } else { $null }
        $device.versionName = if ($package -match 'versionName=([^\r\n]+)') { $Matches[1].Trim() } else { $null }
        $device.installer = if ($package -match 'installerPackageName=([^\s]+)') { $Matches[1] } else { $null }
        $device.debuggable = $package -match '\bDEBUGGABLE\b'
        if ($IncludeInstalledApk -and $device.appInstalled) {
            $paths = Invoke-AdbRead -AdbArguments @('-s', $serial, 'shell', 'pm', 'path', $packageName)
            $baseLine = @($paths -split '\r?\n' | Where-Object { $_ -match '^package:/[A-Za-z0-9_./=~+@-]+/base\.apk$' })
            if ($baseLine.Count -ne 1) { throw "Cannot identify one installed base APK for device-$index." }
            $remotePath = $baseLine[0].Substring('package:'.Length)
            $localPath = Join-Path $deviceDir 'installed-base.apk'
            Invoke-AdbRead -AdbArguments @('-s', $serial, 'pull', $remotePath, $localPath) | Out-Null
            $device.installedBaseApkSha256 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash
            # Split APK versions are described by package.txt; this hashes only the base APK.
        }
    }
    $devices += [pscustomobject]$device
}

$phones = @($devices | Where-Object { $_.eligible -and $_.role -eq 'phone' })
$watches = @($devices | Where-Object { $_.eligible -and $_.role -eq 'watch' })
if ($PhoneSerial) {
    $phones = @($phones | Where-Object serial -EQ $PhoneSerial)
    $watches = @($watches | Where-Object serial -EQ $WatchSerial)
}
$readiness = if ($phones.Count -eq 1 -and $watches.Count -eq 1) {
    if ($phones[0].appInstalled -and $watches[0].appInstalled) {
        if ($phones[0].isEmulator -or $watches[0].isEmulator) { 'emulator_inventory_ready' } else { 'physical_inventory_ready' }
    } else { 'app_missing' }
} elseif (@($devices | Where-Object eligible).Count -eq 0) { 'physical_devices_missing' }
else { 'select_one_physical_phone_and_watch' }
$builds = @()
foreach ($build in @(
    @{ role = 'phone'; path = 'android\app\build\outputs\apk\debug\app-debug.apk' },
    @{ role = 'watch'; path = 'android\wear\build\outputs\apk\debug\wear-debug.apk' }
)) {
    $buildPath = Join-Path $workspaceRoot $build.path
    if (Test-Path -LiteralPath $buildPath -PathType Leaf) {
        $builds += [ordered]@{ role = $build.role; path = $build.path;
            sha256 = (Get-FileHash -LiteralPath $buildPath -Algorithm SHA256).Hash }
    }
}
$report = [ordered]@{
    schemaVersion = 1
    capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    label = $Label
    gitHead = $gitHead.Trim()
    workingTreeChanges = $gitStatus
    readiness = $readiness
    emulatorUseEnabled = [bool]$AllowEmulators
    selectedPhone = if ($phones.Count -eq 1) { $phones[0].serial } else { $null }
    selectedWatch = if ($watches.Count -eq 1) { $watches[0].serial } else { $null }
    devices = $devices
    localDebugBuilds = $builds
    acceptance = 'not_evaluated'
    notes = @('Inventory readiness does not prove pairing, capability, signing compatibility or acceptance.',
        'Local APK hashes do not establish that these APKs are installed; compare installed hashes explicitly.',
        'Collection reads device state and writes only local evidence; it does not install, reset, reboot or grant permissions.')
}
$reportPath = Join-Path $reportRoot 'inventory.json'
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath
Write-Output "Readiness: $readiness"
Write-Output "Evidence: $reportPath"
