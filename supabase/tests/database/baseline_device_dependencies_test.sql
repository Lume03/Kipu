BEGIN;
SELECT plan(8);
SELECT has_table('public','user_devices','device dependency is present after an empty reset');
SELECT ok((SELECT relrowsecurity FROM pg_class WHERE oid='public.user_devices'::regclass),
 'restored device table enables RLS');
INSERT INTO auth.users(id,email) VALUES
 ('a9000000-0000-4000-8000-000000000001','device-owner@kipu.test'),
 ('a9000000-0000-4000-8000-000000000002','device-other@kipu.test');
INSERT INTO public.user_devices(id,user_id,device_fingerprint,device_name) VALUES
 ('a9100000-0000-4000-8000-000000000001','a9000000-0000-4000-8000-000000000001','owner-device','Owner device'),
 ('a9100000-0000-4000-8000-000000000002','a9000000-0000-4000-8000-000000000002','other-device','Other device');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a9000000-0000-4000-8000-000000000001',true);
SELECT is((SELECT count(*)::integer FROM public.user_devices),1,'owner sees only its own device');
SELECT throws_ok($$INSERT INTO public.user_devices(user_id,device_fingerprint,device_name)
 VALUES ('a9000000-0000-4000-8000-000000000002','foreign-write','Foreign')$$,
 '42501',NULL,'owner cannot create a device for another user');
SELECT lives_ok($$UPDATE public.user_devices SET device_name='Renamed owner device'
 WHERE id='a9100000-0000-4000-8000-000000000001'$$,'owner can update its device');
UPDATE public.user_devices SET device_name='Tampered foreign device'
 WHERE id='a9100000-0000-4000-8000-000000000002';
SELECT set_config('request.jwt.claim.sub','a9000000-0000-4000-8000-000000000002',true);
SELECT is((SELECT count(*)::integer FROM public.user_devices),1,'second owner is equally isolated');
SELECT is((SELECT device_name FROM public.user_devices WHERE id='a9100000-0000-4000-8000-000000000002'),
 'Other device','cross-owner update changed no row');
RESET ROLE;
SELECT ok(NOT has_table_privilege('anon','public.user_devices','SELECT'),
 'anonymous role cannot read the restored device table');
SELECT * FROM finish();
ROLLBACK;
