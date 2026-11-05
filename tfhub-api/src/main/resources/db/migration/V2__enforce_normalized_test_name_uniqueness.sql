DO $$
DECLARE
    conflicting_names TEXT;
BEGIN
    SELECT string_agg(
        format('%s (test IDs: %s)', normalized_name, test_ids),
        '; '
    )
    INTO conflicting_names
    FROM (
        SELECT
            lower(trim(name)) AS normalized_name,
            string_agg(id::text, ', ' ORDER BY id) AS test_ids
        FROM tests
        GROUP BY lower(trim(name))
        HAVING count(*) > 1
    ) AS duplicates;

    IF conflicting_names IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot enforce normalized test name uniqueness; resolve these duplicate names first: %',
            conflicting_names;
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_tests_normalized_name
    ON tests (lower(trim(name)));
