param([ValidatePattern('^[a-z0-9-]{1,32}$')][string]$RunId='boot4-seed')
$pgName='haiduo-'+$RunId+'-pg'
$apiName='haiduo-'+$RunId+'-api'
$ErrorActionPreference='Stop'
function Docker { $saved=$ErrorActionPreference;try{$ErrorActionPreference='Continue';& docker.exe @args 2>&1|ForEach-Object{"$_"};if($LASTEXITCODE){throw "Docker failed $LASTEXITCODE"}}finally{$ErrorActionPreference=$saved}}
function Sql($query){$r=Docker exec $pgName psql -U haiduo -d haiduo -At -v ON_ERROR_STOP=1 -c $query;return ($r -join "`n").Trim()}
function Ready($name){$end=(Get-Date).AddSeconds(60);do{$state=Docker inspect --format '{{.State.Health.Status}}' $name;if($state-eq 'healthy'){return};Start-Sleep -Seconds 1}while((Get-Date)-lt $end);throw "Health timeout: $name"}
$existing=Docker ps -a --format '{{.Names}}'
if($existing -contains $pgName -or $existing -contains $apiName){throw 'Seed check containers already exist; inspect before repeating'}
try{
 $env:POSTGRES_PASSWORD=((Get-Content .env|Where-Object{$_ -like 'DB_PASSWORD=*'}|Select-Object -First 1)-split '=',2)[1]
 Docker run -d --name $pgName --network haiduo-dev_default -e POSTGRES_PASSWORD -e POSTGRES_USER=haiduo -e POSTGRES_DB=haiduo --health-cmd 'pg_isready -U haiduo -d haiduo' --health-interval 2s --health-timeout 2s postgres:17.6|Out-Null
 Ready $pgName
 Docker run -d --name $apiName --network haiduo-dev_default --env-file .env -e "DATABASE_URL=jdbc:postgresql://${pgName}:5432/haiduo" -e REDIS_HOST=redis -e MESSAGING_ENABLED=false -e AI_ENABLED=false -e AI_DEMO=false -e SEARCH_URL= -e VECTOR_DATABASE_URL= -e TRACING_ENABLED=false -e SPRING_PROFILES_ACTIVE=demo -e COOKIE_SECURE=false --health-cmd 'java -cp /app/health cn.haiduo.HealthProbe' --health-interval 2s --health-timeout 5s haiduo-api:0.2.0|Out-Null
 Ready $apiName
 if((Sql 'select count(*) from flyway_schema_history where success')-ne '20'){throw 'Expected 20 migrations'}
 foreach($pair in @(@('tenant',2),@('membership',8),@('sku',2),@('sales_order',2),@('exchange_rate_snapshot',6))){if((Sql ('select count(*) from '+$pair[0]))-ne [string]$pair[1]){throw ('Unexpected seed count '+$pair[0])}}
 Write-Output 'PASS: Boot4 empty database, 20 migrations, 2 tenants, 8 roles/accounts, 2 SKUs/orders and 6 rate snapshots'
 Sql "update inventory set on_hand=97"|Out-Null
 Docker restart $apiName|Out-Null
 Ready $apiName
 if((Sql 'select min(on_hand) from inventory')-ne '97'){throw 'Demo restart reset inventory'}
 if((Sql 'select count(*) from exchange_rate_snapshot')-ne '6'){throw 'Demo restart duplicated snapshots'}
 Write-Output 'PASS: restart preserves inventory97 and immutable snapshot count6'
}finally{$env:POSTGRES_PASSWORD=$null;Docker stop $apiName $pgName|Out-Null}
