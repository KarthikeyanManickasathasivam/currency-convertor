# Build the Spring Boot backend (skip tests for fast local build)
$env:JAVA_HOME = "C:\Program Files\OpenLogic\jdk-21.0.3.9-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;C:\apache-maven-3.9.6\bin;$env:PATH"

Write-Host "Java version:" -ForegroundColor Cyan
java -version

Write-Host "`nMaven version:" -ForegroundColor Cyan
mvn -version

Write-Host "`nBuilding backend (skipping tests)..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
mvn clean package -DskipTests -Dspring.profiles.active=local
