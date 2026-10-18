#!/usr/bin/env python3
"""Independent multi-session RELEASE_GOAL quota race harness.

Runs only against the explicitly named local PostgreSQL container. The RED
oracle is ACTIVE goals <= 2 after racing legacy RELEASE with CREATE/UPDATE.
Synthetic rows are committed for cross-session visibility and always deleted
in a verified cleanup transaction, including after failures.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import uuid

CONTAINER = "supabase_db_kipu-met-local-20261017"
DB = "postgres"
USER = "postgres"


def psql(sql: str, timeout: int = 30) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1",
         "-U", USER, "-d", DB, "-At"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    out = (result.stdout + result.stderr).strip()
    print(f"[psql exit={result.returncode}] {out}", flush=True)
    if result.returncode:
        raise RuntimeError(out)
    return result.stdout.strip()


def command_sql(command_type: str, goal_id: str, revision: int | None, payload: dict) -> str:
    body = {"contract_version": 1, "command_type": command_type,
            "goal_id": goal_id, "payload": payload}
    if revision is not None:
        body["expected_revision"] = revision
    # JSON is escaped as a SQL string literal, then cast to jsonb. A SECURITY
    # DEFINER pg_temp signer in the session computes the canonical hash.
    return "pg_temp.goal_cmd(" + ",".join([
        "'" + command_type + "'", "'" + goal_id + "'::uuid",
        "NULL" if revision is None else str(revision),
        "'" + json.dumps(payload, separators=(",", ":")).replace("'", "''") + "'::jsonb",
    ]) + ")"


def session_preamble(owner: str) -> str:
    return f"""BEGIN;
CREATE FUNCTION pg_temp.goal_cmd(t text, g uuid, rev bigint, body jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE p jsonb;
BEGIN
 p := pg_catalog.jsonb_build_object('contract_version',1,'command_type',t,'goal_id',g,'payload',body);
 IF rev IS NOT NULL THEN p := p || pg_catalog.jsonb_build_object('expected_revision',rev); END IF;
 RETURN p || pg_catalog.jsonb_build_object('operation_id',extensions.gen_random_uuid(),
  'request_hash',pg_catalog.encode(extensions.digest(pg_catalog.convert_to(
   internal.goal_canonical_json(p),'UTF8'),'sha256'),'hex'));
END $$;
SET LOCAL ROLE authenticated;
SELECT pg_catalog.set_config('request.jwt.claim.sub','{owner}',true);
"""


def start_session(sql: str) -> subprocess.Popen[str]:
    proc = subprocess.Popen(
        ["docker", "exec", "-i", CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1",
         "-U", USER, "-d", DB, "-At"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1,
    )
    assert proc.stdin is not None
    proc.stdin.write(sql)
    proc.stdin.flush()
    return proc


def read_until(proc: subprocess.Popen[str], marker: str, timeout: float = 15) -> list[str]:
    import queue
    import threading
    lines: list[str] = []
    output: queue.Queue[str | None] = queue.Queue()
    assert proc.stdout is not None

    def pump() -> None:
        for line in proc.stdout:
            output.put(line.strip())
        output.put(None)

    threading.Thread(target=pump, daemon=True).start()
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=max(0.0, deadline - time.monotonic()))
        except queue.Empty:
            break
        if line is None:
            break
        lines.append(line)
        print(f"[session] {line}", flush=True)
        if marker in line:
            return lines
    raise TimeoutError(f"session did not reach {marker!r}; output={lines}; poll={proc.poll()}")


def parse_response(lines: list[str], label: str) -> dict:
    responses = []
    for line in lines:
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict) and ("status" in value or "error" in value):
            responses.append(value)
    if len(responses) != 1:
        raise AssertionError(f"{label}: expected exactly one JSON response, got {responses!r} from {lines!r}")
    return responses[0]


def assert_race_outcomes(contender_response: dict, release_response: dict) -> None:
    assert contender_response.get("status") == "APPLIED", (
        f"contender must be APPLIED, got {contender_response!r}"
    )
    assert release_response == {"error": "REPLACEMENT_REQUIRED", "status": "REJECTED"}, (
        f"serialized RELEASE must reject with REPLACEMENT_REQUIRED, got {release_response!r}"
    )


def probe_response_assertions() -> None:
    assert_race_outcomes(
        {"status": "APPLIED"},
        {"error": "REPLACEMENT_REQUIRED", "status": "REJECTED"},
    )
    try:
        assert_race_outcomes(
            {"status": "REJECTED", "error": "QUOTA_EXCEEDED"},
            {"error": "REPLACEMENT_REQUIRED", "status": "REJECTED"},
        )
    except AssertionError:
        print("ASSERTION PROBE PASS: non-APPLIED contender is rejected by harness", flush=True)
    else:
        raise AssertionError("assertion probe failed to reject non-APPLIED contender")


def finish_session(proc: subprocess.Popen[str], sql: str, timeout: int = 20) -> tuple[int, str]:
    assert proc.stdin is not None and proc.stdout is not None
    proc.stdin.write(sql)
    proc.stdin.close()
    try:
        proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
        raise TimeoutError(f"SQL session exceeded {timeout}s")
    return proc.returncode or 0, proc.stdout.read()


def seed(owner: str, account: str, goals: list[str], event: str, operation: str) -> None:
    sql = f"""BEGIN;
INSERT INTO auth.users(id,email) VALUES ('{owner}','race-{owner}@kipu.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,initial_balance_minor_units,creation_operation_id,preset_id)
VALUES ('{account}','{owner}','Race account','CASH','PEN',0,'{uuid.uuid4()}','CASH');
INSERT INTO public.financial_movements(operation_id,user_id,kind,amount_minor_units,currency,account_id,opening_account_id)
VALUES ('{uuid.uuid4()}','{owner}','OPENING',100000,'PEN','{account}','{account}');
INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,status,revision)
VALUES ('{goals[0]}','{owner}','Active baseline',90000,'PEN','ACTIVE',1),
       ('{goals[1]}','{owner}','Completed release source',100000,'PEN','COMPLETED',1),
       ('{goals[2]}','{owner}','Completed update source',100000,'PEN','COMPLETED',1);
INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,currency_code,backing_account_id,operation_id)
VALUES ('{event}','{owner}','{goals[1]}','ALLOCATION',100000,100000,'PEN','{account}','{operation}');
COMMIT;
"""
    psql(sql)


def run_race(owner: str, account: str, goals: list[str], event: str, contender: str) -> tuple[str, int]:
    source = goals[1] if contender == "CREATE_GOAL" else goals[2]
    if contender == "CREATE_GOAL":
        payload = {"name": "Concurrent create", "target_minor": 50000,
                   "currency_code": "PEN", "color_argb": -1, "icon_key": "target"}
        contender_goal = goals[3]
        invocation = f"SELECT public.create_goal_v1({command_sql(contender, contender_goal, None, payload)});"
    else:
        # Completed goal with zero reserve becomes ACTIVE when its target is raised.
        payload = {"name": "Concurrent update", "target_minor": 50000,
                   "currency_code": "PEN", "color_argb": -1, "icon_key": "target"}
        invocation = f"SELECT public.update_goal_v1({command_sql(contender, source, 1, payload)});"
    release_payload = {"event_id": str(uuid.uuid4()), "amount_minor": 20000,
                       "currency_code": "PEN", "backing_account_id": account}
    release_call = f"SELECT public.release_goal_v1({command_sql('RELEASE_GOAL', goals[1], 1, release_payload)});"

    # Start contender first; its quota lock (UPDATE/CREATE) and mutations are
    # held uncommitted during pg_sleep. Release starts only after READY is read.
    first = start_session(session_preamble(owner) + invocation +
                          "SELECT 'CONTENDER_READY';\nSELECT pg_sleep(2);\n")
    assert first.stdin is not None
    first.stdin.write("")
    # Send SQL now while keeping stdin open; psql consumes through sleep.
    first.stdin.flush()
    output = read_until(first, "CONTENDER_READY")
    second_sql = session_preamble(owner) + release_call + "SELECT 'RELEASE_READY';\nCOMMIT;\n"
    second = start_session(second_sql)
    assert second.stdin is not None
    second.stdin.close()
    # With the shared quota advisory lock, RELEASE correctly waits for the
    # contender's transaction. Commit it before waiting for RELEASE to finish.
    contender_response = parse_response(output, f"{contender} response")
    rc, remaining = finish_session(first, "COMMIT;\n", timeout=15)
    print(f"[{contender} transaction exit={rc}] {remaining.strip()}", flush=True)
    second.wait(timeout=15)
    second_output = second.stdout.read() if second.stdout is not None else ""
    print(f"[{contender} vs RELEASE exit={second.returncode}] {second_output.strip()}", flush=True)
    if rc != 0 or second.returncode != 0:
        raise RuntimeError(f"race SQL failed: contender={rc}, release={second.returncode}")
    release_response = parse_response([str(line) for line in second_output.splitlines()], "RELEASE response")
    assert_race_outcomes(contender_response, release_response)
    print(f"FUNCTIONAL {contender}={json.dumps(contender_response, sort_keys=True)} "
          f"RELEASE={json.dumps(release_response, sort_keys=True)}", flush=True)
    final = psql(f"SELECT count(*) FROM public.goals WHERE user_id='{owner}' AND deleted_at IS NULL AND status='ACTIVE';")
    active_count = int(final)
    if active_count != 2:
        raise AssertionError(f"{contender} race must finish with exactly 2 ACTIVE goals, got {active_count}")
    return final, active_count


def cleanup(owner: str) -> None:
    sql = f"""BEGIN;
DELETE FROM public.goal_events WHERE user_id='{owner}';
DELETE FROM internal.sync_changes WHERE user_id='{owner}';
DELETE FROM internal.command_receipts WHERE user_id='{owner}';
DELETE FROM public.goals WHERE user_id='{owner}';
DELETE FROM public.financial_movements WHERE user_id='{owner}';
DELETE FROM public.accounts WHERE user_id='{owner}';
DELETE FROM auth.users WHERE id='{owner}';
COMMIT;
SELECT concat_ws('|',(SELECT count(*) FROM auth.users WHERE id='{owner}'),
 (SELECT count(*) FROM public.accounts WHERE user_id='{owner}'),
 (SELECT count(*) FROM public.goals WHERE user_id='{owner}'),
 (SELECT count(*) FROM public.goal_events WHERE user_id='{owner}'),
 (SELECT count(*) FROM internal.sync_changes WHERE user_id='{owner}'),
 (SELECT count(*) FROM internal.command_receipts WHERE user_id='{owner}'));
"""
    result = psql(sql)
    if result.splitlines()[-1] != "0|0|0|0|0|0":
        raise RuntimeError(f"cleanup verification failed for {owner}: {result}")
    print(f"CLEANUP VERIFIED user/accounts/goals/events/sync/receipts=0|0|0|0|0|0", flush=True)


def main() -> int:
    probe_response_assertions()
    if len(sys.argv) > 1 and sys.argv[1] != CONTAINER:
        raise SystemExit(f"Refusing non-approved container {sys.argv[1]!r}")
    if os.environ.get("KIPU_ALLOW_LOCAL_DB_RACE") != "1":
        raise SystemExit("Set KIPU_ALLOW_LOCAL_DB_RACE=1 to confirm the isolated local sandbox target")
    results = []
    for contender in ("CREATE_GOAL", "UPDATE_GOAL"):
        owner = str(uuid.uuid4())
        account, event, operation = (str(uuid.uuid4()) for _ in range(3))
        goals = [str(uuid.uuid4()) for _ in range(4)]
        seeded = False
        try:
            seed(owner, account, goals, event, operation)
            seeded = True
            raw, count = run_race(owner, account, goals, event, contender)
            expected = "RED: quota invariant violated" if count > 2 else "PASS: quota invariant maintained"
            print(f"RESULT {contender}_vs_RELEASE active_count={raw} => {expected}", flush=True)
            results.append(count)
        finally:
            if seeded:
                cleanup(owner)
    if any(count > 2 for count in results):
        print("EXPECTED RED: legacy RELEASE is not serialized with quota consumers.", flush=True)
        return 1
    print("PASS: both multi-session races preserved ACTIVE <= 2.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
