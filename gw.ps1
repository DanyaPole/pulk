# Runs Gradle with the tools in C:\Android and prints only what matters.
# Usage: .\gw.ps1 testDebugUnitTest assembleDebug
$env:JAVA_HOME = 'C:\Android\jdk17'; $env:ANDROID_HOME = 'C:\Android\sdk'; $env:PATH = "C:\Android\jdk17\bin;$env:PATH"
$log = Join-Path $PSScriptRoot 'build\gw.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$tasks = $args -join ' '
Push-Location $PSScriptRoot
cmd /c "cd /d $PSScriptRoot && $PSScriptRoot\gradlew.bat --no-daemon $tasks > $log 2>&1"
$code = $LASTEXITCODE
Pop-Location
$txt = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($log))
($txt -split "`r?`n" | Where-Object { $_ -match '^BUILD|^e: |^w: .*(unused|never)|What went wrong|tests completed' } | Select-Object -First 25) -join "`n"
$res = Join-Path $PSScriptRoot 'app\build\test-results\testDebugUnitTest'
if (Test-Path $res) {
    Get-ChildItem $res -Filter *.xml | ForEach-Object {
        $x = [xml](Get-Content $_.FullName -Raw -Encoding UTF8)
        '{0}: {1} tests, {2} failed' -f $_.BaseName.Replace('TEST-app.pulk.', ''), $x.testsuite.tests, ([int]$x.testsuite.failures + [int]$x.testsuite.errors)
        $x.testsuite.testcase | Where-Object { $_.failure -or $_.error } | ForEach-Object {
            $f = if ($_.failure) { $_.failure } else { $_.error }
            '  FAIL ' + $_.name + ': ' + $f.message + ' @ ' + (($f.'#text' -split "`n" | Select-String 'app\.pulk\.' | Select-Object -First 1).Line.Trim())
        }
        $x.testsuite.'system-out'.'#cdata-section' -split "`n" | Where-Object { $_ -match '=|ms' } | ForEach-Object { '  ' + $_ }
    }
}
exit $code
