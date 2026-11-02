BEGIN;
SELECT plan(3);

SELECT has_function(
    'internal',
    'register_transaction_pre_hash_v2',
    ARRAY['jsonb'],
    'The canonical purchase registration function remains available'
);

SELECT ok(
    pg_catalog.strpos(
        pg_catalog.pg_get_functiondef('internal.register_transaction_pre_hash_v2(jsonb)'::regprocedure),
        'CREDIT_LIMIT_EXCEEDED'
    ) = 0,
    'P10 no longer rejects a purchase for crossing the card limit'
);

SELECT ok(
    pg_catalog.strpos(
        pg_catalog.pg_get_functiondef('public.register_transaction_v1_pre_liability_s3(jsonb)'::regprocedure),
        'v_after_debt := v_before_debt + v_financed_total'
    ) > 0,
    'The full purchase amount still contributes to outstanding debt'
);

SELECT * FROM finish();
ROLLBACK;
