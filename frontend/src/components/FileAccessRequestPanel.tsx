import { useState } from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import { buildFilePermissionRequest } from "../fileAccess";
import { errorMessage } from "../format";
import { useTranslation } from "../i18n";
import {
  EXPIRATION_OPTIONS,
  PERMISSION_LEVEL_OPTIONS,
} from "../permissionManagement";
import type { PermissionLevel } from "../types";

interface FileAccessRequestPanelProps {
  fileId: number;
  userId: number;
  checking: boolean;
  onRetry: () => void;
  showToast: (message: string, success?: boolean) => void;
}

const DEFAULT_EXPIRATION =
  EXPIRATION_OPTIONS[2]?.value ??
  EXPIRATION_OPTIONS[0]?.value ??
  86_400_000;

export function FileAccessRequestPanel({
  fileId,
  userId,
  checking,
  onRetry,
  showToast,
}: FileAccessRequestPanelProps) {
  const { t } = useTranslation();
  const [permissionLevel, setPermissionLevel] =
    useState<PermissionLevel>("READABLE");
  const [expiration, setExpiration] = useState(DEFAULT_EXPIRATION);
  const [requesting, setRequesting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const submitRequest = async (event: FormEvent) => {
    event.preventDefault();
    if (requesting) return;

    setRequesting(true);
    try {
      await api.requestPermission(
        buildFilePermissionRequest(
          userId,
          fileId,
          permissionLevel,
          expiration,
        ),
      );
      setSubmitted(true);
      showToast("权限申请已提交，等待管理员审批", true);
    } catch (error) {
      showToast(`权限申请失败：${errorMessage(error)}`);
    } finally {
      setRequesting(false);
    }
  };

  return (
    <div className="file-access-state">
      <section
        className="file-access-card"
        aria-labelledby="file-access-title"
      >
        <div className="file-access-lock" aria-hidden="true">
          🔒
        </div>
        <span className="file-access-eyebrow">{t("文档 #{id}", { id: fileId })}</span>
        <h1 id="file-access-title">{t("你还没有访问权限")}</h1>
        <p className="file-access-description">
          {t("选择所需权限并提交申请。管理员批准后，即可打开这份文档。")}
        </p>

        <form onSubmit={(event) => void submitRequest(event)}>
          <fieldset className="file-access-levels">
            <legend>{t("申请权限")}</legend>
            {PERMISSION_LEVEL_OPTIONS.map((option) => (
              <label
                className={`file-access-level ${
                  permissionLevel === option.value ? "selected" : ""
                }`}
                key={option.value}
              >
                <input
                  type="radio"
                  name="file-permission-level"
                  value={option.value}
                  checked={permissionLevel === option.value}
                  onChange={() => {
                    setPermissionLevel(option.value);
                    setSubmitted(false);
                  }}
                />
                <span>
                  <strong>{t(option.label)}</strong>
                  <small>{t(option.description)}</small>
                </span>
              </label>
            ))}
          </fieldset>

          <label className="file-access-expiration">
            <span>{t("有效期")}</span>
            <select
              value={expiration}
              onChange={(event) => {
                setExpiration(Number(event.target.value));
                setSubmitted(false);
              }}
            >
              {EXPIRATION_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {t(option.label)}
                </option>
              ))}
            </select>
          </label>

          {submitted && (
            <p className="file-access-submitted" role="status">
              {t("申请已提交。你可以等待审批，或修改选项后更新申请。")}
            </p>
          )}

          <button
            type="submit"
            className="btn btn-primary file-access-submit"
            disabled={requesting || checking}
          >
            {requesting
              ? t("提交中…")
              : submitted
                ? t("更新申请")
                : t("提交权限申请")}
          </button>
          <button
            type="button"
            className="btn btn-ghost file-access-retry"
            disabled={requesting || checking}
            onClick={onRetry}
          >
            {checking ? t("正在检查权限…") : t("重新检查权限")}
          </button>
        </form>
      </section>
    </div>
  );
}
