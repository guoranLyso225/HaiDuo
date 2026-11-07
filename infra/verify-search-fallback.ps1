param([string]$BaseUrl='http://localhost:8088',[switch]$ExpectAiDisabled)
$ErrorActionPreference='Stop'
$c=Invoke-RestMethod "$BaseUrl/api/session/csrf" -SessionVariable session
Invoke-WebRequest "$BaseUrl/api/session/login" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$c.token} -Body @{username='demo1.admin';password=[IO.File]::ReadAllText((Join-Path $pwd '.demo-password')).Trim()} -UseBasicParsing|Out-Null
$me=Invoke-RestMethod "$BaseUrl/api/session" -WebSession $session
$c=Invoke-RestMethod "$BaseUrl/api/session/csrf" -WebSession $session
Invoke-WebRequest "$BaseUrl/api/session/tenant/$($me.memberships[0].tenant_id)" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$c.token} -UseBasicParsing|Out-Null
$r=Invoke-RestMethod "$BaseUrl/api/search?q=DEMO" -WebSession $session
if($r.engine-ne 'DATABASE'){throw 'Expected database fallback'}
if($r.items.Count-lt 1){throw 'Expected demo products'}
Write-Output 'PASS: unavailable search falls back to tenant-scoped database query'

if($ExpectAiDisabled){$status=Invoke-RestMethod "$BaseUrl/api/ai/status" -WebSession $session;if($status.mode-ne 'DISABLED'){throw 'Expected AI disabled'};Invoke-RestMethod "$BaseUrl/api/orders?size=1" -WebSession $session|Out-Null;Write-Output 'PASS: AI explicitly disabled and core orders available'}
