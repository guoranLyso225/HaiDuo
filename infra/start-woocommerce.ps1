param([ValidatePattern('^[a-z0-9-]{1,40}$')][string]$ProjectName='haiduo-woo')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
 if(!(Test-Path .woo.env)){
  $lines=foreach($key in @('WOO_DB_PASSWORD','WOO_DB_ROOT_PASSWORD','WOO_ADMIN_PASSWORD')){$key+'='+[guid]::NewGuid().ToString('N')}
  [IO.File]::WriteAllLines((Join-Path $root '.woo.env'),$lines)
 }
 $docker=(Get-Command docker -CommandType Application|Select-Object -First 1).Source
 $compose=@('compose','--env-file','.woo.env','-f','infra/compose.woocommerce.yml','-p',$ProjectName)
 $saved=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  & $docker @compose up -d
  if($LASTEXITCODE){throw 'Store startup failed'}
  $deadline=(Get-Date).AddSeconds(60)
  do{& $docker @compose exec -T shop test -f /var/www/html/wp-config.php|Out-Null;$ready=($LASTEXITCODE-eq 0);if(!$ready){Start-Sleep -Seconds 1}}while(!$ready -and (Get-Date)-lt $deadline)
  if(!$ready){throw 'WordPress files not ready after60seconds'}
  & $docker @compose exec -T -u www-data shop php /opt/haiduo-woo-setup.php
  if($LASTEXITCODE){throw 'Store initialization failed'}
 }finally{$ErrorActionPreference=$saved}
 $port=if($env:WOO_HTTP_PORT){$env:WOO_HTTP_PORT}else{'8090'}
 $products=Invoke-RestMethod -TimeoutSec 20 "http://localhost:$port/wp-json/wc/store/v1/products"
 if(!@($products|Where-Object{$_.sku-eq 'DEMO-BAG'}).Count){throw 'Demo product missing from Store API'}
 Write-Output "PASS: Store API product verified. Admin: http://localhost:$port/wp-admin/ ; user: haiduo_admin ; password: local .woo.env WOO_ADMIN_PASSWORD"
}finally{Pop-Location}
