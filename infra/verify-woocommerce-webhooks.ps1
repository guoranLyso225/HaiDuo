param([switch]$SimulatePayment,[switch]$QueueOnly)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
$docker=(Get-Command docker -CommandType Application|Select-Object -First 1).Source
function DockerCommand{$saved=$ErrorActionPreference;try{$ErrorActionPreference='Continue';$out=& $docker @args 2>&1|ForEach-Object{"$_"};if($LASTEXITCODE){throw 'Docker verification command failed'};return $out}finally{$ErrorActionPreference=$saved}}
function Sql($q){return ((DockerCommand compose --env-file .env -f infra/compose.yml exec -T postgres psql -U haiduo -d haiduo -At -v ON_ERROR_STOP=1 -c $q)-join "`n").Trim()}
try{
 $base='http://localhost:8088';$csrf=Invoke-RestMethod "$base/api/session/csrf" -SessionVariable session
 Invoke-WebRequest "$base/api/session/login" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body @{username='demo1.admin';password=[IO.File]::ReadAllText((Join-Path $root '.demo-password')).Trim()} -UseBasicParsing|Out-Null
 $me=Invoke-RestMethod "$base/api/session" -WebSession $session;$csrf=Invoke-RestMethod "$base/api/session/csrf" -WebSession $session
 Invoke-WebRequest "$base/api/session/tenant/$($me.memberships[0].tenant_id)" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} -UseBasicParsing|Out-Null
 $stores=Invoke-RestMethod "$base/api/stores" -WebSession $session;$store=@($stores|Where-Object{$_.platform-eq 'WOOCOMMERCE' -and $_.connection_status-eq 'CONNECTED'})|Select-Object -First 1
 if(!$store){throw 'Run the WooCommerce live connection test first'}
 $id=[guid]$store.id
 $before=[int](Sql "select count(*) from webhook_inbox where store_id='$id' and topic='order.updated'")
 $moves=Sql "select count(*) from inventory_movement where tenant_id='$($me.memberships[0].tenant_id)'"
 $deliveryArgs=@();if($QueueOnly){$deliveryArgs+= '--queue-only'}
 $raw=DockerCommand compose --env-file .woo.env -f infra/compose.woocommerce.yml exec -T -u www-data -e "HAIDUO_WOO_STORE_ID=$id" -e "HAIDUO_WOO_MARK_PAID=$SimulatePayment" shop php /opt/haiduo-woo-deliver.php @deliveryArgs
 $delivery=($raw-join "`n")|ConvertFrom-Json
 if($delivery.failures-ne 0){throw 'WooCommerce reported delivery failure'}
 if($QueueOnly){Write-Output 'INFO: queued normal WooCommerce update; waiting up to150seconds for cron'}
 $end=(Get-Date).AddSeconds($(if($QueueOnly){150}else{45}))
 do{$count=[int](Sql "select count(*) from webhook_inbox where store_id='$id' and topic='order.updated' and status='PROCESSED'");if($count-ge ($before+1)){break};Start-Sleep -Seconds 1}while((Get-Date)-lt $end)
 $after=[int](Sql "select count(*) from webhook_inbox where store_id='$id' and topic='order.updated'")
 if($after-ne ($before+1) -or $count-ne $after){throw 'Expected exactly one new processed business event for ten deliveries'}
 if((Sql "select count(*) from inventory_movement where tenant_id='$($me.memberships[0].tenant_id)'")-ne $moves){throw 'Read-only webhook changed inventory'}
 try{$response=Invoke-WebRequest "$base/api/webhooks/woocommerce/$id" -Method Post -ContentType 'application/json' -Body '{"id":12}' -Headers @{'X-WC-Webhook-Signature'='invalid'} -UseBasicParsing;$status=[int]$response.StatusCode}catch{if(!$_.Exception.Response){throw};$status=[int]$_.Exception.Response.StatusCode}
 if($status-ne 401){throw 'Forged signature was not rejected'}
 if($QueueOnly){Write-Output 'PASS: normal WordPress queued update delivered automatically by cron, one processed event, no inventory movement, invalid signature401'}else{Write-Output 'PASS: ten real WooCommerce deliveries, one new processed business event, no inventory movement, invalid signature401'}
}finally{Pop-Location}
