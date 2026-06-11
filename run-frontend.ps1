# Run Angular frontend in dev mode (proxies /api/* to localhost:8080)
Set-Location "$PSScriptRoot\frontend"

Write-Host "Starting Angular dev server on http://localhost:4200" -ForegroundColor Green
Write-Host "API calls proxied to http://localhost:8080" -ForegroundColor Yellow
Write-Host ""

npx ng serve --proxy-config proxy.conf.json
