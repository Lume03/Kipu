-- Light is the product default; SYSTEM and DARK remain explicit choices.
ALTER TABLE public.profiles ALTER COLUMN theme_mode SET DEFAULT 'LIGHT';
ALTER TABLE public.user_settings ALTER COLUMN theme_mode SET DEFAULT 'LIGHT';
