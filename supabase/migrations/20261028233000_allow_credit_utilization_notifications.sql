-- EP-NOT FR-002: the notification center recognizes CREDIT_THRESHOLD and the historical
-- CREDIT_UTILIZATION_THRESHOLD_CROSSED type emitted by private.emit_credit_utilization_crossings.
-- The remote CHECK only allowed BUDGET_ALERT/BILLING_DUE/GOAL_REACHED/SYSTEM, so any card purchase
-- crossing 50/80/100% of the limit aborted the whole register_transaction_v1 call.
ALTER TABLE public.app_notifications
    DROP CONSTRAINT IF EXISTS app_notifications_notification_type_check;

ALTER TABLE public.app_notifications
    ADD CONSTRAINT app_notifications_notification_type_check CHECK (
        notification_type = ANY (ARRAY[
            'BUDGET_ALERT',
            'BILLING_DUE',
            'GOAL_REACHED',
            'SYSTEM',
            'CREDIT_THRESHOLD',
            'CREDIT_UTILIZATION_THRESHOLD_CROSSED'
        ]::text[])
    );
