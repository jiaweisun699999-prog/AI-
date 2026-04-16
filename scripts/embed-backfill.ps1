$body = @{
  domain = "ashare"
  limit  = 200
} | ConvertTo-Json -Depth 6

Write-Host "POST /embed/backfill payload:"
Write-Host $body
Write-Host ""

(Invoke-WebRequest -Method Post -Uri "http://localhost:8080/embed/backfill" -ContentType "application/json" -Body $body).Content

