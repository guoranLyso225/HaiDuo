$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'compose-phase.ps1')
$root=Split-Path $PSScriptRoot -Parent
$directory=Join-Path $root 'services/api/target/compose-phase-check'
[IO.Directory]::CreateDirectory($directory)|Out-Null
$fixture=Join-Path $directory 'fixture.ps1'
@'
param([string]$Counter,[string]$Mode)
$count=[int]([IO.File]::ReadAllText($Counter))+1
[IO.File]::WriteAllText($Counter,[string]$count)
if($Mode-eq 'compiler'){Write-Output '[ERROR] COMPILATION ERROR: invalid source';exit 2}
if($Mode-eq 'unauthorized'){Write-Output 'failed to resolve source metadata: unexpected status code https://registry-1.docker.io/v2/library/node/blobs/example500: 401 Unauthorized';exit 9}
if($Mode-eq 'mixed'){Write-Output 'connection reset by peer';Write-Output '[ERROR] COMPILATION ERROR: invalid source';exit 2}
if($Mode-eq 'persistent' -or ($Mode-eq 'transient' -and $count-lt 3)){
 Write-Output 'failed to resolve source metadata: unexpected status code https://registry-1.docker.io/v2/library/node/blobs/example: 500 Internal Server Error'
 exit 9
}
Write-Output 'fixture phase succeeded'
exit 0
'@ | Set-Content -LiteralPath $fixture -Encoding UTF8
$shellExecutable=(Get-Process -Id $PID).Path
foreach($case in @(@{Name='transient';Attempts=3;Code=0},@{Name='persistent';Attempts=3;Code=9},@{Name='compiler';Attempts=1;Code=2},@{Name='success';Attempts=1;Code=0},@{Name='unauthorized';Attempts=1;Code=9},@{Name='mixed';Attempts=1;Code=2})){
 $counter=Join-Path $directory ($case.Name+'.count')
 [IO.File]::WriteAllText($counter,'0')
 $log=Join-Path $directory ($case.Name+'.log')
 $code=Invoke-HaiDuoComposePhase -DockerExecutable $shellExecutable -DockerArguments @('-NoProfile','-ExecutionPolicy','Bypass','-File',$fixture,'-Counter',$counter,'-Mode',$case.Name) -LogPath $log -MaxAttempts 3 -RetryDelaySeconds 0
 $attempts=[int]([IO.File]::ReadAllText($counter))
 if($code-ne $case.Code -or $attempts-ne $case.Attempts){throw "Incorrect result for $($case.Name): exit=$code attempts=$attempts"}
 if(!(Get-Content -LiteralPath $log -Raw)){throw 'Original phase output missing'}
 Write-Output "PASS $($case.Name): exit=$code attempts=$attempts"
}
Write-Output 'PASS: bounded registry retries; compiler errors are not retried; original logs and exit codes retained'
