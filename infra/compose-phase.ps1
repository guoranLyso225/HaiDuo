function Invoke-HaiDuoComposePhase {
 param([Parameter(Mandatory)][string]$DockerExecutable,[Parameter(Mandatory)][string[]]$DockerArguments,[Parameter(Mandatory)][string]$LogPath,[ValidateRange(1,3)][int]$MaxAttempts=1,[ValidateRange(0,30)][int]$RetryDelaySeconds=5)
 $savedPreference=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  if(!(Test-Path -LiteralPath $DockerExecutable -PathType Leaf)){throw 'Docker executable unavailable'}
  [IO.Directory]::CreateDirectory((Split-Path $LogPath -Parent))|Out-Null
  [IO.File]::WriteAllText($LogPath,'')
  for($attempt=1;$attempt-le $MaxAttempts;$attempt++){
   $attemptLog=$LogPath+'.attempt-'+$attempt+'.log'
   & $DockerExecutable @DockerArguments 2>&1|ForEach-Object{"$_"}|Tee-Object -FilePath $attemptLog|Tee-Object -FilePath $LogPath -Append|Out-Host
   $code=if($null-eq $LASTEXITCODE){1}else{[int]$LASTEXITCODE}
   if(!$code){return 0}
   $output=Get-Content -LiteralPath $attemptLog -Raw
   $networkFailure=$output-match 'failed to resolve source metadata|failed to fetch anonymous token|httpReadSeeker|TLS handshake timeout|connection reset by peer|i/o timeout'
   $retryable=$output-match 'unexpected status code[^\r\n]*[: ](429|500|502|503|504)\b|TLS handshake timeout|connection reset by peer|i/o timeout'
   $compilerFailure=$output-match 'COMPILATION ERROR|error TS[0-9]+|SyntaxError'
   if($attempt-ge $MaxAttempts -or $compilerFailure -or !$networkFailure -or !$retryable){return $code}
   $delay=[Math]::Min(30,$RetryDelaySeconds*$attempt)
   Write-Warning "Transient image registry/network failure; retry $($attempt+1)/$MaxAttempts after $delay seconds. Original output retained."
   if($delay){Start-Sleep -Seconds $delay}
  }
 }finally{$ErrorActionPreference=$savedPreference}
}
