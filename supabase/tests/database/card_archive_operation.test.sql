BEGIN;
SET LOCAL search_path=public,extensions;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
CREATE TEMP TABLE archive_tap(line text);
INSERT INTO archive_tap SELECT plan(2);
INSERT INTO auth.users(id,email) VALUES ('a0311111-1111-4111-8111-111111111111','archive-fixture@kipu.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES
('a0322222-2222-4222-8222-222222222222','a0311111-1111-4111-8111-111111111111','VISA','CREDIT_LIABILITY','PEN');
INSERT INTO public.cards(id,user_id,creation_operation_id,account_id,network,alias,last4,is_credit,credit_limit_minor,closing_day,due_day,currency_code,issuer)
VALUES ('a0333333-3333-4333-8333-333333333333','a0311111-1111-4111-8111-111111111111','a0344444-4444-4444-8444-444444444444',
'a0322222-2222-4222-8222-222222222222','VISA','VISA','1234',true,100000,15,5,'PEN','BCP');
SELECT set_config('request.jwt.claim.sub','a0311111-1111-4111-8111-111111111111',true);
INSERT INTO archive_tap SELECT lives_ok($$SELECT public.set_instrument_archived_v1(jsonb_build_object(
'instrument_id','a0333333-3333-4333-8333-333333333333','instrument_type','CARD','is_archived',true))$$,'card archive succeeds with its established snapshot operation');
INSERT INTO archive_tap SELECT ok(EXISTS(SELECT 1 FROM internal.sync_changes WHERE entity_id='a0333333-3333-4333-8333-333333333333' AND operation='ARCHIVE' AND (payload->>'is_archived')::boolean),'archive is visible to other devices');
INSERT INTO archive_tap SELECT * FROM finish();
SELECT line FROM archive_tap;
ROLLBACK;
