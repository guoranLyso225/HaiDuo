param([string]$BaseUrl='http://localhost:8084',[Parameter(Mandatory=$true)][string]$Password)
$ErrorActionPreference='Stop'
function ConnectTenant([string]$User){
 $t=Invoke-RestMethod "$BaseUrl/api/session/csrf" -SessionVariable session
 Invoke-WebRequest "$BaseUrl/api/session/login" -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$t.token} -Body @{username=$User;password=$Password} -UseBasicParsing | Out-Null
 $info=Invoke-RestMethod "$BaseUrl/api/session" -WebSession $session
 $t=Invoke-RestMethod "$BaseUrl/api/session/csrf" -WebSession $session
 Invoke-WebRequest ($BaseUrl+'/api/session/tenant/'+$info.memberships[0].tenant_id) -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$t.token} -UseBasicParsing | Out-Null
 return @{Session=$session;Token=$t.token}
}
function Send($Context,[string]$Path,$Body){Invoke-RestMethod ($BaseUrl+$Path) -Method Post -WebSession $Context.Session -Headers @{'X-CSRF-TOKEN'=$Context.Token} -ContentType 'application/json' -Body ($Body|ConvertTo-Json)}
$a=ConnectTenant 'demo1.admin';$b=ConnectTenant 'demo2.admin'
$code='ISOLATION-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$created=Send $a '/api/products' @{code=$code;name=$code;cost='200.00'}
$catalog=Invoke-RestMethod "$BaseUrl/api/catalog/products/$($created.id)" -WebSession $a.Session
if($catalog.id -ne $created.id){throw 'JPA catalog lookup failed'}
try{Invoke-WebRequest "$BaseUrl/api/catalog/products/$($created.id)" -WebSession $b.Session -UseBasicParsing | Out-Null;throw 'Foreign catalog accessible'}catch{if(!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404){throw}}
$body=@{csv="store,order_no,sku,quantity,net,currency,rate`nmock,$code,$code,1,100.00,USD,7.2"}
for($n=0;$n -lt 10;$n++){
 $r=Send $a '/api/orders/import' $body
 if(($n -eq 0 -and $r.imported -ne 1) -or ($n -gt 0 -and ($r.imported -ne 0 -or $r.duplicates -ne 1))){throw 'Import idempotency failed'}
}
$orders=Invoke-RestMethod "$BaseUrl/api/orders" -WebSession $a.Session
$matched=@($orders | ForEach-Object {foreach($order in $_){if($order.external_id -eq $code){$order}}})
if($matched.Count -ne 1){throw 'Expected exactly one order'}
$id=$matched[0].id
$detail=Invoke-RestMethod "$BaseUrl/api/orders/$id" -WebSession $a.Session
if($detail.items.Count -ne 1){throw 'Duplicate order items'}
$foreign=Invoke-RestMethod "$BaseUrl/api/orders" -WebSession $b.Session
if(($foreign|ConvertTo-Json -Depth 5) -match $code){throw 'Foreign order leaked into list'}
try{Invoke-WebRequest "$BaseUrl/api/orders/$id" -WebSession $b.Session -UseBasicParsing | Out-Null;throw 'Foreign detail accessible'}catch{if(!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404){throw}}
try{Send $b "/api/orders/$id/fees" @{kind='FREIGHT';amount='1.00'};throw 'Foreign fee write allowed'}catch{if(!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404){throw}}
Write-Output 'PASS: 10 imports, one order/item, foreign list/detail/fee isolation'
