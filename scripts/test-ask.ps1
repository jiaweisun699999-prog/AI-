$bodyObj = @{
  question = "请总结新能源车行业供需格局"
  domain   = "ashare"
  topK     = 5
  mode     = "fast"  # fast | deep
}

$json = $bodyObj | ConvertTo-Json -Depth 6
Write-Host "Request JSON:"
Write-Host $json
Write-Host ""

$resp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ask" -ContentType "application/json" -Body $json
$resp | ConvertTo-Json -Depth 10

