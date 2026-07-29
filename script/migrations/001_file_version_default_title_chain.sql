-- FileVersion chains are identified by:
--   tenant_id + repository_id + default_title
--
-- Run the duplicate check first. Do not delete or merge historical rows
-- automatically; resolve any rows returned here before adding the constraint.
SELECT
    tenant_id,
    repository_id,
    default_title,
    version_no,
    COUNT(*) AS duplicate_count
FROM file_versions
GROUP BY tenant_id, repository_id, default_title, version_no
HAVING COUNT(*) > 1;

-- MySQL permits multiple NULL values in a UNIQUE(file_id, version_no) index,
-- so draft versions (whose file_id is NULL) need a chain-level constraint.
ALTER TABLE file_versions
    ADD CONSTRAINT uk_file_versions_default_title_version
    UNIQUE (tenant_id, repository_id, default_title, version_no);
