# Run the Spring Boot backend with local profile
$env:JAVA_HOME = "C:\Program Files\OpenLogic\jdk-21.0.3.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Use the JAR in target/ (run build-backend.ps1 first if you want a fresh compile)
$jar = "$PSScriptRoot\target\currency-exchange-1.0.0.jar"
if (-not (Test-Path $jar)) {
    Write-Error "JAR not found: $jar — run build-backend.ps1 first"
    exit 1
}

Write-Host "Starting Currency Exchange backend on http://localhost:8080" -ForegroundColor Green
Write-Host "Swagger UI: http://localhost:8080/swagger-ui.html" -ForegroundColor Green
Write-Host "Profile: local (in-memory OTP + token blacklist, no Redis/SES)" -ForegroundColor Yellow
Write-Host ""

& "$env:JAVA_HOME\bin\java.exe" `
    -jar $jar `
    --spring.profiles.active=local
