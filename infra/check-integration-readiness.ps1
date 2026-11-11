param([string]$EnvFile=(Join-Path (Split-Path $PSScriptRoot -Parent) '.env'))
$ErrorActionPreference='Stop'
# Offline configuration check only. Never print values or call providers.
$values=@{}
if(Test-Path -LiteralPath $EnvFile){
 foreach($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile).Path)){
  if($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$'){
   $values[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'")
  }
 }
}
$groups=[ordered]@{
 Shopify=@('SHOPIFY_CLIENT_ID','SHOPIFY_CLIENT_SECRET','SHOPIFY_REDIRECT_URI','TOKEN_ENCRYPTION_KEY')
 S3=@('S3_BUCKET','S3_REGION','S3_ACCESS_KEY','S3_SECRET_KEY')
 AI=@('AI_BASE_URL','AI_API_KEY','AI_CHAT_MODEL','AI_EMBEDDING_MODEL','VECTOR_DATABASE_URL','VECTOR_DB_USER','VECTOR_DB_PASSWORD')
}
foreach($group in $groups.GetEnumerator()){
 $missing=@();$invalid=@()
 foreach($key in $group.Value){
  $value=[Environment]::GetEnvironmentVariable($key)
  if([string]::IsNullOrWhiteSpace($value)){$value=$values[$key]}
  if([string]::IsNullOrWhiteSpace($value) -or $value -match '^(replace|your[-_]|changeme)'){$missing+=$key;continue}
  if($key -eq 'TOKEN_ENCRYPTION_KEY'){
   try{if([Convert]::FromBase64String($value).Length -ne 32){$invalid+=$key}}catch{$invalid+=$key}
  }
  if($key -in @('SHOPIFY_REDIRECT_URI','AI_BASE_URL')){
   $uri=$null
   if(![Uri]::TryCreate($value,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https')){$invalid+=$key}
  }
 }
 [pscustomobject]@{Integration=$group.Key;Configuration=if($missing.Count -or $invalid.Count){'INCOMPLETE'}else{'PRESENT_NOT_CONNECTED'};Missing=($missing -join ',');Invalid=($invalid -join ',')}
}
Write-Output 'Offline check only. Credentials, permissions, costs and connectivity are not verified. No provider calls made.'
