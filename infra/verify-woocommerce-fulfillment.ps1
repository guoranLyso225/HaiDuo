$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
try{
 & ./infra/verify-woocommerce-webhooks.ps1 -SimulatePayment
 if(!$?){throw 'Simulated payment status webhook failed'}
 $base='http://localhost:8088';$c=Invoke-RestMethod "$base/api/session/csrf" -SessionVariable session
 Invoke-WebRequest "$base/api/session/login" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$c.token} -Body @{username='demo1.admin';password=[IO.File]::ReadAllText((Join-Path $root '.demo-password')).Trim()} -UseBasicParsing|Out-Null
 $me=Invoke-RestMethod "$base/api/session" -WebSession $session;$c=Invoke-RestMethod "$base/api/session/csrf" -WebSession $session
 Invoke-WebRequest "$base/api/session/tenant/$($me.memberships[0].tenant_id)" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$c.token} -UseBasicParsing|Out-Null
 function Post($path,$body){$csrf=Invoke-RestMethod "$base/api/session/csrf" -WebSession $session;return Invoke-WebRequest -TimeoutSec 20 "$base$path" -WebSession $session -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body ($body|ConvertTo-Json -Compress) -UseBasicParsing}
 $stores=Invoke-RestMethod "$base/api/stores" -WebSession $session
 $store=$stores|Where-Object{$_.platform-eq 'WOOCOMMERCE' -and $_.connection_status-eq 'CONNECTED'}|Select-Object -First 1
 $orders=Invoke-RestMethod "$base/api/orders" -WebSession $session
 $order=$orders|Where-Object{$_.store-eq $store.name}|Select-Object -First 1
 if(!$order){throw 'No Woo order found'}
 $detail=Invoke-RestMethod "$base/api/orders/$($order.id)" -WebSession $session
 if($detail.order.payment_status-ne 'PAID'){throw 'Expected simulated paid fixture'}
 if($detail.order.status-eq 'NEW'){
  $stocks=Invoke-RestMethod "$base/api/inventory?warehouseId=$($detail.order.warehouse_id)&search=DEMO-BAG" -WebSession $session
  $stock=$stocks|Where-Object{$_.code-eq 'DEMO-BAG'}|Select-Object -First 1
  if(!$stock){throw 'Warehouse stock record missing'}
  if($stock.available-lt 1){Post '/api/inventory/receipts' @{requestKey=$order.id;skuId=$detail.items[0].sku_id;quantity=1;warehouseId=$detail.order.warehouse_id}|Out-Null}
  Post "/api/orders/$($order.id)/reserve" @{}|Out-Null
 }
 for($n=0;$n-lt 10;$n++){Post "/api/orders/$($order.id)/shipment" @{carrier='LOCAL-MOCK';trackingNumber=('WOO-LOCAL-'+$order.external_id)}|Out-Null}
 foreach($fee in @(@('FREIGHT','50.00'),@('PLATFORM','72.00'))){Post "/api/orders/$($order.id)/fees" @{kind=$fee[0];amount=$fee[1];currency='CNY';rate='1';source='LOCAL-WOO test direct cost (not settlement)'}|Out-Null}
 $profits=Invoke-RestMethod "$base/api/profits" -WebSession $session;$profit=@($profits|Where-Object{$_.id-eq $order.id})
 if($profit.Count-ne 1 -or [decimal]$profit[0].contribution-ne [decimal]398){throw 'Expected398 CNY contribution'}
 for($n=0;$n-lt 10;$n++){Post "/api/orders/$($order.id)/profit-confirmations" @{requestKey=$order.id;source='LOCAL-WOO test confirmation (simulated payment only)'}|Out-Null}
 $final=Invoke-RestMethod "$base/api/orders/$($order.id)" -WebSession $session
 if($final.order.status-ne 'SHIPPED' -or @($final.shipments).Count-ne 1){throw 'Expected one shipment'}
 Write-Output 'PASS: real local Woo order imported; simulated-paid webhook, reserve, shipment10, fees,398 CNY and confirmation10; one shipment'
}finally{Pop-Location}
