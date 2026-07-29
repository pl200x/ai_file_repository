import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useLocation, useNavigate } from "react-router";
import { api } from "./api";
import { DocumentList } from "./components/DocumentList";
import { EditorWorkspace } from "./components/EditorWorkspace";
import { PermissionManagementPage } from "./components/PermissionManagementPage";
import { RepositorySidebar } from "./components/RepositorySidebar";
import { Toast } from "./components/Toast";
import { TopBar } from "./components/TopBar";
import { TrashBinPage } from "./components/TrashBinPage";
import { DEFAULT_USER_ID, TENANT_ID } from "./config";
import {
  createDraftIdentifier,
  saveDraft,
} from "./draftStorage";
import { errorMessage } from "./format";
import { useToast } from "./hooks/useToast";
import { parseRoute, routes } from "./routing";
import type {
  FileDocument,
  KnowledgeRepository,
} from "./types";

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const route = useMemo(
    () => parseRoute(location.pathname),
    [location.pathname],
  );
  const { toast, showToast } = useToast();
  const [userId, setUserId] = useState(DEFAULT_USER_ID);
  const [repositories, setRepositories] = useState<
    KnowledgeRepository[]
  >([]);
  const [documents, setDocuments] = useState<FileDocument[]>([]);
  const [trashDocuments, setTrashDocuments] = useState<
    FileDocument[]
  >([]);
  const [repositoriesLoading, setRepositoriesLoading] =
    useState(true);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [trashLoading, setTrashLoading] = useState(false);
  const [deletingFileId, setDeletingFileId] = useState<
    number | null
  >(null);
  const [restoringFileId, setRestoringFileId] = useState<
    number | null
  >(null);
  const [creatingDocument, setCreatingDocument] = useState(false);
  const documentRequestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    const loadRepositories = async () => {
      setRepositoriesLoading(true);
      try {
        const response = await api.listRepositories(
          TENANT_ID,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          setRepositories(response.data ?? []);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          showToast(`知识库加载失败：${errorMessage(error)}`);
        }
      } finally {
        if (!controller.signal.aborted) {
          setRepositoriesLoading(false);
        }
      }
    };
    void loadRepositories();
    return () => controller.abort();
  }, [showToast]);

  useEffect(() => {
    if (
      route.mode === "home" &&
      !repositoriesLoading &&
      repositories[0]
    ) {
      navigate(routes.repository(repositories[0].id), {
        replace: true,
      });
    }
  }, [
    navigate,
    repositories,
    repositoriesLoading,
    route.mode,
  ]);

  const refreshDocuments = useCallback(
    async (signal?: AbortSignal) => {
      if (route.repoId === null) {
        setDocuments([]);
        return [];
      }

      const requestId = ++documentRequestIdRef.current;
      setDocumentsLoading(true);
      try {
        const response = await api.listFiles(
          TENANT_ID,
          route.repoId,
          signal,
        );
        const nextDocuments = response.data ?? [];
        if (
          requestId === documentRequestIdRef.current &&
          !signal?.aborted
        ) {
          setDocuments(nextDocuments);
        }
        return nextDocuments;
      } catch (error) {
        if (!signal?.aborted) {
          if (requestId === documentRequestIdRef.current) {
            setDocuments([]);
          }
          showToast(`文档列表加载失败：${errorMessage(error)}`);
        }
        throw error;
      } finally {
        if (
          requestId === documentRequestIdRef.current &&
          !signal?.aborted
        ) {
          setDocumentsLoading(false);
        }
      }
    },
    [route.repoId, showToast],
  );

  useEffect(() => {
    documentRequestIdRef.current += 1;
    setDocuments([]);
    if (route.repoId === null) {
      setDocumentsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    void refreshDocuments(controller.signal).catch(() => undefined);
    return () => controller.abort();
  }, [refreshDocuments, route.repoId]);

  useEffect(() => {
    if (route.mode !== "trash") {
      setTrashLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    const loadTrashBin = async () => {
      setTrashLoading(true);
      try {
        const response = await api.listTrashBin(
          TENANT_ID,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          setTrashDocuments(response.data ?? []);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setTrashDocuments([]);
          showToast(`回收站加载失败：${errorMessage(error)}`);
        }
      } finally {
        if (!controller.signal.aborted) {
          setTrashLoading(false);
        }
      }
    };

    void loadTrashBin();
    return () => controller.abort();
  }, [route.mode, showToast]);

  const currentRepository = repositories.find(
    (repository) => repository.id === route.repoId,
  );
  const permissionTargetDocument =
    route.mode === "permissions" && route.fileId !== null
      ? documents.find((document) => document.id === route.fileId)
      : undefined;

  const handleUserChange = useCallback(
    (nextUserId: number) => {
      setUserId(nextUserId);
      if (
        route.repoId !== null &&
        route.mode !== "permissions"
      ) {
        navigate(routes.repository(route.repoId));
      }
    },
    [navigate, route.mode, route.repoId],
  );

  const handleCreated = useCallback(
    (createdFileId: number) => {
      if (route.repoId === null) return;
      navigate(routes.document(route.repoId, createdFileId));
    },
    [navigate, route.repoId],
  );

  const handleCreate = useCallback(async () => {
    if (route.repoId === null) {
      showToast("请先选择知识库");
      return;
    }
    if (creatingDocument) return;

    const repositoryId = route.repoId;
    const generatedIdentifier = createDraftIdentifier();
    setCreatingDocument(true);

    try {
      const response = await api.initializeFileVersion({
        id: null,
        fileId: null,
        versionNo: 0,
        title: "",
        content: "",
        editorId: userId,
        openTime: null,
        lastMergeTime: null,
        repositoryId,
        tenantId: TENANT_ID,
        defaultTitle: generatedIdentifier,
      });
      const identifier = response.data?.defaultTitle?.trim();
      if (!identifier) {
        throw new Error("服务未返回草稿标识");
      }
      const versionNo = response.data?.versionNo;
      if (
        typeof versionNo !== "number" ||
        !Number.isSafeInteger(versionNo) ||
        versionNo < 1
      ) {
        throw new Error("服务未返回有效的初始版本号");
      }

      saveDraft(TENANT_ID, userId, repositoryId, {
        title: "",
        content: "",
        defaultTitle: identifier,
      });
      navigate(
        routes.newDocument(repositoryId, identifier, versionNo),
      );
    } catch (error) {
      showToast(`草稿创建失败：${errorMessage(error)}`);
    } finally {
      setCreatingDocument(false);
    }
  }, [
    creatingDocument,
    navigate,
    route.repoId,
    showToast,
    userId,
  ]);

  const handleEditorLoadFailure = useCallback(() => {
    if (route.repoId !== null) {
      navigate(routes.repository(route.repoId));
    }
  }, [navigate, route.repoId]);

  const handleMoveToTrash = useCallback(
    async (fileId: number) => {
      try {
        await api.moveToTrashBin(fileId, userId);
        setDocuments((current) =>
          current.filter((document) => document.id !== fileId),
        );
        showToast("文档已移入回收站", true);
        if (route.repoId !== null) {
          navigate(routes.repository(route.repoId));
        }
      } catch (error) {
        showToast(`移入回收站失败：${errorMessage(error)}`);
      }
    },
    [navigate, route.repoId, showToast, userId],
  );

  const handlePermanentDeleteFromDetail = useCallback(
    async (fileId: number) => {
      try {
        await api.deleteFile(fileId, userId);
        setDocuments((current) =>
          current.filter((document) => document.id !== fileId),
        );
        showToast("文档已永久删除", true);
        if (route.repoId !== null) {
          navigate(routes.repository(route.repoId));
        }
      } catch (error) {
        showToast(`永久删除失败：${errorMessage(error)}`);
      }
    },
    [navigate, route.repoId, showToast, userId],
  );

  const handlePermanentDelete = useCallback(
    async (fileId: number) => {
      if (
        deletingFileId !== null ||
        !window.confirm("永久删除后无法恢复，确定继续吗？")
      ) {
        return;
      }

      setDeletingFileId(fileId);
      try {
        await api.deleteFile(fileId, userId);
        setTrashDocuments((current) =>
          current.filter((document) => document.id !== fileId),
        );
        showToast("文档已永久删除", true);
      } catch (error) {
        showToast(`永久删除失败：${errorMessage(error)}`);
      } finally {
        setDeletingFileId(null);
      }
    },
    [deletingFileId, showToast, userId],
  );

  const handleRestoreFromTrash = useCallback(
    async (fileId: number) => {
      if (restoringFileId !== null || deletingFileId !== null) {
        return;
      }

      setRestoringFileId(fileId);
      try {
        await api.restoreFromTrashBin(fileId, userId);
        setTrashDocuments((current) =>
          current.filter((document) => document.id !== fileId),
        );
        showToast("文档已恢复", true);
      } catch (error) {
        showToast(`恢复失败：${errorMessage(error)}`);
      } finally {
        setRestoringFileId(null);
      }
    },
    [deletingFileId, restoringFileId, showToast, userId],
  );

  const refreshCurrentDocuments = useCallback(
    () => refreshDocuments(),
    [refreshDocuments],
  );

  const editorKey = `${route.mode}:${route.repoId ?? "none"}:${
    route.fileId ?? "none"
  }:${
    route.mode === "new" ? route.defaultTitle : "existing"
  }:${userId}`;

  return (
    <>
      <TopBar userId={userId} onUserChange={handleUserChange} />
      <div className="layout">
        <RepositorySidebar
          repositories={repositories}
          selectedRepositoryId={route.repoId}
          trashSelected={route.mode === "trash"}
          loading={repositoriesLoading}
          onSelect={(repositoryId) =>
            navigate(routes.repository(repositoryId))
          }
          onSelectTrash={() => navigate(routes.trash)}
        />
        {route.mode === "trash" ? (
          <TrashBinPage
            documents={trashDocuments}
            loading={trashLoading}
            deletingFileId={deletingFileId}
            restoringFileId={restoringFileId}
            onDelete={(fileId) => void handlePermanentDelete(fileId)}
            onRestore={(fileId) =>
              void handleRestoreFromTrash(fileId)
            }
          />
        ) : route.mode === "permissions" ? (
          <PermissionManagementPage
            targetType={route.targetType}
            targetId={route.targetId}
            targetTitle={
              route.targetType === "FILE"
                ? permissionTargetDocument?.title ??
                  `文档 #${route.targetId}`
                : currentRepository?.title ??
                  `知识库 #${route.targetId}`
            }
            userId={userId}
            showToast={showToast}
            onBack={() => {
              if (route.fileId !== null) {
                navigate(
                  routes.document(route.repoId, route.fileId),
                );
              } else {
                navigate(routes.repository(route.repoId));
              }
            }}
          />
        ) : (
          <>
            <DocumentList
              repositoryTitle={currentRepository?.title ?? "文档"}
              documents={documents}
              selectedFileId={
                route.mode === "edit" ? route.fileId : null
              }
              loading={documentsLoading}
              canCreate={route.repoId !== null}
              creating={creatingDocument}
              onCreate={() => void handleCreate()}
              onManagePermissions={() => {
                if (route.repoId !== null) {
                  navigate(
                    routes.repositoryPermissions(route.repoId),
                  );
                }
              }}
              onSelect={(fileId) => {
                if (route.repoId !== null) {
                  navigate(routes.document(route.repoId, fileId));
                }
              }}
            />
            {(route.mode === "new" || route.mode === "edit") &&
            route.repoId !== null ? (
              <EditorWorkspace
                key={editorKey}
                mode={route.mode}
                repositoryId={route.repoId}
                fileId={route.fileId}
                initialDefaultTitle={
                  route.mode === "new"
                    ? route.defaultTitle
                    : undefined
                }
                initialVersionNo={
                  route.mode === "new" ? route.versionNo : undefined
                }
                userId={userId}
                showToast={showToast}
                refreshDocuments={refreshCurrentDocuments}
                onCreated={handleCreated}
                onLoadFailure={handleEditorLoadFailure}
                onManagePermissions={(fileId) =>
                  navigate(
                    routes.filePermissions(route.repoId, fileId),
                  )
                }
                onPermanentDelete={
                  handlePermanentDeleteFromDetail
                }
                onMoveToTrash={handleMoveToTrash}
              />
            ) : (
              <main className="editor-panel">
                <div className="editor-empty">
                  <div className="empty-icon" aria-hidden="true">
                    📄
                  </div>
                  <p>
                    选择左侧文档开始编辑，或点击「添加文档」新建
                  </p>
                </div>
              </main>
            )}
          </>
        )}
      </div>
      <Toast toast={toast} />
    </>
  );
}
