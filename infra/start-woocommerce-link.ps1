$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try{
 if(!(Test-Path .env)){throw 'Configure the existing HaiDuo .env first'}
 $line=Get-Content .env|Where-Object{$_ -like 'TOKEN_ENCRYPTION_KEY=*'}|Select-Object -First 1
 $key=if($line){($line-split '=',2)[1]}else{''}
 try{$valid=([Convert]::FromBase64String($key).Length-eq 32)}catch{$valid=$false}
 if(!$valid){throw 'Set TOKEN_ENCRYPTION_KEY to a persistent base64-encoded random32-byte key before connecting; never replace an existing token encryption key'}
 & ./infra/start-woocommerce.ps1
 if(!$?){throw 'WooCommerce setup failed'}
 $docker=(Get-Command docker -CommandType Application|Select-Object -First 1).Source
 $saved=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  $credentials=& $docker compose --env-file .woo.env -f infra/compose.woocommerce.yml exec -T -u www-data shop php /opt/haiduo-woo-setup.php --credentials
  if($LASTEXITCODE){throw 'Failed to prepare read-only test credentials'}
  try{$object=($credentials-join "`n")|ConvertFrom-Json}catch{throw 'Invalid credential response; details suppressed'}
  [IO.File]::WriteAllText((Join-Path $root '.woo-credentials.json'),($object|ConvertTo-Json))
  & $docker compose --env-file .env -f infra/compose.yml -f infra/compose.woo-link.yml up -d --build --wait --wait-timeout 180
  if($LASTEXITCODE){throw 'HaiDuo linked services failed to start'}
  & $docker compose --env-file .woo.env -f infra/compose.woocommerce.yml --profile webhooks up -d cron
  if($LASTEXITCODE){throw 'WooCommerce webhook worker failed to start'}
 }finally{$ErrorActionPreference=$saved}
 Write-Output 'Ready: HaiDuo localhost:8088; Woo localhost:8090. Configure the Woo store as ADMIN using local .woo-credentials.json; do not commit credentials.'
}finally{Pop-Location}
