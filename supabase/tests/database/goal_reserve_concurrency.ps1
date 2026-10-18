param([string]$Container = 'supabase_db_kipu-met-local-20261017')
$ErrorActionPreference = 'Stop'

$userId = '96000000-0000-4000-8000-000000000001'
$accountId = '96000000-0000-4000-8000-000000000011'
$goalIds = @('96000000-0000-4000-8000-000000000101', '96000000-0000-4000-8000-000000000102')
$eventIds = @('96000000-0000-4000-8000-000000000201', '96000000-0000-4000-8000-000000000202')
$fixture = Join-Path $PSScriptRoot '_goal_test_fixture.sql'

function Invoke-LocalSql([string]$sql) {
  $output = $sql | & docker exec -i $Container psql -U postgres -d postgres -v ON_ERROR_STOP=1 -tA 2>&1
  if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
  return $output
}

function New-GoalCommand([string]$goalId, [string]$eventId) {
  $payload = [ordered]@{
    amount_minor = 80000
    backing_account_id = $accountId
    currency_code = 'PEN'
    event_id = $eventId
    goal_id = $goalId
  }
  $body = [ordered]@{
    command_type = 'RESERVE_GOAL'
    contract_version = 1
    expected_revision = 1
    goal_id = $goalId
    payload = $payload
  }
  $canonical = ConvertTo-Json -InputObject $body -Compress -Depth 8
  $hasher = [Security.Cryptography.SHA256]::Create()
  try {
    $hash = [BitConverter]::ToString($hasher.ComputeHash(
      [Text.Encoding]::UTF8.GetBytes($canonical))).Replace('-','').ToLowerInvariant()
  } finally { $hasher.Dispose() }
  $body['operation_id'] = [guid]::NewGuid().ToString()
  $body['request_hash'] = $hash
  return ConvertTo-Json -InputObject $body -Compress -Depth 8
}

$job1 = $null
$job2 = $null
try {
  $seed = Get-Content $fixture -Raw
  # Fixture creates a temporary command signer; the signer disappears with this
  # connection. Only its synthetic financial rows are kept until cleanup.
  Invoke-LocalSql "BEGIN; $seed INSERT INTO public.goals(id,user_id,name,target_minor,currency_code) VALUES
    ('$($goalIds[0])','$userId','Race one',90000,'PEN'),
    ('$($goalIds[1])','$userId','Race two',90000,'PEN'); COMMIT;" | Out-Null

  $command1 = New-GoalCommand $goalIds[0] $eventIds[0]
  $command2 = New-GoalCommand $goalIds[1] $eventIds[1]
  $sql1 = @"
BEGIN;
SELECT id FROM public.accounts WHERE id='$accountId'::uuid FOR UPDATE;
SET LOCAL ROLE authenticated;
SELECT pg_catalog.set_config('request.jwt.claim.sub','$userId',true);
SELECT pg_sleep(2);
SELECT public.reserve_goal_v1('$command1'::jsonb);
COMMIT;
"@
  $sql2 = @"
BEGIN;
SET LOCAL ROLE authenticated;
SELECT pg_catalog.set_config('request.jwt.claim.sub','$userId',true);
SELECT public.reserve_goal_v1('$command2'::jsonb);
COMMIT;
"@
  $worker = {
    param($databaseContainer, $sqlText)
    $value = $sqlText | & docker exec -i $databaseContainer psql -U postgres -d postgres -v ON_ERROR_STOP=1 -tA 2>&1
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($value -join "`n") }
  }
  $job1 = Start-Job -ScriptBlock $worker -ArgumentList $Container, $sql1
  Start-Sleep -Milliseconds 650
  $job2 = Start-Job -ScriptBlock $worker -ArgumentList $Container, $sql2
  Wait-Job -Job @($job1,$job2) | Out-Null
  $results = @((Receive-Job $job1), (Receive-Job $job2))
  foreach ($result in $results) {
    if ($result.ExitCode -ne 0) { throw $result.Output }
  }
  $applied = @($results | Where-Object { $_.Output -match '"status": "APPLIED"' }).Count
  $rejected = @($results | Where-Object { $_.Output -match '"error": "INSUFFICIENT_BACKING"' }).Count
  if ($applied -ne 1 -or $rejected -ne 1) {
    throw "Expected one applied and one rejected; results: $($results.Output -join ' | ')"
  }
  $verification = @(Invoke-LocalSql "SELECT (SELECT count(*) FROM public.goal_events WHERE user_id='$userId'),
    internal.goal_reserved('$userId',NULL,'$accountId'),
    internal.goal_confirmed_balance('$userId','$accountId');") -join ''
  if ($verification -ne '1|80000|100000') { throw "Concurrent reserve overdraw: $verification" }
  Write-Output 'PASS: concurrent reserve serialized; one applied, one rejected; 80000 of 100000 reserved.'
} finally {
  if ($job1) { Remove-Job -Job $job1 -Force -ErrorAction SilentlyContinue }
  if ($job2) { Remove-Job -Job $job2 -Force -ErrorAction SilentlyContinue }
  $cleanup = @"
BEGIN;
DELETE FROM public.goal_events WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM internal.sync_changes WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM internal.command_receipts WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM public.goals WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM public.financial_movements WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM public.accounts WHERE user_id IN ('$userId','96000000-0000-4000-8000-000000000002');
DELETE FROM auth.users WHERE id IN ('$userId','96000000-0000-4000-8000-000000000002');
COMMIT;
"@
  Invoke-LocalSql $cleanup | Out-Null
}
