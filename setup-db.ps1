# Create the PostgreSQL database for local development
# Run this ONCE after installing PostgreSQL

$pgBin = "C:\Program Files\PostgreSQL\16\bin"
$env:PGPASSWORD = "postgres"

Write-Host "Creating database 'currency_exchange'..." -ForegroundColor Cyan

& "$pgBin\psql.exe" -U postgres -c "CREATE DATABASE currency_exchange;" 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "Database created successfully." -ForegroundColor Green
} else {
    Write-Host "Database may already exist (that's OK)." -ForegroundColor Yellow
}

Write-Host "`nVerifying connection..." -ForegroundColor Cyan
& "$pgBin\psql.exe" -U postgres -d currency_exchange -c "SELECT version();" 2>&1
