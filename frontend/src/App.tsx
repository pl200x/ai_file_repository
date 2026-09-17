import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useLocation, useNavigate } from "react-router";
import { api } from "./api";
import {
  createAgentSessionId,
  type AgentChatMessage,
} from "./agentChat";
import { AgentChatPage } from "./components/AgentChatPage";
import { DocumentList } from "./components/DocumentList";
import { EditorWorkspace } from "./components/EditorWorkspace";
import { PermissionManagementPage } from "./components/PermissionManagementPage";
import { NotificationPage } from "./components/NotificationPage";
import { RepositorySidebar } from "./components/RepositorySidebar";
import { SearchPage } from "./components/SearchPage";
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
import { useTranslation } from "./i18n";
import { assertPdfSignature, validatePdfFile } from "./pdfUpload";
import { validateMarkdownFile } from "./markdownUpload";
import { SEARCH_TOP_K } from "./chunkSearch";
import { NOTIFICATION_PAGE_SIZE } from "./notifications";
import { parseRoute, routes } from "./routing";
import type {
  ChunkHit,
  FileDocument,
  KnowledgeRepository,
  NotificationItem,
  NotificationPage as NotificationPageData,
  UserSummary,
} from "./types";

//轮询间隔：铃铛角标不需要实时，30s 足够且不给后端压力
const UNREAD_POLL_INTERVAL_MS = 30_000;
const MIN_DOCUMENT_PANEL_WIDTH = 292;
const MIN_EDITOR_WIDTH = 240;
const RESIZE_HANDLE_WIDTH = 8;

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const route = useMemo(
    () => parseRoute(location.pathname),
    [location.pathname],
  );
  const { toast, showToast } = useToast();
  const { t } = useTranslation();
  const layoutRef = useRef<HTMLDivElement | null>(null);
  const sidebarRef = useRef<HTMLDivElement | null>(null);
  const documentPanelRef = useRef<HTMLDivElement | null>(null);
  const resizeStartRef = useRef<{ x: number; width: number } | null>(null);
  const [documentPanelWidth, setDocumentPanelWidth] = useState(320);
  const [maxDocumentPanelWidth, setMaxDocumentPanelWidth] = useState(320);
  const [userId, setUserId] = useState(DEFAULT_USER_ID);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);
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
  const [notificationPage, setNotificationPage] =
    useState<NotificationPageData | null>(null);
  const [notificationsLoading, setNotificationsLoading] =
    useState(false);
  const [notificationPageNo, setNotificationPageNo] = useState(1);
  const [unreadCount, setUnreadCount] = useState(0);
  const [markingNotificationId, setMarkingNotificationId] =
    useState<number | null>(null);
  const [deletingFileId, setDeletingFileId] = useState<
    number | null
  >(null);
  const [restoringFileId, setRestoringFileId] = useState<
    number | null
  >(null);
  const [creatingDocument, setCreatingDocument] = useState(false);
  const [uploadingPdf, setUploadingPdf] = useState(false);
  const [uploadingMarkdown, setUploadingMarkdown] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchHits, setSearchHits] = useState<ChunkHit[]>([]);
  const [searchTopK, setSearchTopK] = useState(SEARCH_TOP_K);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [agentSessionId, setAgentSessionId] = useState(() =>
    createAgentSessionId(DEFAULT_USER_ID),
  );
  const [agentMessages, setAgentMessages] = useState<
    AgentChatMessage[]
  >([]);
  const [agentLoading, setAgentLoading] = useState(false);
  const agentRequestRef = useRef<AbortController | null>(null);
  const documentRequestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    const loadUsers = async () => {
      setUsersLoading(true);
      try {
        const response = await api.listUsers(
          TENANT_ID,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          const nextUsers = response.data ?? [];
          setUsers(nextUsers);
          setUserId((currentUserId) =>
            nextUsers.some((user) => user.id === currentUserId)
              ? currentUserId
              : (nextUsers[0]?.id ?? currentUserId),
          );
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setUsers([]);
          showToast(`用户列表加载失败：${errorMessage(error)}`);
        }
      } finally {
        if (!controller.signal.aborted) {
          setUsersLoading(false);
        }
      }
    };
    void loadUsers();
    return () => controller.abort();
  }, [showToast]);

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

  //角标独立于列表刷新：切用户立即拉一次，之后定时轮询。
  //拉取失败静默处理，角标不是关键路径，不该弹 toast 打断用户
  useEffect(() => {
    const controller = new AbortController();
    const loadUnreadCount = async () => {
      try {
        const response = await api.notificationUnreadCount(
          userId,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          setUnreadCount(response.data ?? 0);
        }
      } catch {
        // 角标失败不打断用户，等下一次轮询
      }
    };

    void loadUnreadCount();
    const timer = window.setInterval(
      () => void loadUnreadCount(),
      UNREAD_POLL_INTERVAL_MS,
    );
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [userId]);

  //换用户时回到第一页，避免带着上一个用户的页码去查
  useEffect(() => {
    setNotificationPageNo(1);
  }, [userId]);

  //命中的可见性是按当前用户算出来的，换人之后旧结果就是上一个人的视角，必须丢掉
  useEffect(() => {
    setSearchHits([]);
    setSearched(false);
  }, [userId]);

  // 对话记忆由 userId:sessionId 隔离。切换用户时终止旧请求并开启新会话，
  // 防止把前一个用户的回答或权限视角带到新用户。
  useEffect(() => {
    agentRequestRef.current?.abort();
    agentRequestRef.current = null;
    setAgentLoading(false);
    setAgentMessages([]);
    setAgentSessionId(createAgentSessionId(userId));
  }, [userId]);

  useEffect(
    () => () => agentRequestRef.current?.abort(),
    [],
  );

  useEffect(() => {
    if (route.mode !== "notifications") {
      setNotificationsLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    const loadNotifications = async () => {
      setNotificationsLoading(true);
      try {
        const response = await api.listNotifications(
          userId,
          notificationPageNo,
          NOTIFICATION_PAGE_SIZE,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          setNotificationPage(response.data ?? null);
          setUnreadCount(response.data?.unreadCount ?? 0);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setNotificationPage(null);
          showToast(`通知加载失败：${errorMessage(error)}`);
        }
      } finally {
        if (!controller.signal.aborted) {
          setNotificationsLoading(false);
        }
      }
    };

    void loadNotifications();
    return () => controller.abort();
  }, [notificationPageNo, route.mode, showToast, userId]);

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
        !window.confirm(t("永久删除后无法恢复，确定继续吗？"))
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
    [deletingFileId, showToast, t, userId],
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

  const handleSearch = useCallback(
    async (query: string, topK: number) => {
      setSearchQuery(query);
      setSearchTopK(topK);
      setSearchLoading(true);
      try {
        const response = await api.searchChunks(query, userId, topK);
        setSearchHits(response.data ?? []);
        setSearched(true);
      } catch (error) {
        setSearchHits([]);
        setSearched(true);
        showToast(`搜索失败：${errorMessage(error)}`);
      } finally {
        setSearchLoading(false);
      }
    },
    [showToast, userId],
  );

  const handleAgentSend = useCallback(
    async (message: string) => {
      if (agentLoading) return;

      const requestId = `${agentSessionId}-${Date.now()}`;
      const controller = new AbortController();
      agentRequestRef.current?.abort();
      agentRequestRef.current = controller;
      setAgentMessages((current) => [
        ...current,
        {
          id: `${requestId}-user`,
          role: "user",
          content: message,
        },
      ]);
      setAgentLoading(true);

      try {
        const response = await api.chatWithAgent(
          agentSessionId,
          message,
          userId,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        const responseText = response.data?.trim();
        setAgentMessages((current) => [
          ...current,
          {
            id: `${requestId}-assistant`,
            role: "assistant",
            content:
              responseText ||
              "没有获得有效回答，请换个说法再试一次。",
            localized: !responseText,
          },
        ]);
      } catch (error) {
        if (controller.signal.aborted) return;
        const detail = errorMessage(error);
        setAgentMessages((current) => [
          ...current,
          {
            id: `${requestId}-error`,
            role: "assistant",
            content: detail
              ? `抱歉，这次没有完成检索：${detail}`
              : "抱歉，这次没有完成检索，请稍后重试。",
            error: true,
            localized: true,
          },
        ]);
        showToast(`智能客服请求失败：${detail || "请稍后重试"}`);
      } finally {
        if (agentRequestRef.current === controller) {
          agentRequestRef.current = null;
          setAgentLoading(false);
        }
      }
    },
    [agentLoading, agentSessionId, showToast, userId],
  );

  const handleAgentReset = useCallback(() => {
    agentRequestRef.current?.abort();
    agentRequestRef.current = null;
    setAgentLoading(false);
    setAgentMessages([]);
    setAgentSessionId(createAgentSessionId(userId));
  }, [userId]);

  const handleMarkNotificationRead = useCallback(
    async (notification: NotificationItem) => {
      if (markingNotificationId !== null) return;
      const nextRead = !notification.read;
      setMarkingNotificationId(notification.id);
      try {
        await api.markNotificationRead({
          id: notification.id,
          receiverId: userId,
          read: nextRead,
        });
        //本地就地更新，省掉一次整页重查；未读数同步加减
        setNotificationPage((current) =>
          current === null
            ? current
            : {
                ...current,
                unreadCount: Math.max(
                  0,
                  current.unreadCount + (nextRead ? -1 : 1),
                ),
                notifications: current.notifications.map((item) =>
                  item.id === notification.id
                    ? { ...item, read: nextRead }
                    : item,
                ),
              },
        );
        setUnreadCount((current) =>
          Math.max(0, current + (nextRead ? -1 : 1)),
        );
      } catch (error) {
        showToast(`更新已读状态失败：${errorMessage(error)}`);
      } finally {
        setMarkingNotificationId(null);
      }
    },
    [markingNotificationId, showToast, userId],
  );

  //点通知跳到它指向的对象；未读的顺手标记已读
  const handleOpenNotificationTarget = useCallback(
    (notification: NotificationItem) => {
      if (!notification.read) {
        void handleMarkNotificationRead(notification);
      }
      if (notification.targetType === "KNOWLEDGE_REPOSITORY") {
        navigate(routes.repositoryPermissions(notification.targetId));
        return;
      }
      //权限申请要在文档的权限页处理，所以直接落到权限页而不是编辑页。
      //FILE 通知不带所属知识库 id，用当前选中的库兜底
      const repositoryId =
        route.repoId ?? repositories[0]?.id ?? null;
      if (repositoryId === null) {
        showToast("无法定位该文档所属的知识库");
        return;
      }
      navigate(
        routes.filePermissions(repositoryId, notification.targetId),
      );
    },
    [
      handleMarkNotificationRead,
      navigate,
      repositories,
      route.repoId,
      showToast,
    ],
  );

  const refreshCurrentDocuments = useCallback(
    () => refreshDocuments(),
    [refreshDocuments],
  );

  const handleUploadPdf = useCallback(
    async (file: File) => {
      if (route.repoId === null) {
        showToast("请先选择知识库");
        return;
      }
      if (uploadingPdf) return;

      try {
        validatePdfFile(file);
        await assertPdfSignature(file);
      } catch (error) {
        showToast(errorMessage(error));
        return;
      }

      const repositoryId = route.repoId;
      setUploadingPdf(true);
      try {
        const response = await api.uploadPdf(
          file,
          repositoryId,
          userId,
          TENANT_ID,
          createDraftIdentifier(),
        );
        const fileId = response.data?.fileId;
        if (fileId === null || fileId === undefined) {
          throw new Error("服务未返回新建文档的 id");
        }
        await refreshCurrentDocuments();
        navigate(routes.document(repositoryId, fileId));
        showToast("PDF 上传成功", true);
      } catch (error) {
        showToast(`PDF 上传失败：${errorMessage(error)}`);
      } finally {
        setUploadingPdf(false);
      }
    },
    [
      navigate,
      refreshCurrentDocuments,
      route.repoId,
      showToast,
      uploadingPdf,
      userId,
    ],
  );

  const handleUploadMarkdown = useCallback(
    async (file: File) => {
      if (route.repoId === null) {
        showToast("请先选择知识库");
        return;
      }
      if (uploadingMarkdown) return;

      try {
        validateMarkdownFile(file);
      } catch (error) {
        showToast(errorMessage(error));
        return;
      }

      const repositoryId = route.repoId;
      setUploadingMarkdown(true);
      try {
        const response = await api.uploadMarkdown(
          file,
          repositoryId,
          userId,
          TENANT_ID,
          createDraftIdentifier(),
        );
        const fileId = response.data?.fileId;
        if (fileId === null || fileId === undefined) {
          throw new Error("服务未返回新建文档的 id");
        }
        await refreshCurrentDocuments();
        navigate(routes.document(repositoryId, fileId));
        showToast("Markdown 上传成功", true);
      } catch (error) {
        showToast(`Markdown 上传失败：${errorMessage(error)}`);
      } finally {
        setUploadingMarkdown(false);
      }
    },
    [
      navigate,
      refreshCurrentDocuments,
      route.repoId,
      showToast,
      uploadingMarkdown,
      userId,
    ],
  );

  const editorKey = `${route.mode}:${route.repoId ?? "none"}:${
    route.fileId ?? "none"
  }:${
    route.mode === "new" ? route.defaultTitle : "existing"
  }:${userId}`;

  const measureDocumentPanelMax = useCallback(() => {
    const layoutWidth = layoutRef.current?.clientWidth ?? 0;
    const sidebarWidth = sidebarRef.current?.clientWidth ?? 0;
    return Math.max(
      MIN_DOCUMENT_PANEL_WIDTH,
      layoutWidth - sidebarWidth - MIN_EDITOR_WIDTH - RESIZE_HANDLE_WIDTH,
    );
  }, []);

  useEffect(() => {
    const layout = layoutRef.current;
    if (!layout) return undefined;
    const update = () => {
      const maximum = measureDocumentPanelMax();
      setMaxDocumentPanelWidth(maximum);
      setDocumentPanelWidth((current) =>
        Math.min(Math.max(current, MIN_DOCUMENT_PANEL_WIDTH), maximum),
      );
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(layout);
    if (sidebarRef.current) observer.observe(sidebarRef.current);
    return () => observer.disconnect();
  }, [measureDocumentPanelMax]);

  const resizeDocumentPanel = (width: number) => {
    setDocumentPanelWidth(
      Math.min(
        Math.max(width, MIN_DOCUMENT_PANEL_WIDTH),
        measureDocumentPanelMax(),
      ),
    );
  };

  return (
    <>
      <TopBar
        userId={userId}
        users={users}
        loading={usersLoading}
        onUserChange={handleUserChange}
      />
      <div className="layout" ref={layoutRef}>
        <div className="sidebar-container" ref={sidebarRef}>
          <RepositorySidebar
            repositories={repositories}
            selectedRepositoryId={route.repoId}
            trashSelected={route.mode === "trash"}
            notificationsSelected={route.mode === "notifications"}
            searchSelected={route.mode === "search"}
            assistantSelected={route.mode === "assistant"}
            unreadNotificationCount={unreadCount}
            loading={repositoriesLoading}
            onSelect={(repositoryId) =>
              navigate(routes.repository(repositoryId))
            }
            onSelectTrash={() => navigate(routes.trash)}
            onSelectNotifications={() => navigate(routes.notifications)}
            onSelectSearch={() => navigate(routes.search)}
            onSelectAssistant={() => navigate(routes.assistant)}
          />
        </div>
        {route.mode === "assistant" ? (
          <AgentChatPage
            messages={agentMessages}
            loading={agentLoading}
            currentUserName={
              users.find((user) => user.id === userId)?.name ??
              t("用户 ID {id}", { id: userId })
            }
            onSend={(message) => void handleAgentSend(message)}
            onReset={handleAgentReset}
          />
        ) : route.mode === "search" ? (
          <SearchPage
            query={searchQuery}
            hits={searchHits}
            topK={searchTopK}
            loading={searchLoading}
            searched={searched}
            onSearch={(query, topK) => void handleSearch(query, topK)}
            onOpenDocument={(repositoryId, fileId) =>
              navigate(routes.document(repositoryId, fileId))
            }
          />
        ) : route.mode === "notifications" ? (
          <NotificationPage
            page={notificationPage}
            loading={notificationsLoading}
            markingId={markingNotificationId}
            onMarkRead={(notification) =>
              void handleMarkNotificationRead(notification)
            }
            onOpenTarget={handleOpenNotificationTarget}
            onChangePage={setNotificationPageNo}
          />
        ) : route.mode === "trash" ? (
          <TrashBinPage
            documents={trashDocuments}
            users={users}
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
                  t("文档 #{id}", { id: route.targetId })
                : currentRepository?.title ??
                  t("知识库 #{id}", { id: route.targetId })
            }
            userId={userId}
            users={users}
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
            <div
              id="document-panel"
              className="document-panel-container"
              ref={documentPanelRef}
              style={{ width: documentPanelWidth }}
            >
              <DocumentList
                repositoryTitle={currentRepository?.title ?? t("文档")}
                documents={documents}
                users={users}
                selectedFileId={
                  route.mode === "edit" ? route.fileId : null
                }
                loading={documentsLoading}
                canCreate={route.repoId !== null}
                creating={creatingDocument}
                uploadingPdf={uploadingPdf}
                uploadingMarkdown={uploadingMarkdown}
                onCreate={() => void handleCreate()}
                onUploadPdf={(file) => void handleUploadPdf(file)}
                onUploadMarkdown={(file) => void handleUploadMarkdown(file)}
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
            </div>
            <div
              className="document-resize-handle"
              role="separator"
              aria-controls="document-panel"
              aria-label={t("调整文档列表宽度")}
              aria-orientation="vertical"
              aria-valuemin={MIN_DOCUMENT_PANEL_WIDTH}
              aria-valuemax={maxDocumentPanelWidth}
              aria-valuenow={documentPanelWidth}
              tabIndex={0}
              onPointerDown={(event) => {
                if (event.button !== 0) return;
                resizeStartRef.current = {
                  x: event.clientX,
                  width:
                    documentPanelRef.current?.getBoundingClientRect().width ??
                    documentPanelWidth,
                };
                event.currentTarget.setPointerCapture(event.pointerId);
                event.preventDefault();
              }}
              onPointerMove={(event) => {
                if (!resizeStartRef.current) return;
                resizeDocumentPanel(
                  resizeStartRef.current.width +
                    event.clientX -
                    resizeStartRef.current.x,
                );
              }}
              onPointerUp={(event) => {
                resizeStartRef.current = null;
                event.currentTarget.releasePointerCapture(event.pointerId);
              }}
              onPointerCancel={() => {
                resizeStartRef.current = null;
              }}
              onKeyDown={(event) => {
                if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
                  event.preventDefault();
                  resizeDocumentPanel(
                    documentPanelWidth +
                      (event.key === "ArrowRight" ? 20 : -20),
                  );
                } else if (event.key === "Home" || event.key === "End") {
                  event.preventDefault();
                  resizeDocumentPanel(
                    event.key === "Home"
                      ? MIN_DOCUMENT_PANEL_WIDTH
                      : maxDocumentPanelWidth,
                  );
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
                users={users}
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
                    {t("选择左侧文档开始编辑，或点击「添加文档」新建")}
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
