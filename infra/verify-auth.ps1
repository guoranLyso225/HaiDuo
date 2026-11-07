param([string]$BaseUrl='http://localhost:8084',[Parameter(Mandatory=$true)][string]$Password)
$ErrorActionPreference='Stop'
function Login([string]$User){
 $token=Invoke-RestMethod "$BaseUrl/api/session/csrf" -SessionVariable localSession
 Invoke-WebRequest "$BaseUrl/api/session/login" -Method Post -WebSession $localSession -Headers @{'X-CSRF-TOKEN'=$token.token} -Body @{username=$User;password=$Password} -UseBasicParsing | Out-Null
 return $localSession
}
function ExpectStatus([scriptblock]$Request,[int]$Expected){
 try{$r=& $Request;$status=[int]$r.StatusCode}catch{if(!$_.Exception.Response){throw};$status=[int]$_.Exception.Response.StatusCode}
 if($status -ne $Expected){throw "Expected $Expected, got $status"}
}
$admin=Login 'demo1.admin'
$other=Login 'demo2.admin'
$a=Invoke-RestMethod "$BaseUrl/api/session" -WebSession $admin
$b=Invoke-RestMethod "$BaseUrl/api/session" -WebSession $other
$tenant=$a.memberships[0].tenant_id
$foreignTenant=$b.memberships[0].tenant_id
$token=Invoke-RestMethod "$BaseUrl/api/session/csrf" -WebSession $admin
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/session/tenant/$tenant" -Method Post -WebSession $admin -UseBasicParsing} 403
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/session/tenant/$foreignTenant" -Method Post -WebSession $admin -Headers @{'X-CSRF-TOKEN'=$token.token} -UseBasicParsing} 403
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/session/tenant/$tenant" -Method Post -WebSession $admin -Headers @{'X-CSRF-TOKEN'=$token.token} -UseBasicParsing} 200
$dashboard=Invoke-WebRequest "$BaseUrl/api/dashboard" -WebSession $admin -UseBasicParsing
if($dashboard.Content -notmatch ([string][char]0x975e+[char]0x53d6+[char]0x6d88)){throw 'UTF-8 response decoding failed'}
$warehouse=Login 'demo1.warehouse'
$token=Invoke-RestMethod "$BaseUrl/api/session/csrf" -WebSession $warehouse
Invoke-WebRequest "$BaseUrl/api/session/tenant/$tenant" -Method Post -WebSession $warehouse -Headers @{'X-CSRF-TOKEN'=$token.token} -UseBasicParsing | Out-Null
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/profits" -WebSession $warehouse -UseBasicParsing} 403
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/products" -Method Post -WebSession $warehouse -Headers @{'X-CSRF-TOKEN'=$token.token} -ContentType 'application/json' -Body '{"code":"FORBIDDEN","name":"test","cost":"1"}' -UseBasicParsing} 403
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/session/logout" -Method Post -WebSession $warehouse -Headers @{'X-CSRF-TOKEN'=$token.token} -UseBasicParsing} 204
ExpectStatus {Invoke-WebRequest "$BaseUrl/api/session" -WebSession $warehouse -UseBasicParsing} 401
Write-Output 'PASS: CSRF, foreign tenant, role restrictions, logout, UTF-8'
