BEGIN;
SELECT no_plan();
CREATE TEMP TABLE l9_tap(line text);
\ir _goal_test_fixture.sql.inc
\ir _recurrence_test_fixture.sql.inc

CREATE FUNCTION pg_temp.l9_sign(c jsonb) RETURNS jsonb LANGUAGE sql AS $$
 SELECT c || jsonb_build_object('request_hash',encode(extensions.digest(public._rec_json_canonical(c-ARRAY['operation_id','request_hash']),'sha256'),'hex'))
$$;
CREATE FUNCTION pg_temp.l9_revise(tx uuid, amount bigint, account uuid, note text)
RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE c jsonb; t public.transactions%ROWTYPE;
BEGIN
 SELECT * INTO t FROM public.transactions WHERE id=tx;
 c:=jsonb_build_object('contract_version',1,'command_type','REVISE_TRANSACTION',
 'idempotency_key',gen_random_uuid(),'transaction_id',tx,'expected_revision',t.revision,
 'depends_on_command_id',NULL,'reason','L9 regression','revised_payload',jsonb_build_object(
 'type',t.transaction_type,'operation_kind',t.operation_kind,'amount_minor',amount,'currency_code',t.currency_code,
 'source_account_id',account,'destination_account_id',NULL,'category_id',t.category_id,'merchant_id',NULL,
 'merchant_provisional_text',NULL,'occurred_at','2026-06-10T12:00:00.000Z','note',note));
 RETURN public.revise_transaction_v1(c||jsonb_build_object('request_hash',internal.movement_revision_hash_v1(c)));
END $$;

-- SQL3-01 / SQL4-02: exercise real compensating revisions and the public payment.
DO $$
DECLARE u uuid:='96000000-0000-4000-8000-000000000001';
 a uuid:='96000000-0000-4000-8000-000000000011';
 tx uuid:=gen_random_uuid(); card uuid:=gen_random_uuid(); liability uuid; r jsonb; n bigint;
 purchase uuid:=gen_random_uuid(); income_tx uuid:=gen_random_uuid(); income_account uuid:=gen_random_uuid();
BEGIN
 PERFORM set_config('request.jwt.claim.sub',u::text,true);
 INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,amount_minor,currency_code,status,category_id,occurred_at)
 VALUES(tx,u,a,'EXPENSE','STANDARD',90000,'PEN','CONFIRMED','00000000-0000-0000-0000-000000000001','2026-06-10T12:00:00Z');
 INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role)
 VALUES(tx,u,a,-90000,'PEN','PRIMARY');
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(tx,90000,a,'note edit')->>'status','APPLIED','SQL3-01: note edit applies');
 INSERT INTO l9_tap SELECT is(internal.goal_confirmed_balance(u,a),10000::bigint,'SQL3-01: note edit preserves spent backing');
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(tx,95000,a,'amount edit')->>'status','APPLIED','SQL4-02: amount edit applies');
 INSERT INTO l9_tap SELECT is(internal.goal_confirmed_balance(u,a),5000::bigint,'SQL4-02: net ledger includes revised expense exactly once');
 -- A stale legacy mirror must never be added to canonical opening ledger entries.
 UPDATE public.accounts SET initial_balance_minor_units=100000 WHERE id=a;
 liability:=internal.credit_liability_identity_v1('account',u,card);
 INSERT INTO public.accounts(id,user_id,name,account_type,currency_code) VALUES(liability,u,'L9 liability','CREDIT_LIABILITY','PEN');
 INSERT INTO public.cards(id,user_id,network,alias,last4,is_credit,credit_limit_minor,closing_day,due_day,account_id,currency_code,issuer)
 VALUES(card,u,'VISA','L9 credit','1234',true,100000,15,5,liability,'PEN','BCP');
 SELECT count(*) INTO n FROM internal.ledger_entries WHERE user_id=u;
 r:=public.pay_credit_card_v1(jsonb_build_object('operation_id',gen_random_uuid(),'card_id',card,'source_account_id',a,'amount_minor_units',20000,'currency','PEN','payload_hash','L9-pay'));
 INSERT INTO l9_tap SELECT is(r#>>'{error,code}','INSUFFICIENT_FUNDS','SQL4-02: payment exceeding revised funds rejected');
 INSERT INTO l9_tap SELECT is((SELECT count(*) FROM internal.ledger_entries WHERE user_id=u),n,'SQL4-02: rejected payment has no ledger effect');
 INSERT INTO public.transactions(id,user_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,status,category_id)
 VALUES(purchase,u,card,'EXPENSE','CARD_PURCHASE',30000,'PEN','CONFIRMED','00000000-0000-0000-0000-000000000001');
 INSERT INTO public.credit_installments(user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status)
 VALUES(u,purchase,1,current_date,30000,0,'PENDING');
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(tx,40000,a,'lower expense')->>'status','APPLIED','SQL4-02: further revision retains canonical effect');
 INSERT INTO l9_tap SELECT is(public.record_opening_adjustment_v1(jsonb_build_object('operation_id',gen_random_uuid(),
 'account_id',a,'reversal_movement_id',gen_random_uuid(),'adjustment_movement_id',gen_random_uuid(),
 'new_amount_minor_units',90000,'new_effective_at','2026-01-01T12:00:00Z'))->>'status','APPLIED','SQL4-02: opening correction applies');
 INSERT INTO l9_tap SELECT is(internal.goal_confirmed_balance(u,a),50000::bigint,'SQL4-02: corrected opening counted once with revised expense');
 INSERT INTO l9_tap SELECT is(public.pay_credit_card_v1(jsonb_build_object('operation_id',gen_random_uuid(),'card_id',card,
 'source_account_id',a,'amount_minor_units',20000,'currency','PEN','payload_hash','L9-valid'))->>'status','APPLIED','SQL4-02: payment within corrected funds succeeds');
 INSERT INTO l9_tap SELECT is(internal.goal_confirmed_balance(u,a),30000::bigint,'SQL4-02: accepted payment debits canonical funds exactly once');
 INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id) VALUES(income_account,u,'Revised income','BANK','PEN','GENERIC');
 INSERT INTO public.transactions(id,user_id,account_id,transaction_type,operation_kind,amount_minor,currency_code,status)
 VALUES(income_tx,u,income_account,'INCOME','STANDARD',1000,'PEN','CONFIRMED');
 INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role)
 VALUES(income_tx,u,income_account,1000,'PEN','PRIMARY');
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(income_tx,2000,income_account,'income revised')->>'status','APPLIED','SQL4-02: income revision applies');
 INSERT INTO l9_tap SELECT is(public.pay_credit_card_v1(jsonb_build_object('operation_id',gen_random_uuid(),'card_id',card,
 'source_account_id',income_account,'amount_minor_units',1500,'currency','PEN','payload_hash','L9-income'))->>'status','APPLIED','SQL4-02: revised income funds a valid payment');
 INSERT INTO l9_tap SELECT is(internal.goal_confirmed_balance(u,income_account),500::bigint,'SQL4-02: revised income and payment balance exact');
END $$;

-- SQL3-02: completed A plus active B/C cannot become three active goals.
DO $$
DECLARE u uuid:='96000000-0000-4000-8000-000000000001';
 a uuid:='96000000-0000-4000-8000-000000000011';
 src uuid:=gen_random_uuid(); dest uuid:=gen_random_uuid(); other uuid:=gen_random_uuid(); c jsonb; r jsonb;
BEGIN
 INSERT INTO public.goals(id,user_id,name,target_minor,currency_code,status) VALUES
 (src,u,'A',100,'PEN','COMPLETED'),(dest,u,'B',1000,'PEN','ACTIVE'),(other,u,'C',1000,'PEN','ACTIVE');
 INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,currency_code,backing_account_id,operation_id)
 VALUES(gen_random_uuid(),u,src,'ALLOCATION',100,100,'PEN',a,gen_random_uuid());
 c:=pg_temp.goal_cmd('REASSIGN_GOAL_RESERVE',src,1,jsonb_build_object('source_goal_id',src,'destination_goal_id',dest,
 'source_expected_revision',1,'destination_expected_revision',1,'source_event_id',gen_random_uuid(),
 'destination_event_id',gen_random_uuid(),'backing_account_id',a,'amount_minor',10,'currency_code','PEN'));
 r:=public.reassign_goal_reserve_v1(c);
 INSERT INTO l9_tap SELECT is(r->>'error','REPLACEMENT_REQUIRED','SQL3-02: Free reassignment rejects third active goal');
 INSERT INTO l9_tap SELECT is(internal.goal_reserved(u,src),100::bigint,'SQL3-02: rejection keeps source reserve');
 INSERT INTO l9_tap SELECT is(internal.goal_reserved(u,dest),0::bigint,'SQL3-02: rejection keeps destination reserve');
 INSERT INTO l9_tap SELECT is((SELECT count(*) FROM public.goals WHERE user_id=u AND status='ACTIVE'),2::bigint,'SQL3-02: states remain intact');
 -- Completing B simultaneously frees the slot needed by A.
 UPDATE public.goals SET target_minor=10 WHERE id=dest;
 INSERT INTO l9_tap SELECT is(public.reassign_goal_reserve_v1(c)->>'status','APPLIED','SQL3-02: atomic completion of destination frees a slot');
END $$;

-- SQL3-03,07,08,09: real RPCs, integral decimal JSON and mutable references.
DO $$
DECLARE u uuid:='a7000000-0000-4000-8000-000000000001';
 a uuid:='a7100000-0000-4000-8000-000000000001';
 cat uuid:='a7200000-0000-4000-8000-000000000001';
 v_id uuid:=gen_random_uuid(); c jsonb; upd jsonb; r jsonb; n bigint;
BEGIN
 PERFORM set_config('request.jwt.claim.sub',u::text,true);
 c:=pg_temp.l9_sign(jsonb_build_object('command_type','CREATE_RECURRENCE','contract_version',1,'operation_id',gen_random_uuid(),
 'recurrence_id',v_id,'payload',jsonb_build_object('amount_mode','FIXED','custom_interval_unit','WEEK','end_date',NULL,
 'frequency','CUSTOM','interval_count',1.0,'next_due_date','2026-01-15','start_date','2026-01-01','reminder_lead_days',2.0,
 'template_transaction',jsonb_build_object('concept','L9','type','EXPENSE','amount_minor',100.0,'currency_code','PEN',
 'source_account_id',a,'destination_account_id',NULL,'category_id',cat,'merchant_id',NULL,'merchant_provisional_text',NULL))));
 r:=public.create_recurrence_v1(c);
 INSERT INTO l9_tap SELECT is(r->>'status','APPLIED','SQL3-09: CREATE accepts integral decimal fields');
 SELECT count(*) INTO n FROM internal.sync_changes WHERE entity_id=v_id;
 UPDATE public.accounts SET is_archived=true WHERE id=a;
 INSERT INTO l9_tap SELECT is(public.create_recurrence_v1(c),r,'SQL3-03: retry after archive returns original receipt');
 INSERT INTO l9_tap SELECT is((SELECT count(*) FROM internal.sync_changes WHERE entity_id=v_id),n,'SQL3-03: retry emits no sync row');
 INSERT INTO l9_tap SELECT is((SELECT count(*) FROM public.recurrences WHERE id=v_id),1::bigint,'SQL3-03: retry creates no recurrence');
 UPDATE public.accounts SET is_archived=false WHERE id=a;
 upd:=pg_temp.l9_sign(jsonb_build_object('command_type','UPDATE_RECURRENCE','contract_version',1,'operation_id',gen_random_uuid(),
 'recurrence_id',v_id,'expected_revision',1.0,'payload',jsonb_build_object('reminder_lead_days',3.0)));
 INSERT INTO l9_tap SELECT is(public.update_recurrence_v1(upd)->>'status','APPLIED','SQL3-09: UPDATE accepts decimal revision and reminder');
 upd:=pg_temp.l9_sign(upd||jsonb_build_object('operation_id',gen_random_uuid(),'expected_revision',2,
 'payload',jsonb_build_object('interval_count',2,'reminder_lead_days',3)));
 INSERT INTO l9_tap SELECT is(public.update_recurrence_v1(upd)#>>'{error,code}','CADENCE_FIELDS_REQUIRED','SQL3-08: partial CUSTOM cadence change rejected');
 INSERT INTO l9_tap SELECT is((SELECT interval_count FROM public.recurrences WHERE recurrences.id=v_id),1::bigint,'SQL3-08: cadence unchanged');
 upd:=pg_temp.l9_sign(jsonb_build_object('command_type','SET_RECURRENCE_STATUS','contract_version',1,'operation_id',gen_random_uuid(),
 'recurrence_id',v_id,'expected_revision',2.0,'payload',jsonb_build_object('status','PAUSED')));
 INSERT INTO l9_tap SELECT is(public.set_recurrence_status_v1(upd)->>'status','APPLIED','SQL3-09: lifecycle accepts decimal revision');
 UPDATE public.categories SET category_type='INCOME' WHERE categories.id=cat;
 c:=pg_temp.l9_sign(c||jsonb_build_object('operation_id',gen_random_uuid(),'recurrence_id',gen_random_uuid()));
 INSERT INTO l9_tap SELECT is(public.create_recurrence_v1(c)#>>'{error,code}','REFERENCE_NOT_FOUND','SQL3-07: expense cannot use income category');
 INSERT INTO l9_tap SELECT ok(NOT EXISTS(SELECT 1 FROM internal.command_receipts WHERE idempotency_key=c->>'operation_id'),'SQL3-07: rejected category creates no receipt');
END $$;

-- SQL2-02 / SQL4-03: grouped instruments and Premium share creation rules.
DO $$
DECLARE u uuid:='a7000000-0000-4000-8000-000000000001';
 a uuid:='a7100000-0000-4000-8000-000000000001';
 card uuid:=gen_random_uuid(); target uuid:=gen_random_uuid(); tx uuid:=gen_random_uuid(); r jsonb;
BEGIN
 INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id)
 SELECT gen_random_uuid(),u,'L9 bank','BANK','PEN','GENERIC' FROM generate_series(1,3);
 INSERT INTO public.cards(id,user_id,network,alias,last4,is_credit,account_id,currency_code,is_archived,issuer)
 VALUES(card,u,'VISA','L9 debit','9876',false,a,'PEN',true,'BCP');
 r:=public.set_instrument_archived_v1(jsonb_build_object('operation_id',gen_random_uuid(),'instrument_id',card,'instrument_type','CARD','is_archived',false));
 INSERT INTO l9_tap SELECT is(r->>'status','APPLIED','SQL4-03: debit reactivates at four bank groups');
 INSERT INTO l9_tap SELECT is((SELECT count(DISTINCT quota_id) FROM private.instrument_quota_groups(u)),4::bigint,'SQL4-03: debit shares bank slot');
 UPDATE public.categories SET category_type='EXPENSE' WHERE user_id=u;
 INSERT INTO public.transactions(id,user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,status)
 VALUES(tx,u,a,'a7200000-0000-4000-8000-000000000001','EXPENSE','STANDARD',100,'PEN','CONFIRMED');
 INSERT INTO internal.ledger_entries(transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role) VALUES(tx,u,a,-100,'PEN','PRIMARY');
 SELECT id INTO target FROM public.accounts WHERE user_id=u AND id<>a LIMIT 1;
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(tx,100,target,'group move')->>'status','APPLIED','SQL2-02: Free grouped debit does not block account change');
 INSERT INTO public.accounts(id,user_id,name,account_type,currency_code,preset_id,is_archived) VALUES(gen_random_uuid(),u,'fifth','BANK','PEN','GENERIC',false),
 (gen_random_uuid(),u,'archived','BANK','PEN','GENERIC',true);
 SELECT id INTO target FROM public.accounts WHERE user_id=u AND is_archived LIMIT 1;
 BEGIN
   PERFORM public.set_instrument_archived_v1(jsonb_build_object('operation_id',gen_random_uuid(),'instrument_id',target,'instrument_type','ACCOUNT','is_archived',false));
   INSERT INTO l9_tap SELECT fail('SQL4-03: Free independent bank must be blocked');
 EXCEPTION WHEN raise_exception THEN
   INSERT INTO l9_tap SELECT ok(SQLERRM LIKE '%FREE_LIMIT_REACHED%','SQL4-03: Free independent bank remains blocked');
 END;
 INSERT INTO l9_tap SELECT ok((SELECT is_archived FROM public.accounts WHERE id=target),'SQL4-03: rejection rolls back archive state');
 UPDATE internal.test_plan_override_gate SET enabled=true;
 PERFORM public.dev_activate_plan(gen_random_uuid(),'PRO_LIFETIME');
 INSERT INTO l9_tap SELECT ok(private.is_user_pro(u),'Premium fixture active');
 INSERT INTO l9_tap SELECT is(pg_temp.l9_revise(tx,100,a,'premium move')->>'status','APPLIED','SQL2-02: Premium can change account above four banks');
 SELECT id INTO target FROM public.accounts WHERE user_id=u AND is_archived LIMIT 1;
 INSERT INTO l9_tap SELECT is(public.set_instrument_archived_v1(jsonb_build_object('operation_id',gen_random_uuid(),
 'instrument_id',target,'instrument_type','ACCOUNT','is_archived',false))->>'status','APPLIED','SQL4-03: Premium reactivates above four groups');
END $$;

-- SQL4-06 / decision 14: historical bounds, limit, scope and Lima midnight.
DO $$
DECLARE u uuid:='a7000000-0000-4000-8000-000000000001'; id uuid:=gen_random_uuid(); r record;
BEGIN
 INSERT INTO public.categories(id,user_id,name,origin,category_type)
 VALUES('a7200000-0000-4000-8000-000000000009',u,'Historical scope','CUSTOM','EXPENSE');
 INSERT INTO public.budgets(id,user_id,name,limit_minor,currency_code,period_type,category_id)
 VALUES(id,u,'L9 historical',90000,'PEN','WEEKLY',NULL);
 INSERT INTO public.budget_periods(user_id,budget_id,starts_on,ends_on,limit_minor,scope_category_id)
 VALUES(u,id,'2026-06-01','2026-07-01',30000,'a7200000-0000-4000-8000-000000000009');
 INSERT INTO public.transactions(user_id,account_id,category_id,transaction_type,operation_kind,amount_minor,currency_code,status,occurred_at)
 VALUES(u,'a7100000-0000-4000-8000-000000000001','a7200000-0000-4000-8000-000000000009','EXPENSE','STANDARD',500,'PEN','CONFIRMED','2026-07-01T03:00:00Z'),
 (u,'a7100000-0000-4000-8000-000000000001','a7200000-0000-4000-8000-000000000009','EXPENSE','STANDARD',700,'PEN','CONFIRMED','2026-06-02T12:00:00Z');
 SELECT * INTO r FROM public.get_budget_status(id,'2026-06-10');
 INSERT INTO l9_tap SELECT is(r.period_starts_on,'2026-06-01'::date,'SQL4-06: historical monthly start survives weekly calendar');
 INSERT INTO l9_tap SELECT is(r.period_ends_on,'2026-07-01'::date,'SQL4-06: historical exclusive end preserved');
 INSERT INTO l9_tap SELECT is(r.limit_minor,30000::bigint,'SQL4-06: historical limit preserved');
 INSERT INTO l9_tap SELECT is(r.consumed_minor,1200::bigint,'D14: omitted timezone counts June 30 Lima expense and entire month');
 INSERT INTO l9_tap SELECT is((SELECT consumed_minor FROM public.get_budget_status(id,'2026-06-10','UTC')),700::bigint,'D14: explicit timezone parameter remains supported');
END $$;

-- SQL3-06: mutable merchant/category references cannot invalidate accepted retries.
DO $$
DECLARE u uuid:='a7000000-0000-4000-8000-000000000001'; m uuid:=gen_random_uuid(); alias_id uuid:=gen_random_uuid(); c jsonb; pref jsonb;
BEGIN
 INSERT INTO public.merchant_services(id,name,normalized_name,is_system,is_active,priority)
 VALUES(m,'L9 retry merchant','l9 retry merchant',true,true,'B');
 INSERT INTO public.merchant_alias_rules(id,user_id,merchant_id,normalized_pattern,revision)
 VALUES(alias_id,u,m,'l9 original',1);
 c:=jsonb_build_object('operation_id',gen_random_uuid(),'rule_id',alias_id,'merchant_id',m,'normalized_pattern','l9 changed','expected_revision',1);
 INSERT INTO l9_tap SELECT is(public.upsert_merchant_alias_rule_v1(c)->>'status','APPLIED','SQL3-06: alias edit accepted');
 pref:=jsonb_build_object('operation_id',gen_random_uuid(),'preference_id',gen_random_uuid(),'merchant_id',m,'category_id','a7200000-0000-4000-8000-000000000001');
 INSERT INTO l9_tap SELECT is(public.upsert_merchant_category_preference_v1(pref)->>'status','APPLIED','SQL3-06: category preference accepted');
 UPDATE public.merchant_services SET is_active=false WHERE id=m;
 UPDATE public.categories SET is_active=false WHERE id='a7200000-0000-4000-8000-000000000001';
 INSERT INTO l9_tap SELECT is(public.upsert_merchant_alias_rule_v1(c)->>'status','DUPLICATE','SQL3-06: alias replay survives inactive merchant');
 INSERT INTO l9_tap SELECT is(public.upsert_merchant_category_preference_v1(pref)->>'status','DUPLICATE','SQL3-06: preference replay survives inactive references');
 INSERT INTO l9_tap SELECT is((SELECT revision FROM public.merchant_alias_rules WHERE id=alias_id),2::bigint,'SQL3-06: alias retry does not mutate revision');
END $$;

SELECT line FROM l9_tap;
SELECT * FROM finish();
ROLLBACK;
