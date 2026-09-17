-- Tracks whether a file's content should be rendered as Markdown or as
-- plain/edited text. Existing rows default to PLAIN (unchanged behavior);
-- new rows are set explicitly by FileServiceImpl#buildFile(AddFileDTO).
ALTER TABLE files
    ADD COLUMN content_format VARCHAR(16) NOT NULL DEFAULT 'PLAIN'
    COMMENT 'PLAIN or MARKDOWN; controls frontend rendering only, not the chunk-splitting pipeline';
