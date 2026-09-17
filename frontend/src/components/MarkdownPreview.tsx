import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useTranslation } from "../i18n";
import { toMarkdownSource } from "../documentContent";

interface MarkdownPreviewProps {
  content: string;
}

export function MarkdownPreview({ content }: MarkdownPreviewProps) {
  const { t } = useTranslation();
  return (
    <div className="document-markdown-preview" aria-label={t("文档格式预览")}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>
        {toMarkdownSource(content)}
      </ReactMarkdown>
    </div>
  );
}
