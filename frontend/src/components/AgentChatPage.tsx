import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useTranslation } from "../i18n";
import {
  AGENT_INPUT_MAX_LENGTH,
  isAgentInputValid,
  normalizeAgentInput,
  type AgentChatMessage,
} from "../agentChat";

const SUGGESTED_QUESTIONS = [
  "如何申请一个文件的读取权限？",
  "文档删除后还能恢复吗？",
  "帮我总结知识库里的权限规则",
  "把知识库里关于访问权限的说明扩写得更详细一些",
];

interface AgentChatPageProps {
  messages: AgentChatMessage[];
  loading: boolean;
  currentUserName: string;
  onSend: (message: string) => void;
  onReset: () => void;
}

export function AgentChatPage({
  messages,
  loading,
  currentUserName,
  onSend,
  onReset,
}: AgentChatPageProps) {
  const { t } = useTranslation();
  const [input, setInput] = useState("");
  const scrollAnchorRef = useRef<HTMLDivElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const normalizedInput = normalizeAgentInput(input);
  const canSend = !loading && isAgentInputValid(input);

  useEffect(() => {
    scrollAnchorRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "end",
    });
  }, [loading, messages]);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${textarea.scrollHeight}px`;
  }, [input]);

  const send = (message: string) => {
    const normalized = normalizeAgentInput(message);
    if (!isAgentInputValid(normalized) || loading) return;
    setInput("");
    onSend(normalized);
  };

  return (
    <main className="agent-panel">
      <header className="agent-header">
        <div className="agent-heading">
          <span className="agent-heading-icon" aria-hidden="true">
            AI
          </span>
          <div>
            <h1>{t("智能客服")}</h1>
            <p>{t("基于你有权限访问的知识库内容回答问题")}</p>
          </div>
        </div>
        <div className="agent-header-actions">
          <span className="agent-access-badge">
            <span aria-hidden="true">🔒</span>
            {t("{name} 的权限视角", { name: currentUserName })}
          </span>
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              setInput("");
              onReset();
            }}
          >
            {t("新对话")}
          </button>
        </div>
      </header>

      <section
        className="agent-conversation"
        aria-label={t("客服对话")}
        aria-live="polite"
      >
        <div className="agent-conversation-inner">
          {messages.length === 0 ? (
            <div className="agent-welcome">
              <span className="agent-welcome-icon" aria-hidden="true">
                ✦
              </span>
              <h2>{t("你好，我是知识库智能客服")}</h2>
              <p>
                {t("我会检索与你问题最相关的文档片段，并且只使用你有权限查看的内容作答。")}
              </p>
              <div className="agent-suggestions" aria-label={t("快捷问题")}>
                {SUGGESTED_QUESTIONS.map((question) => (
                  <button
                    type="button"
                    key={question}
                    onClick={() => send(t(question))}
                  >
                    <span>{t(question)}</span>
                    <span aria-hidden="true">→</span>
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="agent-message-list">
              {messages.map((message) => (
                <article
                  className={`agent-message ${message.role} ${
                    message.error ? "error" : ""
                  }`}
                  key={message.id}
                >
                  {message.role === "assistant" && (
                    <span className="agent-avatar" aria-hidden="true">
                      AI
                    </span>
                  )}
                  <div className="agent-message-content">
                    <span className="agent-message-author">
                      {message.role === "assistant"
                        ? t("智能客服")
                        : currentUserName}
                    </span>
                    {message.role === "assistant" && !message.error ? (
                      <div className="agent-message-body agent-markdown">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {message.localized ? t(message.content) : message.content}
                        </ReactMarkdown>
                      </div>
                    ) : (
                      <p className="agent-message-body">
                        {message.localized ? t(message.content) : message.content}
                      </p>
                    )}
                  </div>
                </article>
              ))}
              {loading && (
                <article className="agent-message assistant">
                  <span className="agent-avatar" aria-hidden="true">
                    AI
                  </span>
                  <div className="agent-message-content">
                    <span className="agent-message-author">{t("智能客服")}</span>
                    <div className="agent-typing" aria-label={t("正在思考")}>
                      <span />
                      <span />
                      <span />
                    </div>
                  </div>
                </article>
              )}
            </div>
          )}
          <div ref={scrollAnchorRef} />
        </div>
      </section>

      <footer className="agent-composer">
        <form
          className="agent-composer-inner"
          onSubmit={(event) => {
            event.preventDefault();
            if (canSend) send(normalizedInput);
          }}
        >
          <div className="agent-input-wrap">
            <textarea
              ref={textareaRef}
              value={input}
              rows={1}
              maxLength={AGENT_INPUT_MAX_LENGTH}
              placeholder={t("输入你想了解的文档、流程或权限问题…")}
              aria-label={t("发送给智能客服")}
              disabled={loading}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (
                  event.key === "Enter" &&
                  !event.shiftKey &&
                  !event.nativeEvent.isComposing
                ) {
                  event.preventDefault();
                  if (canSend) send(normalizedInput);
                }
              }}
            />
            <button
              type="submit"
              className="agent-send-button"
              disabled={!canSend}
              aria-label={t("发送消息")}
            >
              <span aria-hidden="true">↑</span>
            </button>
          </div>
          <div className="agent-composer-meta">
            <span>{t("Enter 发送 · Shift + Enter 换行")}</span>
            <span>
              {input.length}/{AGENT_INPUT_MAX_LENGTH}
            </span>
          </div>
        </form>
      </footer>
    </main>
  );
}
