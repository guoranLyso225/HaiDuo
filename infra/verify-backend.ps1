param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if ($JavaHome) { $env:JAVA_HOME=$JavaHome; $env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH }
$root=Split-Path $PSScriptRoot -Parent
Push-Location $root
$names='InventoryContainerTest,MessagingContainerTest,SyncContainerTest,ShopifyAdapterTest,BlobContractTest,ProfitTest,RequestCorrelationTest,KnowledgeContainerTest,VectorContainerTest,ModelAdapterTest,TrackingSnapshotContainerTest,ArchitectureTest,RoleTest,WooCommerceAdapterTest'
$keys=@('RUN_TESTCONTAINERS','RUN_DATABASE_TESTS','RUN_MESSAGING_TESTS','RUN_SYNC_TESTS')
$previous=@{}
try {
 foreach($key in $keys){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,'true','Process')}
 # Windows PowerShell 5 treats native stderr warnings as errors under Stop.
 # Preserve the output and judge Maven by its exit code and XML reports.
 $savedPreference=$ErrorActionPreference
 try {
  $ErrorActionPreference='Continue'
  & mvn -B -s infra/maven-settings.xml -f services/api/pom.xml "-Dtest=$names" verify
  $mavenExit=$LASTEXITCODE
 } finally { $ErrorActionPreference=$savedPreference }
 if($mavenExit){throw "Maven failed: $mavenExit"}
 $count=0
 foreach($name in $names.Split(',')){
  [xml]$report=Get-Content "services/api/target/surefire-reports/TEST-cn.haiduo.$name.xml" -Raw
  $suite=$report.testsuite
  if([int]$suite.skipped -gt 0 -or [int]$suite.failures -gt 0 -or [int]$suite.errors -gt 0){throw "Incomplete or failed tests: $name"}
  $count += [int]$suite.tests
 }
 Write-Output "PASS: $count backend tests; no failures, errors or skips"
} finally {
 foreach($key in $keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
 Pop-Location
}
