-- Repair the explicitly known expense taxonomy, never infer custom roots from names or transactions.
-- Existing transaction/category links, personal presentations and active flags are preserved.
BEGIN;

-- Roots with children ordinarily cannot change type. This locked, atomic data correction
-- updates the whole branch and restores the guard before any other session can write.
LOCK TABLE public.categories IN ACCESS EXCLUSIVE MODE;
ALTER TABLE public.categories DISABLE TRIGGER trg_inherit_category_type_from_root;
CREATE TEMP TABLE kipu_expense_catalog_roots ON COMMIT DROP AS
SELECT id FROM public.categories
WHERE user_id IS NULL AND origin = 'SYSTEM' AND parent_id IS NULL
  AND id IN ('00000000-0000-0000-0000-000000000001'::uuid,'00000000-0000-0000-0000-000000000002'::uuid,'00000000-0000-0000-0000-000000000003'::uuid,'00000000-0000-0000-0000-000000000004'::uuid,'00000000-0000-0000-0000-000000000005'::uuid,'00000000-0000-0000-0000-000000000006'::uuid,'00000000-0000-0000-0000-000000000007'::uuid,'00000000-0000-0000-0000-000000000008'::uuid,'00000000-0000-0000-0000-000000000009'::uuid,'00000000-0000-0000-0000-000000000010'::uuid,'00000000-0000-0000-0000-000000000011'::uuid,'00000000-0000-0000-0000-000000000012'::uuid);
UPDATE public.categories
SET category_type = 'EXPENSE', remote_revision = remote_revision + 1,
    revision = revision + 1, updated_at = now()
WHERE category_type <> 'EXPENSE'
  AND (id IN (SELECT id FROM kipu_expense_catalog_roots)
       OR parent_id IN (SELECT id FROM kipu_expense_catalog_roots));
ALTER TABLE public.categories ENABLE TRIGGER trg_inherit_category_type_from_root;

-- Existing classifications stay valid for unrelated edits, including historical income
-- linked to one of the repaired expense categories. New assignments still validate type.
CREATE OR REPLACE FUNCTION private.validate_transaction_category_type()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    v_category_type text;
    v_transaction_type text := NEW.transaction_type::text;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.category_id IS NOT DISTINCT FROM OLD.category_id
           AND NEW.transaction_type IS NOT DISTINCT FROM OLD.transaction_type THEN
            RETURN NEW;
        END IF;
    END IF;
    IF v_transaction_type = 'TRANSFER' AND NEW.category_id IS NOT NULL THEN
        RAISE EXCEPTION 'Transfers cannot have a category' USING ERRCODE = '23514';
    END IF;
    IF NEW.category_id IS NULL OR v_transaction_type NOT IN ('EXPENSE', 'INCOME') THEN
        RETURN NEW;
    END IF;
    SELECT category_type INTO v_category_type FROM public.categories WHERE id = NEW.category_id;
    IF FOUND AND v_category_type NOT IN ('GENERAL', v_transaction_type) THEN
        RAISE EXCEPTION 'Category type % is incompatible with transaction type %',
            v_category_type, v_transaction_type USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

-- Keep the large revision RPC intact except for the existing-reference exemption,
-- matching MovementRevisionPlanner. Fail closed if its expected contract has changed.
DO $$
DECLARE
    v_definition text := pg_get_functiondef('internal.apply_movement_revision_v1(jsonb,text)'::regprocedure);
    v_old text := 'AND c.category_type IN (''GENERAL'',v_type)';
    v_new text := 'AND (c.category_type IN (''GENERAL'',v_type) OR (v_category=v_tx.category_id AND v_type=v_tx.transaction_type::text))';
BEGIN
    IF strpos(v_definition, v_new) > 0 THEN
        RETURN;
    ELSIF strpos(v_definition, v_old) = 0 THEN
        RAISE EXCEPTION 'Unexpected movement revision category contract';
    END IF;
    EXECUTE replace(v_definition, v_old, v_new);
END;
$$;

-- Old devices can have queued GENERAL subcategory creates under the repaired system
-- roots. Inherit the authoritative expense type, and recognize an otherwise identical
-- pre-correction receipt without rewriting that historical receipt or duplicating rows.
DO $$
DECLARE
    v_definition text := pg_get_functiondef('public.create_category_v1(jsonb)'::regprocedure);
    v_old text := 'IF v_requested_type IS NOT NULL AND v_requested_type <> v_parent.category_type THEN';
    v_new text := 'IF v_requested_type IS NOT NULL AND v_requested_type <> v_parent.category_type AND NOT (v_requested_type = ''GENERAL'' AND v_parent.user_id IS NULL AND v_parent.origin = ''SYSTEM'' AND v_parent.category_type = ''EXPENSE'' AND v_parent.id IN (''00000000-0000-0000-0000-000000000001''::uuid,''00000000-0000-0000-0000-000000000002''::uuid,''00000000-0000-0000-0000-000000000003''::uuid,''00000000-0000-0000-0000-000000000004''::uuid,''00000000-0000-0000-0000-000000000005''::uuid,''00000000-0000-0000-0000-000000000006''::uuid,''00000000-0000-0000-0000-000000000007''::uuid,''00000000-0000-0000-0000-000000000008''::uuid,''00000000-0000-0000-0000-000000000009''::uuid,''00000000-0000-0000-0000-000000000010''::uuid,''00000000-0000-0000-0000-000000000011''::uuid,''00000000-0000-0000-0000-000000000012''::uuid)) THEN';
    v_receipt_old text := 'v_receipt.command_payload = v_canonical_payload THEN';
    v_receipt_new text := '(v_receipt.command_payload = v_canonical_payload OR ((v_requested_type IS NULL OR v_requested_type = ''GENERAL'') AND v_parent.user_id IS NULL AND v_parent.origin = ''SYSTEM'' AND v_parent.category_type = ''EXPENSE'' AND v_parent.id IN (''00000000-0000-0000-0000-000000000001''::uuid,''00000000-0000-0000-0000-000000000002''::uuid,''00000000-0000-0000-0000-000000000003''::uuid,''00000000-0000-0000-0000-000000000004''::uuid,''00000000-0000-0000-0000-000000000005''::uuid,''00000000-0000-0000-0000-000000000006''::uuid,''00000000-0000-0000-0000-000000000007''::uuid,''00000000-0000-0000-0000-000000000008''::uuid,''00000000-0000-0000-0000-000000000009''::uuid,''00000000-0000-0000-0000-000000000010''::uuid,''00000000-0000-0000-0000-000000000011''::uuid,''00000000-0000-0000-0000-000000000012''::uuid) AND v_receipt.command_payload->>''category_type'' = ''GENERAL'' AND (v_receipt.command_payload - ''category_type'') = (v_canonical_payload - ''category_type''))) THEN';
BEGIN
    IF strpos(v_definition, v_new) > 0 AND strpos(v_definition, v_receipt_new) > 0 THEN
        RETURN;
    END IF;
    IF strpos(v_definition, v_old) = 0 OR strpos(v_definition, v_receipt_old) = 0 THEN
        RAISE EXCEPTION 'Unexpected create category inheritance/receipt contract';
    END IF;
    EXECUTE replace(replace(v_definition, v_old, v_new), v_receipt_old, v_receipt_new);
END;
$$;
COMMIT;
