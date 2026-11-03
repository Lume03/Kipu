BEGIN;
SET LOCAL search_path=public,extensions;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SELECT plan(27);
SELECT ok(NOT has_function_privilege('anon','public.purge_account_data(uuid,uuid,text)','EXECUTE'),'anon cannot purge');
SELECT ok(NOT has_function_privilege('authenticated','public.purge_account_data(uuid,uuid,text)','EXECUTE'),'authenticated cannot purge');
SELECT ok(has_function_privilege('service_role','public.purge_account_data(uuid,uuid,text)','EXECUTE'),'service role can purge');
SELECT ok(NOT has_function_privilege('authenticated','public.account_deletion_status(uuid,text)','EXECUTE'),'receipt capability lookup is service only');
SELECT ok(NOT has_function_privilege('authenticated','public.complete_account_deletion(uuid,text)','EXECUTE'),'completion is service only');
SELECT ok((SELECT prosecdef AND proconfig @> ARRAY['search_path=""'] FROM pg_proc WHERE oid='public.purge_account_data(uuid,uuid,text)'::regprocedure),'fixed empty search_path and SECURITY DEFINER');

INSERT INTO auth.users(id,email) VALUES
 ('a0611111-1111-4111-8111-111111111111','hu06-delete@kipu.test'),
 ('a0611111-1111-4111-8111-111111111112','hu06-other@kipu.test');
INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id)
SELECT u.id,u.id,'Fixture','BANK','PEN','GENERIC' FROM auth.users u WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO public.categories(id,user_id,name,origin,category_type) SELECT id,id,'Fixture','CUSTOM','EXPENSE' FROM auth.users WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,occurred_at)
SELECT u.id,u.id,u.id,u.id,'EXPENSE','STANDARD',100,'PEN',now() FROM auth.users u WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO public.transaction_revisions(user_id,transaction_id,revision_number,previous_payload,new_payload,change_reason,command_id,command_type,base_revision,local_revision,provenance)
SELECT user_id,id,1,'{}','{}','Fixture',id,'CREATE',0,1,'LOCAL' FROM public.transactions WHERE id::text LIKE 'a0611111%';
INSERT INTO public.movement_official_revisions(user_id,transaction_id,official_revision,revision_id,official_revision_id)
SELECT user_id,transaction_id,1,id,id FROM public.transaction_revisions WHERE user_id::text LIKE 'a0611111%';
INSERT INTO internal.ledger_entries(user_id,transaction_id,account_id,entry_role,signed_amount_minor,currency_code)
SELECT user_id,id,account_id,'PRIMARY',-100,'PEN' FROM public.transactions WHERE id::text LIKE 'a0611111%';
INSERT INTO internal.movement_ledger_effects(user_id,command_id,effect_ordinal,transaction_id,revision_id,ledger_entry_id)
SELECT r.user_id,r.command_id,0,r.transaction_id,r.id,l.id FROM public.transaction_revisions r JOIN internal.ledger_entries l USING(user_id,transaction_id) WHERE r.user_id::text LIKE 'a0611111%';
INSERT INTO internal.movement_ledger_aliases(user_id,physical_entry_id,command_id,effect_ordinal)
SELECT user_id,ledger_entry_id,command_id,effect_ordinal FROM internal.movement_ledger_effects WHERE user_id::text LIKE 'a0611111%';
INSERT INTO public.billing_products(id,store_product_id,name,plan_type) VALUES('hu06-product','hu06-product','Fixture','PRO_LIFETIME');
INSERT INTO public.billing_purchases(user_id,purchase_token_hash,product_id,purchase_state)
SELECT id,repeat(CASE WHEN email='hu06-delete@kipu.test' THEN 'a' ELSE 'b' END,64),'hu06-product','PURCHASED' FROM auth.users WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO internal.billing_events(user_id,purchase_token_hash,event_type)
SELECT user_id,purchase_token_hash,'VERIFICATION' FROM public.billing_purchases WHERE product_id='hu06-product';
INSERT INTO internal.billing_events(user_id,purchase_token_hash,event_type) VALUES(NULL,repeat('a',64),'RTDN_UPDATE');
INSERT INTO internal.billing_event_receipts(id,event_identity_hash,provider_message_id,purchase_token_hash,user_id,notification_type,receipt_status)
SELECT user_id,repeat(CASE WHEN purchase_token_hash=repeat('a',64) THEN 'c' ELSE 'd' END,64),user_id::text,purchase_token_hash,user_id,'SUBSCRIPTION','RETRYABLE'
FROM public.billing_purchases WHERE product_id='hu06-product';
INSERT INTO internal.billing_reconciliation_jobs(purchase_token_hash,user_id,latest_event_receipt_id,job_status)
SELECT purchase_token_hash,user_id,id,'RETRYABLE' FROM internal.billing_event_receipts WHERE user_id::text LIKE 'a0611111%';
INSERT INTO internal.billing_event_receipts(event_identity_hash,provider_message_id,purchase_token_hash,notification_type,receipt_status)
VALUES(repeat('e',64),'unassociated',repeat('a',64),'SUBSCRIPTION','RETRYABLE');
INSERT INTO internal.billing_reconciliation_jobs(purchase_token_hash,job_status) VALUES(repeat('f',64),'WAITING_FOR_TOKEN');
INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload)
SELECT id,'hu06','CREATE','hash','{}' FROM auth.users WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO public.app_notifications(user_id,notification_type,title,body)
SELECT id,'SYSTEM','Fixture','Fixture' FROM auth.users WHERE email LIKE 'hu06-%@kipu.test';
INSERT INTO private.auth_otp_guards(email_hash) VALUES(extensions.digest(convert_to('hu06-delete@kipu.test','UTF8'),'sha256'));
INSERT INTO private.registration_rate_buckets(bucket_hash) VALUES(extensions.digest(convert_to('login:id:hu06-delete@kipu.test','UTF8'),'sha256'));

CREATE FUNCTION pg_temp.owner_snapshot(subject uuid) RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE t record; rows jsonb; result jsonb:='{}'; BEGIN
 FOR t IN SELECT n.nspname,c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='user_id' AND NOT a.attisdropped
 WHERE n.nspname IN ('public','private','internal') AND c.relkind='r' AND a.atttypid='uuid'::regtype AND c.relname<>'account_deletion_requests'
 LOOP
  EXECUTE format('SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),''[]''::jsonb) FROM %I.%I t WHERE user_id=$1',t.nspname,t.relname) INTO rows USING subject;
  result:=result||jsonb_build_object(t.nspname||'.'||t.relname,rows);
 END LOOP;
 RETURN result;
END $$;
CREATE TEMP TABLE before_other AS SELECT pg_temp.owner_snapshot('a0611111-1111-4111-8111-111111111112') AS data;
SELECT set_config('kipu.deleting_account','a0611111-1111-4111-8111-111111111111',true);
SELECT throws_ok($$DELETE FROM public.transaction_revisions WHERE user_id='a0611111-1111-4111-8111-111111111111'$$,'55000','Movement financial evidence is append-only','a forged GUC without purge receipt cannot delete evidence');
SELECT set_config('kipu.deleting_account','',true);

CREATE TABLE internal.deletion_unmapped_fixture(user_id uuid);
INSERT INTO internal.deletion_unmapped_fixture VALUES('a0611111-1111-4111-8111-111111111111');
SELECT throws_ok($$SELECT public.purge_account_data('a0611111-1111-4111-8111-111111111111','a0622222-2222-4222-8222-222222222222',repeat('1',64))$$,'P0001','Unpurged user data in internal.deletion_unmapped_fixture','unknown user table fails closed');
SELECT is((SELECT count(*)::integer FROM public.transactions WHERE user_id='a0611111-1111-4111-8111-111111111111'),1,'failed purge rolls back financial deletion');
SELECT is((SELECT count(*)::integer FROM private.account_deletion_requests WHERE request_id='a0622222-2222-4222-8222-222222222222'),0,'failed purge rolls back receipt');
DROP TABLE internal.deletion_unmapped_fixture;

SET LOCAL ROLE service_role;
SELECT is(public.purge_account_data('a0611111-1111-4111-8111-111111111111','a0622222-2222-4222-8222-222222222222',repeat('1',64))->>'state','data_deleted','service role purges all data');
SELECT is(public.purge_account_data('a0611111-1111-4111-8111-111111111111','a0622222-2222-4222-8222-222222222222',repeat('1',64))->>'state','data_deleted','repeat is idempotent');
RESET ROLE;
SELECT ok(NOT EXISTS(SELECT 1 FROM jsonb_each(pg_temp.owner_snapshot('a0611111-1111-4111-8111-111111111111')) WHERE value<>'[]'::jsonb),'no owned rows remain in any data schema');
SELECT is(pg_temp.owner_snapshot('a0611111-1111-4111-8111-111111111112'),(SELECT data FROM before_other),'all tables of other user are byte-for-byte unchanged');
SELECT is((SELECT count(*)::integer FROM internal.billing_events WHERE purchase_token_hash=repeat('a',64)),0,'unassociated billing events also deleted by purchase ownership');
SELECT is((SELECT count(*)::integer FROM internal.billing_event_receipts WHERE purchase_token_hash=repeat('a',64)),0,'unassociated billing receipts deleted');
SELECT is((SELECT count(*)::integer FROM private.auth_otp_guards),0,'OTP identity guard deleted');
SELECT is((SELECT count(*)::integer FROM private.registration_rate_buckets WHERE bucket_hash=extensions.digest(convert_to('login:id:hu06-delete@kipu.test','UTF8'),'sha256')),0,'identity rate bucket deleted');
SELECT throws_ok($$INSERT INTO public.plan_preferences(user_id,selection) VALUES('a0611111-1111-4111-8111-111111111111','FREE')$$,'42501','Account deletion in progress or account absent','stale sync cannot recreate data while Auth is pending');
SELECT throws_ok($$SELECT public.complete_account_deletion('a0622222-2222-4222-8222-222222222222',repeat('1',64))$$,'P0001','Auth deletion not completed','cannot claim completed while Auth exists');
SELECT ok(public.account_deletion_status('a0622222-2222-4222-8222-222222222222',repeat('2',64)) IS NULL,'wrong capability reveals no receipt');
SELECT lives_ok($$DELETE FROM auth.users WHERE id='a0611111-1111-4111-8111-111111111111'$$,'Auth can now be removed without orphan FKs');
SELECT lives_ok($$SELECT public.complete_account_deletion('a0622222-2222-4222-8222-222222222222',repeat('1',64))$$,'receipt marked completed only after Auth removal');
SELECT is(public.account_deletion_status('a0622222-2222-4222-8222-222222222222',repeat('1',64))->>'state','completed','completion can be reconciled after losing JWT');
SELECT ok((SELECT user_id IS NULL FROM private.account_deletion_requests WHERE request_id='a0622222-2222-4222-8222-222222222222'),'completed receipt has no user identity');
SELECT lives_ok($$SELECT public.complete_account_deletion('a0622222-2222-4222-8222-222222222222',repeat('1',64))$$,'completion is idempotent');
SELECT ok(EXISTS(SELECT 1 FROM auth.users WHERE id='a0611111-1111-4111-8111-111111111112'),'other Auth user remains intact');
SELECT * FROM finish();
ROLLBACK;
