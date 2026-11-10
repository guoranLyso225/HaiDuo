param([ValidatePattern('^[a-z0-9-]{1,32}$')][string]$RunId=('drill-'+(Get-Date -Format 'yyyyMMdd-HHmmss')))
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
$dockerCommand=Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue|Select-Object -First 1
if(!$dockerCommand){$dockerCommand=Get-Command docker -CommandType Application -ErrorAction Stop|Select-Object -First 1}
[string]$dockerExecutable=$dockerCommand.Source
if([string]::IsNullOrWhiteSpace($dockerExecutable)){throw 'Docker executable not found'}
$compose=@('compose','--env-file','.env','-f','infra/compose.yml')
function Docker {
 $saved=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  $output=@(& $dockerExecutable @args 2>&1|ForEach-Object{"$_"})
  $code=$LASTEXITCODE
  if($code){
   # Never echo full arguments (they can contain environment credentials).
   $detail=($output|Select-Object -Last 8)-join "`n"
   foreach($entry in @($sourceEnvironment)){
    if($entry -match '^[^=]*(PASSWORD|SECRET|TOKEN|KEY)[^=]*=(.+)$' -and $Matches[2].Length -ge 4){$detail=$detail.Replace($Matches[2],'[REDACTED]')}
   }
   throw "Docker command failed ($code), phase=$phase, operation=$($args[0]): $detail"
  }
  return $output
 }finally{$ErrorActionPreference=$saved}
}
function Sql($container,$query){return ((Docker exec $container psql -U haiduo -d haiduo -At -v ON_ERROR_STOP=1 -c $query)-join "`n").Trim()}
function Counts($container){
 $result=[ordered]@{}
 $query="select tablename || '|' || (xpath('/row/c/text()',query_to_xml(format('select count(*) as c from public.%I',tablename),false,true,'')))[1]::text from pg_tables where schemaname='public' order by tablename"
 foreach($line in ((Sql $container $query)-split "`n")){$pair=$line.Split('|');if($pair.Count-ne 2){throw 'Unexpected table count result'};$result[$pair[0]]=[long]$pair[1]}
 return $result
}
$target='haiduo-restore-'+$RunId
$restoreApi=$target+'-api'
$restoreFiles=$target+'-files'
$folder=Join-Path $root ('backups/'+$RunId)
$resume=$false;$created=$false;$passed=$false
$temp='/tmp/haiduo-'+[guid]::NewGuid().ToString('N')+'.dump'
$oldPassword=$env:POSTGRES_PASSWORD
$oldDbPassword=$env:DB_PASSWORD
$appCreated=$false
$clock=[Diagnostics.Stopwatch]::StartNew()
$phase='source inspection'
try{
 Docker info --format '{{.ServerVersion}}'|Out-Null
 if(Test-Path -LiteralPath $folder){throw 'Backup directory exists; use a new RunId'}
 if((Docker ps -a --format '{{.Names}}') -contains $target){throw 'Restore container exists; use a new RunId'}
 if((Docker ps -a --format '{{.Names}}') -contains $restoreApi){throw 'Restore API exists; use a new RunId'}
 if((Docker volume ls --format '{{.Name}}') -contains $restoreFiles){throw 'Restore files volume exists; use a new RunId'}
 $pg=((Docker @compose ps -q postgres)-join '').Trim();$api=((Docker @compose ps -a -q api)-join '').Trim()
 if(!$pg -or !$api){throw 'Existing project PostgreSQL and API containers are required'}
 $sourceEnvironment=@(Docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $api)
 if($sourceEnvironment -contains 'STORAGE_BACKEND=s3'){throw 'This drill covers local object storage, not S3; use a bucket backup/restore plan'}
 $mounts=(Docker inspect --format '{{json .Mounts}}' $api)-join ''|ConvertFrom-Json
 $files=@($mounts|Where-Object{$_.Destination-eq '/data/files' -and $_.Type-eq 'volume'})
 if($files.Count-ne 1){throw 'This drill supports the default local files named volume only'}
 $sourceVolume=$files[0].Name
 New-Item -ItemType Directory -Path $folder|Out-Null
 $running=((Docker inspect --format '{{.State.Running}}' $api)-join '').Trim()
 if($running-eq 'true'){$resume=$true;Docker stop --time 30 $api|Out-Null}
 try{
  $phase='source table counts and backup'
  $counts=Counts $pg
  Docker exec $pg pg_dump -U haiduo -d haiduo --format=custom --no-owner --no-acl --file=$temp|Out-Null
  Docker cp "${pg}:$temp" (Join-Path $folder 'database.dump')|Out-Null
  Docker run --rm -v "${sourceVolume}:/source:ro" -v "${folder}:/backup" postgres:17.6 tar -C /source -czf /backup/files.tar.gz .|Out-Null
  $fileHashes=@(Docker run --rm -v "${sourceVolume}:/source:ro" -w /source postgres:17.6 sh -c 'find . -type f -exec sha256sum {} + | sort')
  $manifest=[ordered]@{formatVersion=1;createdUtc=[DateTime]::UtcNow.ToString('o');runId=$RunId;postgresImage='postgres:17.6';databaseSha256=(Get-FileHash (Join-Path $folder 'database.dump')).Hash;filesSha256=(Get-FileHash (Join-Path $folder 'files.tar.gz')).Hash;tableCounts=$counts;fileHashes=$fileHashes;scope='PostgreSQL and local objects; no secrets, Redis or RabbitMQ volume'}
  [IO.File]::WriteAllText((Join-Path $folder 'manifest.json'),($manifest|ConvertTo-Json -Depth 8))
 }finally{
  if($resume){Docker start $api|Out-Null;$resume=$false}
  Docker exec $pg rm -f $temp|Out-Null
 }
 # Restore only into a fresh container without published ports or access to core volumes.
 $phase='restore database startup'
 $env:POSTGRES_PASSWORD=[guid]::NewGuid().ToString('N')
 Docker run -d --name $target --label haiduo.purpose=restore-drill -e POSTGRES_PASSWORD -e POSTGRES_USER=haiduo -e POSTGRES_DB=haiduo postgres:17.6|Out-Null
 $created=$true
 $deadline=(Get-Date).AddSeconds(60)
 # The image uses a temporary Unix-only server during initdb. Wait for the
 # final TCP listener, so pg_restore cannot race that temporary server shutdown.
 do{try{Docker exec $target pg_isready -h 127.0.0.1 -U haiduo -d haiduo|Out-Null;$ready=$true}catch{$ready=$false};if(!$ready){Start-Sleep -Seconds 1}}while(!$ready -and (Get-Date)-lt $deadline)
 if(!$ready){throw 'Restore PostgreSQL not ready after 60 seconds'}
 if((Get-FileHash (Join-Path $folder 'database.dump')).Hash-ne $manifest.databaseSha256 -or (Get-FileHash (Join-Path $folder 'files.tar.gz')).Hash-ne $manifest.filesSha256){throw 'Backup checksum mismatch'}
 Docker cp (Join-Path $folder 'database.dump') "${target}:/tmp/database.dump"|Out-Null
 $phase='pg_restore and restored table counts'
 Docker exec $target pg_restore -U haiduo -d haiduo --exit-on-error --single-transaction --no-owner --no-acl /tmp/database.dump|Out-Null
 $restored=Counts $target
 if(($counts|ConvertTo-Json -Compress)-ne ($restored|ConvertTo-Json -Compress)){throw 'Restored table counts differ'}
 Docker cp (Join-Path $folder 'files.tar.gz') "${target}:/tmp/files.tar.gz"|Out-Null
 $phase='object archive restore'
 Docker exec $target mkdir /restored-files|Out-Null
 Docker exec $target tar -C /restored-files -xzf /tmp/files.tar.gz|Out-Null
 $restoredHashes=@(Docker exec -w /restored-files $target sh -c 'find . -type f -exec sha256sum {} + | sort')
 if(($fileHashes-join "`n")-ne ($restoredHashes-join "`n")){throw 'Restored object file hashes differ'}
 $invalid=Sql $target "select count(*) from inventory where on_hand<0 or reserved<0 or reserved>on_hand"
 if($invalid-ne '0'){throw 'Restored inventory invariant failed'}
 # Start the existing application against restored data, with messaging and AI disabled.
 $phase='restored application verification'
 $network=((Docker inspect --format '{{json .NetworkSettings.Networks}}' $pg)-join ''|ConvertFrom-Json).PSObject.Properties.Name|Select-Object -First 1
 Docker network connect $network $target|Out-Null
 Docker volume create --label haiduo.purpose=restore-drill $restoreFiles|Out-Null
 Docker run --rm -v "${restoreFiles}:/restore" -v "${folder}:/backup:ro" postgres:17.6 tar -C /restore -xzf /backup/files.tar.gz|Out-Null
 $env:DB_PASSWORD=$env:POSTGRES_PASSWORD
 Docker run -d --name $restoreApi --label haiduo.purpose=restore-drill --network $network --env-file .env -e DB_PASSWORD -e "DATABASE_URL=jdbc:postgresql://${target}:5432/haiduo" -e DB_USER=haiduo -e REDIS_HOST=redis -e "SPRING_SESSION_DATA_REDIS_NAMESPACE=haiduo:restore:$RunId" -e SPRING_PROFILES_ACTIVE=default -e FILE_ROOT=/data/files -e STORAGE_BACKEND=local -e COOKIE_SECURE=false -e MESSAGING_ENABLED=false -e AI_ENABLED=false -e AI_DEMO=false -e SEARCH_URL= -e VECTOR_DATABASE_URL= -e TRACING_ENABLED=false -v "${restoreFiles}:/data/files" -p '127.0.0.1::8080' haiduo-api:0.2.0|Out-Null
 $appCreated=$true
 $address=((Docker port $restoreApi 8080/tcp)-join '').Trim()
 $base='http://'+$address
 $deadline=(Get-Date).AddSeconds(60)
 do{try{$csrf=Invoke-RestMethod -TimeoutSec 3 "$base/api/session/csrf" -SessionVariable restoredSession;$ready=$true}catch{$ready=$false};if(!$ready){Start-Sleep -Seconds 1}}while(!$ready -and (Get-Date)-lt $deadline)
 if(!$ready){throw 'Restored API not ready after 60 seconds'}
 $password=[IO.File]::ReadAllText((Join-Path $root '.demo-password')).Trim()
 Invoke-WebRequest -TimeoutSec 10 "$base/api/session/login" -WebSession $restoredSession -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body @{username='demo1.admin';password=$password} -UseBasicParsing|Out-Null
 $me=Invoke-RestMethod -TimeoutSec 10 "$base/api/session" -WebSession $restoredSession
 if(!$me.memberships.Count){throw 'Restored account memberships missing'}
 $csrf=Invoke-RestMethod -TimeoutSec 10 "$base/api/session/csrf" -WebSession $restoredSession
 Invoke-WebRequest -TimeoutSec 10 "$base/api/session/tenant/$($me.memberships[0].tenant_id)" -WebSession $restoredSession -Method Post -Headers @{'X-CSRF-TOKEN'=$csrf.token} -UseBasicParsing|Out-Null
 $orders=Invoke-WebRequest -TimeoutSec 10 "$base/api/orders?size=1" -WebSession $restoredSession -UseBasicParsing
 if($orders.StatusCode-ne 200){throw 'Restored orders inaccessible'}
 $tenant=[guid]$me.memberships[0].tenant_id
 $fileId=Sql $target "select image_file_id from product where tenant_id='$tenant' and image_file_id is not null limit 1"
 $fileApi='no image fixture for selected tenant'
 if($fileId){
  $fileId=[guid]$fileId
  $download=Join-Path $folder 'restored-object-check.bin'
  Invoke-WebRequest -TimeoutSec 10 "$base/api/files/$fileId" -WebSession $restoredSession -OutFile $download -UseBasicParsing|Out-Null
  $expected=@($fileHashes|Where-Object{$_.EndsWith("./$tenant/$fileId")})
  if($expected.Count-ne 1 -or (Get-FileHash $download).Hash.ToLowerInvariant()-ne $expected[0].Substring(0,64).ToLowerInvariant()){throw 'Restored authorized object API hash mismatch'}
  $fileApi='authorized download hash matched'
 }
 $passed=$true;$clock.Stop()
 [ordered]@{verifiedUtc=[DateTime]::UtcNow.ToString('o');runId=$RunId;tables=$restored.Count;files=$restoredHashes.Count;inventoryInvariant='passed';archiveChecksums='matched';allTableCounts='matched';objectFileHashes='matched';restoredApplication='login, tenant selection and orders passed';restoredObjectApi=$fileApi;elapsedSeconds=[math]::Round($clock.Elapsed.TotalSeconds,2);result='PASS'}|ConvertTo-Json|Set-Content (Join-Path $folder 'verification.json') -Encoding UTF8
 Write-Output "PASS: backup restored in isolated PostgreSQL; $($restored.Count) table counts and $($restoredHashes.Count) file hashes match"
 Write-Output "Evidence: backups/$RunId/verification.json (sensitive archives remain ignored)"
}finally{
 if($resume){Docker start $api|Out-Null}
 if($appCreated){Docker stop $restoreApi|Out-Null}
 if($created){Docker stop $target|Out-Null}
 $env:POSTGRES_PASSWORD=$oldPassword
 $env:DB_PASSWORD=$oldDbPassword
 Pop-Location
}
