param([string]$BaseUrl='http://localhost:8088')
$ErrorActionPreference='Stop'
$compose=@('compose','--env-file','.env','-f','infra/compose.yml')
function InvokeDocker { $saved=$ErrorActionPreference;try{$ErrorActionPreference="Continue";& docker @args 2>&1 | ForEach-Object { "$_" };if($LASTEXITCODE){throw "Docker command failed: $LASTEXITCODE"}}finally{$ErrorActionPreference=$saved} }
function Sql([string]$q){$r=InvokeDocker @compose exec -T postgres psql -U haiduo -d haiduo -At -v ON_ERROR_STOP=1 -c $q;if($LASTEXITCODE){throw 'SQL verification failed'};return ($r -join "`n").Trim()}
function Login([string]$user){$c=Invoke-RestMethod -TimeoutSec 15 "$BaseUrl/api/session/csrf" -SessionVariable session;Invoke-WebRequest -TimeoutSec 15 "$BaseUrl/api/session/login" -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$c.token} -Body @{username=$user;password=[IO.File]::ReadAllText((Join-Path $pwd '.demo-password')).Trim()} -UseBasicParsing|Out-Null;$me=Invoke-RestMethod -TimeoutSec 15 "$BaseUrl/api/session" -WebSession $session;$c=Invoke-RestMethod -TimeoutSec 15 "$BaseUrl/api/session/csrf" -WebSession $session;Invoke-WebRequest -TimeoutSec 15 "$BaseUrl/api/session/tenant/$($me.memberships[0].tenant_id)" -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$c.token} -UseBasicParsing|Out-Null;return $session}
function Post($session,[string]$path,$body=$null){$c=Invoke-RestMethod -TimeoutSec 15 "$BaseUrl/api/session/csrf" -WebSession $session;Invoke-WebRequest -TimeoutSec 15 "$BaseUrl$path" -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$c.token} -ContentType 'application/json' -Body $body -UseBasicParsing}
function Until([scriptblock]$check,[string]$name){$end=(Get-Date).AddSeconds(50);do{if(& $check){Write-Output "PASS: $name";return};Start-Sleep -Seconds 1}while((Get-Date)-lt $end);throw "Timeout: $name"}
function Status([scriptblock]$request,[int]$expected){try{$code=[int](& $request).StatusCode}catch{if(!$_.Exception.Response){throw};$code=[int]$_.Exception.Response.StatusCode};if($code-ne $expected){throw "Expected $expected, got $code"}}
$admin=Login 'demo1.admin';$tenant=(Invoke-RestMethod -TimeoutSec 15 "$BaseUrl/api/session" -WebSession $admin).memberships[0].tenant_id
$event=[guid]::NewGuid().ToString();$store=[guid]::NewGuid().ToString();$constraint='stage2_fault_'+$event.Replace('-','');$name='Recovery-'+$event
try{
 Sql "BEGIN; INSERT INTO store VALUES('$store','$tenant','$name','MOCK','MOCK'); ALTER TABLE sync_job ADD CONSTRAINT $constraint CHECK(event_id <> '$event'::uuid OR status <> 'COMPLETED'); INSERT INTO outbox_event(id,tenant_id,kind,target_id) VALUES('$event','$tenant','STORE_SYNC','$store'); COMMIT;"|Out-Null
 Until {(Sql "SELECT count(*) FROM sync_job WHERE event_id='$event' AND status='FAILED'")-eq '1'} 'consumer failure recorded'
 Start-Sleep -Seconds 5
 if((Sql "SELECT count(*) FROM processed_event WHERE event_id='$event'")-ne '0'){throw 'Failed consumer committed dedup'}
 if((Sql "SELECT count(*) FROM sales_order WHERE tenant_id='$tenant' AND store='$name'")-ne '0'){throw 'Failed consumer committed order'}
 Sql "ALTER TABLE sync_job DROP CONSTRAINT $constraint"|Out-Null
 $ops=Login 'demo1.operations';$other=Login 'demo2.admin'
 Status {Post $ops "/api/sync/$event/replay"} 403;Status {Post $other "/api/sync/$event/replay"} 404
 Post $admin "/api/sync/$event/replay"|Out-Null
 Until {(Sql "SELECT count(*) FROM sync_job WHERE event_id='$event' AND status='COMPLETED'")-eq '1'} 'authorized replay recovered'
 if((Sql "SELECT count(*) FROM sales_order WHERE tenant_id='$tenant' AND store='$name'")-ne '1'){throw 'Replay order count incorrect'}
 Status {Post $admin "/api/sync/$event/replay"} 409
 Write-Output 'PASS: rollback, replay role/tenant restrictions and completed-event rejection'
 $restart=[guid]::NewGuid().ToString()
 InvokeDocker @compose stop rabbitmq|Out-Null;if($LASTEXITCODE){throw 'Broker stop failed'}
 Sql "INSERT INTO outbox_event(id,tenant_id,kind,target_id) VALUES('$restart','$tenant','RECOVERY_PROBE','$store')"|Out-Null
 Until {(Sql "SELECT attempts FROM outbox_event WHERE id='$restart'")-match '^[1-5]$'} 'broker outage persisted retry'
 InvokeDocker @compose up -d --no-deps --force-recreate api|Out-Null;if($LASTEXITCODE){throw 'API restart failed'}
 InvokeDocker @compose start rabbitmq|Out-Null
 InvokeDocker @compose up -d --wait --wait-timeout 60|Out-Null;if($LASTEXITCODE){throw 'Stack recovery failed'}
 Until {(Sql "SELECT count(*) FROM processed_event WHERE event_id='$restart'")-eq '1'} 'physical API/broker restart recovered pending delivery'
 if((Sql "SELECT count(*) FROM sync_job WHERE event_id='$restart' AND status='COMPLETED'")-ne '1'){throw 'Recovery job count incorrect'}
 Until {try{(Invoke-WebRequest -TimeoutSec 15 "$BaseUrl/api/sync" -WebSession $admin -UseBasicParsing).StatusCode-eq 200}catch{$false}} 'Redis session and proxy after API replacement'
 $image=Sql "SELECT image_file_id FROM product WHERE tenant_id='$tenant' AND image_file_id IS NOT NULL LIMIT 1"
 if($image){$r=Invoke-WebRequest -TimeoutSec 15 "$BaseUrl/api/files/$image" -WebSession $admin -UseBasicParsing;if($r.StatusCode-ne 200){throw 'Image did not survive restart'};Write-Output 'PASS: persistent image and Redis session after API restart'}
 Status {Invoke-WebRequest -TimeoutSec 15 "$BaseUrl/api/webhooks/shopify" -Method Post -Body '{}' -ContentType 'application/json' -UseBasicParsing} 503
 Write-Output 'PASS: unconfigured Shopify webhook explicitly unavailable (not CSRF rejected)'
}finally{Sql "ALTER TABLE sync_job DROP CONSTRAINT IF EXISTS $constraint"|Out-Null;InvokeDocker @compose start rabbitmq|Out-Null}
