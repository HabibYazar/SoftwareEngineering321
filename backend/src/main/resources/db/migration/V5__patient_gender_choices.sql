-- Sprint 2: only Male and Female are accepted.
-- Existing patient rows are preserved.

UPDATE field_rules
SET allowed_values = ARRAY['Male', 'Female']::text[]
WHERE table_name = 'patients'
  AND column_name = 'gender';