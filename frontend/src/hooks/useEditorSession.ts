import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { api, ApiError } from "../api";
import { AUTOSAVE_INTERVAL_MS, TENANT_ID } from "../config";
import {
  clearDraft,
  loadDraft,
  saveDraft,
} from "../draftStorage";
import { isFileAccessDenied } from "../fileAccess";
import { errorMessage, formatClock } from "../format";
import { resolveSubmitTitle } from "../documentTitle";
import {
  draftFromVersion,
  isEmptyDraft,
  latestFileVersion,
} from "../versionRecovery";
import type {
  DraftContent,
  FileDocument,
  FileVersion,
} from "../types";

const EMPTY_DRAFT: DraftContent = { title: "", content: "" };

interface UseEditorSessionOptions {
  mode: "new" | "edit";
  repositoryId: number;
  fileId: number | null;
  initialDefaultTitle?: string;
  initialVersionNo?: number;
  userId: number;
  showToast: (message: string, success?: boolean) => void;
  refreshDocuments: () => Promise<FileDocument[]>;
  onCreated: (fileId: number) => void;
  onLoadFailure: () => void;
}

function sameDraft(left: DraftContent, right: DraftContent) {
  return left.title === right.title && left.content === right.content;
}

export function useEditorSession({
  mode,
  repositoryId,
  fileId,
  initialDefaultTitle,
  initialVersionNo,
  userId,
  showToast,
  refreshDocuments,
  onCreated,
  onLoadFailure,
}: UseEditorSessionOptions) {
  const [restoredDraft] = useState(() => {
    if (mode !== "new") return null;
    const stored = loadDraft(
      TENANT_ID,
      userId,
      repositoryId,
      initialDefaultTitle ?? "",
    );
    return stored?.defaultTitle === initialDefaultTitle
      ? stored
      : null;
  });
  const [draft, setDraft] = useState<DraftContent>(() =>
    restoredDraft
      ? { title: restoredDraft.title, content: restoredDraft.content }
      : EMPTY_DRAFT,
  );
  const [lastSaved, setLastSaved] =
    useState<DraftContent>(EMPTY_DRAFT);
  const [defaultTitle, setDefaultTitle] = useState(() =>
    mode === "new"
      ? initialDefaultTitle?.trim() ||
        restoredDraft?.defaultTitle ||
        ""
      : "",
  );
  const [loadedFile, setLoadedFile] = useState<FileDocument | null>(
    null,
  );
  const [versions, setVersions] = useState<FileVersion[]>([]);
  const [versionsVisible, setVersionsVisible] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState<string | null>(
    null,
  );
  const [loading, setLoading] = useState(mode === "edit");
  const [ready, setReady] = useState(false);
  const [accessDenied, setAccessDenied] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [latestVersionNo, setLatestVersionNo] = useState(() =>
    mode === "new" &&
    typeof initialVersionNo === "number" &&
    Number.isSafeInteger(initialVersionNo) &&
    initialVersionNo > 0
      ? initialVersionNo
      : 0,
  );
  const [saveStatus, setSaveStatus] = useState(() =>
    restoredDraft &&
    (restoredDraft.title || restoredDraft.content)
      ? "已恢复本地草稿，内容变化后每 30 秒自动保存版本"
      : mode === "new"
        ? initialDefaultTitle
          ? "草稿已创建，内容变化后每 30 秒自动保存版本"
          : "草稿标识缺失"
        : "加载中…",
  );

  const activeRef = useRef(true);
  const draftRef = useRef(draft);
  const lastSavedRef = useRef(lastSaved);
  const defaultTitleRef = useRef(defaultTitle);
  const savingRef = useRef(false);
  const submittingRef = useRef(false);
  const latestVersionNoRef = useRef(latestVersionNo);
  const savingPromiseRef = useRef<Promise<unknown> | null>(null);
  const autosaveRef = useRef<() => void>(() => undefined);

  useEffect(() => {
    draftRef.current = draft;
  }, [draft]);

  useEffect(() => {
    lastSavedRef.current = lastSaved;
  }, [lastSaved]);

  useEffect(() => {
    defaultTitleRef.current = defaultTitle;
  }, [defaultTitle]);

  useEffect(
    () => () => {
      activeRef.current = false;
    },
    [],
  );

  const updateDefaultTitle = useCallback((value: string) => {
    defaultTitleRef.current = value;
    setDefaultTitle(value);
  }, []);

  const advanceLatestVersionNo = useCallback((value: number) => {
    if (!Number.isSafeInteger(value) || value < 1) {
      throw new Error("服务返回了无效的版本号");
    }
    if (value <= latestVersionNoRef.current) return;

    latestVersionNoRef.current = value;
    setLatestVersionNo(value);
  }, []);

  const recordSavedDraft = useCallback(
    (snapshot: DraftContent, status: string) => {
      if (!activeRef.current) return;
      lastSavedRef.current = snapshot;
      setLastSaved(snapshot);
      setSaveStatus(status);
    },
    [],
  );

  const refreshVersions = useCallback(
    async (signal?: AbortSignal) => {
      const identifier = defaultTitleRef.current;
      if (fileId === null && !identifier) return [];

      setVersionsLoading(true);
      setVersionsError(null);
      try {
        const response =
          fileId !== null
            ? await api.listVersions(fileId, userId, signal)
            : await api.listDraftVersions(
                TENANT_ID,
                repositoryId,
                identifier,
                userId,
                signal,
              );
        if (!activeRef.current || signal?.aborted) return [];

        const nextVersions = response.data ?? [];
        const latestVersion = latestFileVersion(nextVersions);
        if (!latestVersion) {
          throw new Error("版本链中没有可用版本");
        }
        const versionIdentifier =
          latestVersion.defaultTitle?.trim();
        if (!versionIdentifier) {
          throw new Error("版本链缺少 canonical defaultTitle");
        }
        if (
          identifier &&
          versionIdentifier !== identifier
        ) {
          throw new Error("版本链 defaultTitle 与当前文档不一致");
        }
        advanceLatestVersionNo(latestVersion.versionNo);
        setVersions(nextVersions);
        return nextVersions;
      } catch (error) {
        if (signal?.aborted) return [];
        const message = errorMessage(error);
        if (activeRef.current && message) {
          setVersionsError(message);
        }
        throw error;
      } finally {
        if (activeRef.current && !signal?.aborted) {
          setVersionsLoading(false);
        }
      }
    },
    [advanceLatestVersionNo, fileId, repositoryId, userId],
  );

  const saveVersion = useCallback(
    async (snapshot: DraftContent) => {
      const identifier = defaultTitleRef.current;
      if (!identifier || savingRef.current || submittingRef.current) {
        return;
      }

      savingRef.current = true;
      setSaving(true);
      setSaveStatus("自动保存中…");

      const request = api.addFileVersion({
        id: null,
        fileId,
        versionNo: latestVersionNoRef.current,
        title: snapshot.title,
        content: snapshot.content,
        editorId: userId,
        openTime: null,
        lastMergeTime: null,
        repositoryId,
        tenantId: TENANT_ID,
        defaultTitle: identifier,
      });
      savingPromiseRef.current = request;

      try {
        const response = await request;
        if (!activeRef.current) return;
        const canonicalIdentifier =
          response.data?.defaultTitle?.trim();
        if (!canonicalIdentifier) {
          throw new Error("服务未返回草稿标识");
        }
        if (canonicalIdentifier !== identifier) {
          updateDefaultTitle(canonicalIdentifier);
        }
        advanceLatestVersionNo(response.data.versionNo);
        const savedAt = new Date();
        recordSavedDraft(
          snapshot,
          `上次自动保存 ${formatClock(savedAt)}`,
        );
        void refreshVersions().catch(() => undefined);
      } catch (error) {
        if (!activeRef.current) return;
        if (
          error instanceof ApiError &&
          (error.code === 409 || error.httpStatus === 409)
        ) {
          const message =
            "自动保存冲突：服务器已有更新，请刷新文档后再继续编辑";
          setSaveStatus(message);
          showToast(message);
          return;
        }
        const message = errorMessage(error);
        setSaveStatus(`自动保存失败，将重试：${message}`);
      } finally {
        if (savingPromiseRef.current === request) {
          savingPromiseRef.current = null;
        }
        savingRef.current = false;
        if (activeRef.current) {
          setSaving(false);
        }
      }
    },
    [
      advanceLatestVersionNo,
      fileId,
      recordSavedDraft,
      refreshVersions,
      repositoryId,
      showToast,
      updateDefaultTitle,
      userId,
    ],
  );

  const autosave = useCallback(() => {
    if (
      !ready ||
      savingRef.current ||
      submittingRef.current ||
      sameDraft(draftRef.current, lastSavedRef.current)
    ) {
      return;
    }

    const snapshot = { ...draftRef.current };
    void saveVersion(snapshot);
  }, [ready, saveVersion]);

  useEffect(() => {
    autosaveRef.current = autosave;
  }, [autosave]);

  useEffect(() => {
    if (!ready || !defaultTitle) return undefined;
    const timerId = window.setInterval(
      () => autosaveRef.current(),
      AUTOSAVE_INTERVAL_MS,
    );
    return () => window.clearInterval(timerId);
  }, [defaultTitle, ready]);

  useEffect(() => {
    if (mode !== "new" || !defaultTitle) return;
    saveDraft(TENANT_ID, userId, repositoryId, {
      ...draft,
      defaultTitle,
    });
  }, [defaultTitle, draft, mode, repositoryId, userId]);

  useEffect(() => {
    if (mode !== "new") return undefined;
    const controller = new AbortController();
    const restoreLatestDraftVersion = async () => {
      try {
        const versionList = await refreshVersions(controller.signal);
        if (controller.signal.aborted || !activeRef.current) return;

        const latestVersion = latestFileVersion(versionList);
        if (!latestVersion) {
          throw new Error("未找到已初始化的草稿版本");
        }
        const canonicalIdentifier =
          latestVersion.defaultTitle?.trim();
        if (!canonicalIdentifier) {
          throw new Error("草稿版本链缺少 canonical defaultTitle");
        }
        if (canonicalIdentifier !== defaultTitleRef.current) {
          throw new Error("草稿版本链 defaultTitle 与当前路由不一致");
        }

        if (isEmptyDraft(draftRef.current)) {
          const recoveredDraft = draftFromVersion(latestVersion);
          lastSavedRef.current = recoveredDraft;
          setLastSaved(recoveredDraft);

          if (!isEmptyDraft(recoveredDraft)) {
            draftRef.current = recoveredDraft;
            setDraft(recoveredDraft);
            setSaveStatus(
              `已从自动保存版本 v${latestVersion.versionNo} 恢复`,
            );
          }
        }
        setReady(true);
      } catch (error) {
        if (controller.signal.aborted || !activeRef.current) return;
        const message = errorMessage(error);
        setReady(false);
        setSaveStatus(`草稿恢复失败：${message}`);
        showToast(`草稿恢复失败：${message}`);
      }
    };
    void restoreLatestDraftVersion();

    return () => controller.abort();
  }, [mode, refreshVersions, showToast]);

  useEffect(() => {
    if (mode !== "edit" || fileId === null) return undefined;

    const controller = new AbortController();
    const loadExistingFile = async () => {
      setLoading(true);
      setReady(false);
      setSaveStatus("加载中…");

      try {
        const fileResponse = await api.queryFile(
          fileId,
          userId,
          controller.signal,
        );
        if (controller.signal.aborted || !activeRef.current) return;
        if (!fileResponse.data) {
          throw new Error("目标文档不存在");
        }
        const file = fileResponse.data;

        setVersionsLoading(true);
        setVersionsError(null);
        let versionList: FileVersion[];
        let latestVersion: FileVersion;
        try {
          const versionResponse = await api.listVersions(
            fileId,
            userId,
            controller.signal,
          );
          versionList = versionResponse.data ?? [];
          const latestMetadata = latestFileVersion(versionList);
          if (!latestMetadata) {
            throw new Error("目标文档没有可恢复的版本");
          }
          const metadataIdentifier =
            latestMetadata.defaultTitle?.trim();
          if (!metadataIdentifier) {
            throw new Error(
              "目标文档版本链缺少 canonical defaultTitle",
            );
          }

          const detailResponse = await api.queryVersionDetail(
            fileId,
            latestMetadata.versionNo,
            userId,
            controller.signal,
          );
          if (!detailResponse.data) {
            throw new Error("最新文档版本不存在");
          }
          latestVersion = detailResponse.data;
          const detailIdentifier =
            latestVersion.defaultTitle?.trim();
          if (!detailIdentifier) {
            throw new Error(
              "最新文档版本缺少 canonical defaultTitle",
            );
          }
          if (detailIdentifier !== metadataIdentifier) {
            throw new Error(
              "版本列表与最新版本的 defaultTitle 不一致",
            );
          }
        } catch (error) {
          if (!controller.signal.aborted && activeRef.current) {
            setVersionsError(errorMessage(error));
          }
          throw error;
        } finally {
          if (!controller.signal.aborted && activeRef.current) {
            setVersionsLoading(false);
          }
        }

        if (controller.signal.aborted || !activeRef.current) return;
        const loadedDraft = draftFromVersion(latestVersion);
        const identifier = latestVersion.defaultTitle!.trim();

        setLoadedFile(file);
        setDraft(loadedDraft);
        draftRef.current = loadedDraft;
        setLastSaved(loadedDraft);
        lastSavedRef.current = loadedDraft;
        setVersions(versionList);
        updateDefaultTitle(identifier);
        advanceLatestVersionNo(latestVersion.versionNo);
        setAccessDenied(false);
        setLoading(false);
        setReady(true);
        setSaveStatus(
          `已加载最新版本 v${latestVersion.versionNo}，内容变化后每 30 秒自动保存`,
        );
      } catch (error) {
        if (controller.signal.aborted || !activeRef.current) return;
        setLoading(false);
        setReady(false);
        if (isFileAccessDenied(error)) {
          setAccessDenied(true);
          setSaveStatus("等待访问权限");
          return;
        }
        const message = errorMessage(error);
        showToast(`文档加载失败：${message}`);
        onLoadFailure();
      }
    };

    void loadExistingFile();
    return () => controller.abort();
  }, [
    fileId,
    mode,
    onLoadFailure,
    showToast,
    advanceLatestVersionNo,
    updateDefaultTitle,
    userId,
    reloadToken,
  ]);

  const retryLoad = useCallback(() => {
    setReloadToken((current) => current + 1);
  }, []);

  const updateDraft = useCallback(
    (field: keyof DraftContent, value: string) => {
      setDraft((current) => {
        const next = { ...current, [field]: value };
        draftRef.current = next;
        return next;
      });
    },
    [],
  );

  const toggleVersions = useCallback(() => {
    setVersionsVisible((visible) => {
      const nextVisible = !visible;
      if (nextVisible) {
        void refreshVersions().catch(() => undefined);
      }
      return nextVisible;
    });
  }, [refreshVersions]);

  const submit = useCallback(async () => {
    if (submittingRef.current) return;

    submittingRef.current = true;
    setSubmitting(true);

    try {
      const pendingSave = savingPromiseRef.current;
      if (pendingSave) {
        await pendingSave.catch(() => undefined);
      }
      if (!activeRef.current) return;

      // Capture after an in-flight autosave settles so Submit always uses the
      // latest editor state, not the snapshot from before the wait.
      const latestDraft = { ...draftRef.current };
      const resolvedTitle = resolveSubmitTitle(
        latestDraft.title,
        latestDraft.content,
      );
      const snapshot: DraftContent = {
        title: resolvedTitle.title,
        content: latestDraft.content,
      };
      const identifier = defaultTitleRef.current.trim();
      if (!identifier) {
        throw new Error("缺少草稿标识，请重新点击添加文档");
      }

      if (mode === "new") {
        const response = await api.addFile({
          repositoryId,
          ownerId: userId,
          title: snapshot.title,
          content: snapshot.content,
          writableList: String(userId),
          readableList: "",
          manageableList: String(userId),
          recentUpdateTime: null,
          latestModifiedUserId: userId,
          tenantId: TENANT_ID,
          isPrivate: false,
          defaultTitle: identifier,
          autoTitle: resolvedTitle.autoTitle,
        });
        if (!activeRef.current) return;

        const result = response.data;
        if (result?.fileId === null || result?.fileId === undefined) {
          throw new Error("服务未返回新文档 ID");
        }
        const canonicalIdentifier =
          result.defaultTitle?.trim() || identifier;
        advanceLatestVersionNo(result.versionNo);
        const submittedSnapshot = {
          title: result.title?.trim() || snapshot.title,
          content: snapshot.content,
        };
        updateDefaultTitle(canonicalIdentifier);
        setDraft(submittedSnapshot);
        draftRef.current = submittedSnapshot;
        recordSavedDraft(
          submittedSnapshot,
          `已提交 ${formatClock(new Date())}`,
        );
        clearDraft(
          TENANT_ID,
          userId,
          repositoryId,
          canonicalIdentifier,
        );
        showToast("文档创建成功", true);
        void refreshDocuments().catch(() => undefined);
        onCreated(result.fileId);
        return;
      }

      if (fileId === null) {
        throw new Error("缺少目标文档 ID");
      }

      const response = await api.updateFile({
        id: fileId,
        title: snapshot.title,
        content: snapshot.content,
        writableList:
          loadedFile?.writerList ?? String(userId),
        readableList: loadedFile?.readerList ?? "",
        manageableList: loadedFile?.manageableList ?? "",
        latestModifiedUserId: userId,
        isPrivate: loadedFile?.private ?? false,
        defaultTitle: identifier,
        autoTitle: resolvedTitle.autoTitle,
      });
      if (!activeRef.current) return;

      const result = response.data;
      if (result?.fileId === null || result?.fileId === undefined) {
        throw new Error("服务未返回目标文档 ID");
      }
      const resultFileId = result.fileId;
      const canonicalIdentifier =
        result.defaultTitle?.trim() || identifier;
      advanceLatestVersionNo(result.versionNo);
      const submittedSnapshot = {
        title: result.title?.trim() || snapshot.title,
        content: snapshot.content,
      };
      updateDefaultTitle(canonicalIdentifier);
      setDraft(submittedSnapshot);
      draftRef.current = submittedSnapshot;
      const submittedAt = new Date();
      recordSavedDraft(
        submittedSnapshot,
        `已提交 ${formatClock(submittedAt)}`,
      );
      setLoadedFile((current) =>
        current
          ? {
              ...current,
              id: resultFileId,
              title: submittedSnapshot.title,
              content: submittedSnapshot.content,
              latestModifiedUserId: userId,
            }
          : current,
      );
      showToast("提交成功", true);
      void refreshVersions().catch(() => undefined);
      void refreshDocuments().catch(() => undefined);
    } catch (error) {
      if (activeRef.current) {
        showToast(
          `${mode === "new" ? "创建" : "提交"}失败：${errorMessage(error)}`,
        );
      }
    } finally {
      submittingRef.current = false;
      if (activeRef.current) {
        setSubmitting(false);
      }
    }
  }, [
    advanceLatestVersionNo,
    fileId,
    loadedFile,
    mode,
    onCreated,
    recordSavedDraft,
    refreshDocuments,
    refreshVersions,
    repositoryId,
    showToast,
    updateDefaultTitle,
    userId,
  ]);

  return {
    draft,
    dirty: !sameDraft(draft, lastSaved),
    loading,
    ready,
    accessDenied,
    saving,
    submitting,
    saveStatus,
    latestVersionNo,
    versions,
    versionsVisible,
    versionsLoading,
    versionsError,
    updateDraft,
    toggleVersions,
    retryLoad,
    submit,
  };
}
