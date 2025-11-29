param(
    [string]$Version
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-JPackage {
    if ($env:JPACKAGE_PATH -and (Test-Path $env:JPACKAGE_PATH)) {
        return (Resolve-Path $env:JPACKAGE_PATH).Path
    }

    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin" "jpackage.exe"
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Could not locate jpackage. Install JDK 17+ and ensure jpackage.exe is on PATH or set JPACKAGE_PATH."
}

function Resolve-SignTool {
    if ($env:SIGNTOOL_PATH -and (Test-Path $env:SIGNTOOL_PATH)) {
        return (Resolve-Path $env:SIGNTOOL_PATH).Path
    }

    $cmd = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    return $null
}

function Invoke-CodeSign {
    param(
        [string[]]$Targets
    )

    $certPath = $env:SIGNING_CERT_PATH
    if (-not $certPath -or -not $Targets -or $Targets.Count -eq 0) {
        return
    }

    if (-not (Test-Path $certPath)) {
        throw "SIGNING_CERT_PATH is set but the file was not found: $certPath"
    }

    $signTool = Resolve-SignTool
    if (-not $signTool) {
        throw "SIGNING_CERT_PATH is configured but signtool.exe is missing. Install the Windows SDK or set SIGNTOOL_PATH."
    }

    $resolvedCert = (Resolve-Path $certPath).Path
    $timestampUrl = if ($env:SIGNING_TIMESTAMP_URL) { $env:SIGNING_TIMESTAMP_URL } else { "http://timestamp.digicert.com" }
    $password = $env:SIGNING_CERT_PASSWORD

    foreach ($target in $Targets) {
        if (-not (Test-Path $target)) {
            continue
        }
        $resolvedTarget = (Resolve-Path $target).Path
        $args = @("sign", "/fd", "SHA256", "/td", "SHA256", "/tr", $timestampUrl, "/f", $resolvedCert)
        if ($password) {
            $args += @("/p", $password)
        }
        $args += $resolvedTarget
        & $signTool @args
        if ($LASTEXITCODE -ne 0) {
            throw "signtool.exe failed while signing $resolvedTarget"
        }
    }
}

function Get-JavaFxPlatform {
    if ($env:JAVAFX_PLATFORM) {
        return $env:JAVAFX_PLATFORM
    }

    $arch = (Get-CimInstance Win32_OperatingSystem).OSArchitecture
    if ($arch -match "ARM") {
        return "windows-aarch64"
    }
    return "windows-x64"
}

$JavaFxDownloads = Join-Path "target" "javafx"

function Resolve-JavaFxJmods {
    param(
        [string]$Version,
        [string]$CacheRoot
    )

    if ($env:JAVAFX_JMODS -and (Test-Path $env:JAVAFX_JMODS)) {
        return (Resolve-Path $env:JAVAFX_JMODS).Path
    }

    if (-not (Test-Path $CacheRoot)) {
        New-Item -ItemType Directory -Path $CacheRoot | Out-Null
    }

    $JmodsDir = Join-Path $CacheRoot "javafx-jmods-$Version"
    if (Test-Path $JmodsDir) {
        return (Resolve-Path $JmodsDir).Path
    }

    $platform = Get-JavaFxPlatform
    $zipName = "openjfx-$Version`_${platform}_bin-jmods.zip"
    $zipPath = Join-Path $CacheRoot $zipName
    $downloadUrl = "https://download2.gluonhq.com/openjfx/$Version/$zipName"

    Write-Host "Downloading JavaFX $Version jmods ($platform)"
    Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $CacheRoot -Force
    Remove-Item $zipPath -Force

    if (!(Test-Path $JmodsDir)) {
        throw "Failed to extract JavaFX jmods to $JmodsDir"
    }

    return (Resolve-Path $JmodsDir).Path
}

function Resolve-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        return (Resolve-Path $env:JAVA_HOME).Path
    }

    $jpackage = Resolve-JPackage
    $binDir = Split-Path -Parent $jpackage
    $home = Split-Path -Parent $binDir
    if (!(Test-Path $home)) {
        throw "Unable to determine JDK home from jpackage path."
    }
    return (Resolve-Path $home).Path
}

function Set-MejaisJavaOptions {
    param(
        [string]$ConfigPath,
        [string[]]$Options
    )

    if (!(Test-Path $ConfigPath)) {
        return
    }

    $content = Get-Content -Path $ConfigPath -ErrorAction SilentlyContinue
    foreach ($opt in $Options) {
        $line = "java-options=$opt"
        if ($content -notcontains $line) {
            Add-Content -Path $ConfigPath -Value $line
        }
    }
}

$RepoRoot = Resolve-Path (Join-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath "..") -ChildPath "..")
Push-Location $RepoRoot
try {
    [xml]$pom = Get-Content "pom.xml"
    $AppName = "Mejais"
    $PomVersion = $pom.project.version
    $JavaFxVersion = $pom.project.properties.'javafx.version'
    if (-not $PomVersion) {
        throw "Unable to read project.version from pom.xml."
    }
    if (-not $JavaFxVersion) {
        throw "Unable to read javafx.version from pom.xml."
    }

    if (-not $Version) {
        $Version = $PomVersion
    } elseif ($Version -ne $PomVersion) {
        Write-Warning "Script parameter version ($Version) does not match pom.xml version ($PomVersion). The shaded JAR will still use $PomVersion."
    }

    $JarName = "$($pom.project.artifactId)-$PomVersion-shaded.jar"
    $JarPath = Join-Path "target" $JarName

    Write-Host "Building $JarName"
    mvn -q -DskipTests package

    if (!(Test-Path $JarPath)) {
        $JarMatch = Get-ChildItem -Path "target" -Filter "*-shaded.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $JarMatch) {
            throw "No shaded JAR found under target/. Did Maven complete successfully?"
        }
        $JarName = $JarMatch.Name
        $JarPath = $JarMatch.FullName
    }

    $SnapshotPath = Join-Path "data" "snapshot.db"
    if (!(Test-Path $SnapshotPath)) {
        throw "data/snapshot.db is missing. Place the latest snapshot before packaging."
    }

    $WorkingRoot = Join-Path "target" "packaging"
    $AppInput = Join-Path $WorkingRoot "app"
    $TempDir = Join-Path $WorkingRoot "tmp"
    $DistDir = Join-Path "target" "dist"

    Write-Host "Preparing staging directories"
    if (Test-Path $WorkingRoot) {
        Remove-Item -Path $WorkingRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $AppInput | Out-Null
    New-Item -ItemType Directory -Path $TempDir | Out-Null
    if (!(Test-Path $DistDir)) {
        New-Item -ItemType Directory -Path $DistDir | Out-Null
    } else {
        $ExistingImage = Join-Path $DistDir $AppName
        if (Test-Path $ExistingImage) {
            Remove-Item -Path $ExistingImage -Recurse -Force
        }
    }

    Copy-Item -Path $JarPath -Destination (Join-Path $AppInput $JarName)
    Copy-Item -Path "data" -Destination (Join-Path $AppInput "data") -Recurse
    $CertDestination = Join-Path $AppInput "data\riotgames.pem"
    $CertCandidates = @(
        "C:\Riot Games\League of Legends\riotgames.pem"
    )
    if ($env:LOCALAPPDATA) {
        $CertCandidates += (Join-Path $env:LOCALAPPDATA "Riot Games\Riot Client\Config\riotgames.pem")
    }
    foreach ($candidate in $CertCandidates) {
        if ($candidate -and (Test-Path $candidate)) {
            Copy-Item -Path $candidate -Destination $CertDestination -Force
            break
        }
    }

    $IconPath = Resolve-Path "src/main/resources/org/example/images/logo/output.ico"
    $JavaFxJmods = Resolve-JavaFxJmods -Version $JavaFxVersion -CacheRoot $JavaFxDownloads
    $JPackageExe = Resolve-JPackage
    $JdkHome = Resolve-JdkHome
    $JlinkExe = Join-Path -Path (Join-Path -Path $JdkHome -ChildPath "bin") -ChildPath "jlink.exe"
    if (!(Test-Path $JlinkExe)) {
        throw "jlink executable not found under $JdkHome."
    }
    $JdkJmods = Join-Path -Path $JdkHome -ChildPath "jmods"
    if (!(Test-Path $JdkJmods)) {
        throw "Could not locate JDK jmods at $JdkJmods."
    }

    $RuntimeImage = Join-Path $WorkingRoot "runtime"
    if (Test-Path $RuntimeImage) {
        Remove-Item -Path $RuntimeImage -Recurse -Force
    }

    $ModulePath = "$JdkJmods;$JavaFxJmods"
    $RuntimeModules = "java.se,javafx.controls,javafx.fxml,javafx.swing,jdk.crypto.ec"
    Write-Host "Assembling custom runtime image"
    $JlinkArgs = @(
        "--module-path", $ModulePath,
        "--add-modules", $RuntimeModules,
        "--strip-java-debug-attributes",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2",
        "--output", $RuntimeImage
    )
    & $JlinkExe @JlinkArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jlink failed to create the runtime image."
    }

    Write-Host "Using jpackage at $JPackageExe"

    $CommonArgs = @(
        "--name", $AppName,
        "--input", $AppInput,
        "--main-jar", $JarName,
        "--app-version", $Version,
        "--vendor", "rguldborg",
        "--description", "League of Legends draft assistant",
        "--icon", $IconPath,
        "--temp", $TempDir,
        "--runtime-image", $RuntimeImage
    )

    Write-Host "Creating portable app image"
    $ImageArgs = $CommonArgs + @(
        "--type", "app-image",
        "--dest", $DistDir
    )
    & $JPackageExe @ImageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage app-image build failed."
    }

    $LockfileOverride = "-DLEAGUE_LOCKFILE_PATH=""C:\Riot Games\League of Legends\lockfile"""
    $CfgPath = Join-Path (Join-Path $DistDir $AppName) "app\Mejais.cfg"
    # Also write the override into a plain text file that the app can read as a fallback.
    if (Test-Path $CfgPath) {
        $overrideFile = Join-Path (Split-Path $CfgPath -Parent) "data\lockfile.override"
        $overrideValue = "C:\Riot Games\League of Legends\lockfile"
        New-Item -ItemType Directory -Path (Split-Path $overrideFile -Parent) -Force | Out-Null
        Set-Content -Path $overrideFile -Value $overrideValue
        Set-MejaisJavaOptions -ConfigPath $CfgPath -Options @($LockfileOverride)
    }

    Remove-Item -Path $TempDir -Recurse -Force
    New-Item -ItemType Directory -Path $TempDir | Out-Null

    $AppImagePath = Join-Path $DistDir $AppName
    $AppExecutable = Join-Path $AppImagePath "$AppName.exe"
    Invoke-CodeSign -Targets $AppExecutable
    $PortableZip = Join-Path $DistDir "$AppName-$Version-portable.zip"
    if (Test-Path $PortableZip) {
        Remove-Item $PortableZip -Force
    }
    Compress-Archive -Path $AppImagePath -DestinationPath $PortableZip

    Write-Host "Creating Windows installer"
    $InstallerArgs = @(
        "--type", "exe",
        "--name", $AppName,
        "--app-version", $Version,
        "--vendor", "rguldborg",
        "--description", "League of Legends draft assistant",
        "--icon", $IconPath,
        "--app-image", $AppImagePath,
        "--dest", $DistDir,
        "--temp", $TempDir,
        "--win-shortcut",
        "--win-menu",
        "--win-menu-group", $AppName,
        "--win-dir-chooser",
        "--win-per-user-install"
    )
    & $JPackageExe @InstallerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage installer build failed."
    }

    $Installer = Join-Path $DistDir "$AppName-$Version.exe"
    Invoke-CodeSign -Targets $Installer
    Write-Host ""
    Write-Host "Portable zip : $PortableZip"
    if (Test-Path $Installer) {
        Write-Host "Installer     : $Installer"
    } else {
        Write-Warning "Expected installer file not found under $DistDir"
    }
}
finally {
    Pop-Location
}
