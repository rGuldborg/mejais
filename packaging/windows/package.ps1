param(
    [string] = "1.3.0"
)

Continue = "Stop"

Write-Host "Building Mejais  via javafx:jpackage"
mvn -q -DskipTests javafx:jpackage

 = Join-Path "target" "dist"
if (!(Test-Path )) {
    throw "Could not find target\\dist output from javafx:jpackage."
}
 = Get-Date -Format "yyyyMMddHHmmss"
 = Get-ChildItem -Path  -Filter "*.exe" | Select-Object -First 1
if () {
     = "Mejais--.exe"
    Copy-Item .FullName (Join-Path  ) -Force
    Write-Host "Installer created in target\\dist\\"
} else {
    Write-Warning "No EXE produced by javafx:jpackage."
}
