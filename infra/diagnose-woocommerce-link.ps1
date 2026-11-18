param([ValidatePattern('^[a-z0-9-]{1,40}$')][string]$CoreProjectName='haiduo-dev',[ValidatePattern('^[a-z0-9-]{1,40}$')][string]$WooProjectName='haiduo-woo')
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
$dockerCommand=Get-Command docker -CommandType Application -ErrorAction SilentlyContinue|Select-Object -First 1
if(!$dockerCommand){Write-Warning 'Docker executable unavailable; diagnostics skipped';return}
$dockerExecutable=[string]$dockerCommand.Source
$outputDirectory=Join-Path $root 'services/api/target/woo-link-diagnostics'
[IO.Directory]::CreateDirectory($outputDirectory)|Out-Null
function Save-Diagnostic([string]$Name,[string[]]$DockerArguments){
 Write-Output "--- $Name ---"
 & $dockerExecutable @DockerArguments 2>&1|ForEach-Object{"$_"}|Tee-Object -FilePath (Join-Path $outputDirectory ($Name+'.log'))|Out-Host
 $code=$LASTEXITCODE
 if($code){Write-Warning "Diagnostic $Name unavailable (exit $code); continuing"}
}
Push-Location $root
try{
 Save-Diagnostic 'docker-version' @('version','--format','{{.Server.Version}}')
 $core=@('compose','-p',$CoreProjectName,'--env-file','.env','-f','infra/compose.yml','-f','infra/compose.woo-link.yml')
 $woo=@('compose','-p',$WooProjectName,'--env-file','.woo.env','-f','infra/compose.woocommerce.yml')
 Save-Diagnostic 'core-state' ($core+@('ps','-a'))
 Save-Diagnostic 'core-services' ($core+@('logs','--no-color','--tail','200','postgres','redis','rabbitmq','api','web'))
 Save-Diagnostic 'woo-state' ($woo+@('--profile','webhooks','ps','-a'))
 Save-Diagnostic 'woo-services' ($woo+@('--profile','webhooks','logs','--no-color','--tail','100','db','shop','cron'))
 $database=& $dockerExecutable @core ps --status running -q postgres 2>$null
 if($LASTEXITCODE-eq 0 -and $database){
  Save-Diagnostic 'event-metadata' ($core+@('exec','-T','postgres','psql','-U','haiduo','-d','haiduo','-c','select kind,status,count(*) from outbox_event group by kind,status order by kind,status; select type,status,count(*) from sync_job group by type,status;'))
 }else{Write-Output 'PostgreSQL is not running; SQL diagnostics skipped. Check image-build and core service logs first.'}
 Write-Output 'Diagnostics collected without printing Compose environment or connection credentials.'
}finally{Pop-Location}
# Diagnostics must not replace the original startup/test failure in CI.
$global:LASTEXITCODE=0
