$body = @{
  domain    = "ashare"
  source    = "local"
  folder    = "/inbox"
  recursive = $true
} | ConvertTo-Json -Depth 6

Write-Host "POST /ingest/run payload:"
Write-Host $body
Write-Host ""

(Invoke-WebRequest -Method Post -Uri "http://localhost:8080/ingest/run" -ContentType "application/json" -Body $body).Content

