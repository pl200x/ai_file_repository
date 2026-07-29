import {
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type SyntheticEvent,
} from "react";
import {
  insertImageAtSelection,
  parseDocumentContent,
  serializeDocumentContent,
  type DocumentBlock,
} from "../documentContent";
import {
  convertImageFileToDataUrl,
  MAX_IMAGE_SIZE_BYTES,
} from "../imageUpload";

interface DocumentContentEditorProps {
  value: string;
  onChange: (value: string) => void;
  onError: (message: string) => void;
  placeholder: string;
  disabled?: boolean;
}

interface ContentSelection {
  start: number;
  end: number;
}

type TextDocumentBlock = Extract<DocumentBlock, { type: "text" }>;

function resizeTextArea(textArea: HTMLTextAreaElement) {
  textArea.style.height = "0px";
  textArea.style.height = `${Math.max(38, textArea.scrollHeight)}px`;
}

function errorText(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : "图片上传或转换失败，请重试";
}

export function DocumentContentEditor({
  value,
  onChange,
  onError,
  placeholder,
  disabled = false,
}: DocumentContentEditorProps) {
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const textAreaRefs = useRef(new Map<number, HTMLTextAreaElement>());
  const selectionRef = useRef<ContentSelection | null>(null);
  const pendingInsertionRef = useRef<ContentSelection | null>(null);
  const pendingCaretRef = useRef<number | null>(null);
  const valueRef = useRef(value);
  valueRef.current = value;

  const parsedBlocks = useMemo(
    () => parseDocumentContent(value),
    [value],
  );
  const canonicalContent = useMemo(
    () => serializeDocumentContent(parsedBlocks),
    [parsedBlocks],
  );
  const editorBlocks = useMemo<DocumentBlock[]>(() => {
    const lastBlock = parsedBlocks.at(-1);
    if (!lastBlock || lastBlock.type === "text") {
      return parsedBlocks;
    }
    return [
      ...parsedBlocks,
      {
        type: "text",
        content: "",
        start: canonicalContent.length + 1,
      },
    ];
  }, [canonicalContent.length, parsedBlocks]);

  useLayoutEffect(() => {
    const caretOffset = pendingCaretRef.current;
    if (caretOffset === null) return;

    const targetBlock =
      editorBlocks.find(
        (block) =>
          block.type === "text" &&
          caretOffset >= block.start &&
          caretOffset <= block.start + block.content.length,
      ) ??
      [...editorBlocks]
        .reverse()
        .find((block) => block.type === "text");

    if (!targetBlock || targetBlock.type !== "text") return;
    const target = textAreaRefs.current.get(targetBlock.start);
    if (!target) return;

    const localOffset = Math.max(
      0,
      Math.min(
        targetBlock.content.length,
        caretOffset - targetBlock.start,
      ),
    );
    target.focus();
    target.setSelectionRange(localOffset, localOffset);
    selectionRef.current = {
      start: targetBlock.start + localOffset,
      end: targetBlock.start + localOffset,
    };
    pendingCaretRef.current = null;
  }, [editorBlocks]);

  const rememberSelection = (
    block: TextDocumentBlock,
    event: SyntheticEvent<HTMLTextAreaElement>,
  ) => {
    const target = event.currentTarget;
    selectionRef.current = {
      start: block.start + target.selectionStart,
      end: block.start + target.selectionEnd,
    };
  };

  const updateTextBlock = (
    blockIndex: number,
    block: TextDocumentBlock,
    event: ChangeEvent<HTMLTextAreaElement>,
  ) => {
    const nextBlocks = editorBlocks.map((candidate, index) =>
      index === blockIndex && candidate.type === "text"
        ? { ...candidate, content: event.currentTarget.value }
        : candidate,
    );
    selectionRef.current = {
      start: block.start + event.currentTarget.selectionStart,
      end: block.start + event.currentTarget.selectionEnd,
    };
    onChange(serializeDocumentContent(nextBlocks));
  };

  const openImagePicker = () => {
    if (disabled || uploading) return;
    pendingInsertionRef.current = selectionRef.current ?? {
      start: canonicalContent.length,
      end: canonicalContent.length,
    };
    fileInputRef.current?.click();
  };

  const handleImageSelection = async (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (!file) return;

    setUploading(true);
    try {
      const dataUrl = await convertImageFileToDataUrl(file);
      const currentContent = valueRef.current;
      const fallbackOffset = serializeDocumentContent(
        parseDocumentContent(currentContent),
      ).length;
      const selection = pendingInsertionRef.current ?? {
        start: fallbackOffset,
        end: fallbackOffset,
      };
      const insertion = insertImageAtSelection(
        currentContent,
        selection.start,
        selection.end,
        dataUrl,
      );
      pendingCaretRef.current = insertion.caretOffset;
      selectionRef.current = {
        start: insertion.caretOffset,
        end: insertion.caretOffset,
      };
      pendingInsertionRef.current = null;
      onChange(insertion.content);
    } catch (error) {
      pendingInsertionRef.current = null;
      onError(errorText(error));
    } finally {
      setUploading(false);
    }
  };

  const removeImage = (blockIndex: number, blockStart: number) => {
    const nextBlocks = parsedBlocks.filter(
      (_block, index) => index !== blockIndex,
    );
    const nextContent = serializeDocumentContent(nextBlocks);
    pendingCaretRef.current = Math.min(blockStart, nextContent.length);
    onChange(nextContent);
  };

  return (
    <section
      className={`document-content-editor ${
        disabled ? "disabled" : ""
      }`}
      aria-label="文档正文编辑器"
    >
      <div className="content-editor-toolbar">
        <button
          type="button"
          className="btn btn-ghost btn-sm image-upload-button"
          onClick={openImagePicker}
          disabled={disabled || uploading}
        >
          {uploading ? "图片处理中…" : "插入图片"}
        </button>
        <span className="image-upload-help">
          PNG、JPG、GIF、WebP，单张不超过{" "}
          {MAX_IMAGE_SIZE_BYTES / 1024 / 1024} MB
        </span>
        <input
          ref={fileInputRef}
          className="visually-hidden"
          type="file"
          accept=".png,.jpg,.jpeg,.gif,.webp,image/png,image/jpeg,image/gif,image/webp"
          onChange={(event) => void handleImageSelection(event)}
          disabled={disabled || uploading}
          aria-label="选择要插入的图片"
        />
      </div>
      <div className="content-block-list">
        {editorBlocks.map((block, blockIndex) => {
          if (block.type === "image") {
            return (
              <figure
                className="content-image-block"
                key={`image-${block.start}`}
              >
                <img src={block.dataUrl} alt="文档图片" />
                <button
                  type="button"
                  className="image-remove-button"
                  onClick={() => removeImage(blockIndex, block.start)}
                  disabled={disabled}
                  aria-label="删除这张图片"
                  title="删除图片"
                >
                  删除
                </button>
              </figure>
            );
          }

          const followsImage =
            blockIndex > 0 &&
            editorBlocks[blockIndex - 1]?.type === "image";
          return (
            <textarea
              key={`text-${block.start}`}
              ref={(textArea) => {
                if (textArea) {
                  textAreaRefs.current.set(block.start, textArea);
                  resizeTextArea(textArea);
                } else {
                  textAreaRefs.current.delete(block.start);
                }
              }}
              className="content-text-block"
              value={block.content}
              onChange={(event) =>
                updateTextBlock(blockIndex, block, event)
              }
              onSelect={(event) => rememberSelection(block, event)}
              onClick={(event) => rememberSelection(block, event)}
              onKeyUp={(event) => rememberSelection(block, event)}
              onInput={(event) => resizeTextArea(event.currentTarget)}
              placeholder={
                block.content
                  ? undefined
                  : followsImage
                    ? "在图片下方继续输入…"
                    : placeholder
              }
              aria-label={`文档正文文本区域 ${blockIndex + 1}`}
              disabled={disabled}
              rows={1}
            />
          );
        })}
      </div>
    </section>
  );
}
