-- Soft-deleted files remain in the files table and are only visible through
-- the recycle-bin query. Existing rows stay active after this migration.
ALTER TABLE files
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '0 = active, 1 = moved to trash';

CREATE INDEX idx_files_tenant_deleted
    ON files (tenant_id, is_deleted, recent_update_time);
