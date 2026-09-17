export interface UserSummary {
  id: number;
  name: string;
  email: string;
  tenantId: number;
  groupId: number;
  profile?: string | null;
  createTime?: string | number | null;
}

export type PermissionTargetType = "FILE" | "KNOWLEDGE_REPOSITORY";
export type PermissionLevel = "READABLE" | "WRITABLE" | "MANAGEABLE";
export type PermissionStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "REVOKED";

export interface BaseResponse {
  code: number;
  time: number;
  success: boolean;
  errorMessage: string | null;
}

export interface DataResponse<T> extends BaseResponse {
  data: T;
}

export interface FileWriteResult {
  fileId: number | null;
  versionNo: number;
  defaultTitle: string;
  title: string;
}

export interface KnowledgeRepository {
  id: number;
  title: string;
  description?: string | null;
  ownerId: number;
  writableList?: string | null;
  readableList?: string | null;
  manageableList?: string | null;
  createTime?: string | number | null;
  tenantId: number;
  personal: boolean;
}

export type ContentFormat = "PLAIN" | "MARKDOWN";

export interface FileDocument {
  id: number;
  repositoryId: number;
  ownerId: number;
  title: string;
  content: string;
  contentFormat?: ContentFormat | null;
  writerList?: string | null;
  readerList?: string | null;
  manageableList?: string | null;
  publishTime?: string | number | null;
  recentUpdateTime?: string | number | null;
  latestModifiedUserId: number;
  tenantId: number;
  private: boolean;
  deleted: boolean;
}

export interface FileVersion {
  id: number;
  fileId: number | null;
  versionNo: number;
  title: string;
  content?: string | null;
  editorId: number;
  openTime?: string | number | null;
  lastMergeTime?: string | number | null;
  defaultTitle?: string | null;
  tenantId?: number;
  repositoryId?: number;
}

export interface DraftContent {
  title: string;
  content: string;
}

export interface StoredDraft extends DraftContent {
  defaultTitle: string;
}

export interface AddFileRequest {
  repositoryId: number;
  ownerId: number;
  title: string;
  content: string;
  writableList: string;
  readableList: string;
  manageableList: string;
  recentUpdateTime: null;
  latestModifiedUserId: number;
  tenantId: number;
  isPrivate: boolean;
  defaultTitle: string;
  autoTitle: boolean;
}

export interface UpdateFileRequest {
  id: number;
  title: string;
  content: string;
  writableList: string;
  readableList: string;
  manageableList: string;
  latestModifiedUserId: number;
  isPrivate: boolean;
  defaultTitle: string;
  autoTitle: boolean;
}

export interface AddFileVersionRequest {
  id: null;
  fileId: number | null;
  versionNo: number;
  title: string;
  content: string;
  editorId: number;
  openTime: null;
  lastMergeTime: null;
  repositoryId: number;
  tenantId: number;
  defaultTitle: string;
}

export type NotificationTopic =
  | "APPLY_PERMISSION"
  | "LIKE"
  | "COMMENT";

export interface NotificationItem {
  id: number;
  topic: NotificationTopic | string;
  applicantId: number;
  applicantName?: string | null;
  applicantProfile?: string | null;
  receiverId: number;
  targetType: PermissionTargetType;
  targetId: number;
  targetTitle?: string | null;
  operationContent?: string | null;
  operationTime?: string | number | null;
  read: boolean;
}

export interface NotificationPage {
  notifications: NotificationItem[];
  total: number;
  unreadCount: number;
  page: number;
  pageSize: number;
}

export interface NotificationReadRequest {
  id: number;
  receiverId: number;
  read: boolean;
}

//检索命中的片段。后端ChunkVO的isVisible字段经Lombok/Jackson后线上名是visible。
//无权限的命中仍会返回，但fileName和chunkContent为null
export interface ChunkHit {
  id: number;
  fileId: number;
  fileName: string | null;
  chunkId: string;
  chunkContent: string | null;
  ownerId: number;
  repositoryId: number;
  chunkIndex: number;
  visible: boolean;
}

export interface UserPermission {
  permissionId: number;
  userId: number;
  targetType: PermissionTargetType;
  targetId: number;
  profile?: string | null;
  name: string;
  email?: string | null;
  permissionType: PermissionLevel | "NONE";
  status: PermissionStatus;
  expirationTime?: string | number | null;
}

export interface InvitationRequest {
  requestUserId: number;
  targetUserId: number;
  targetType: PermissionTargetType;
  targetId: number;
  permissionType: PermissionLevel;
  needConfirmation: boolean;
  expirationDate: number;
}

export interface PermissionRequest {
  requestUserId: number;
  targetUserId: number;
  targetType: PermissionTargetType;
  targetId: number;
  permissionType: PermissionLevel;
  expirationDate: number;
}

export interface PermissionOperationRequest {
  requestUserId: number;
  targetUserId: number;
  targetType: PermissionTargetType;
  targetId: number;
}

export type AppRoute =
  | { mode: "home"; repoId: null; fileId: null }
  | { mode: "trash"; repoId: null; fileId: null }
  | { mode: "notifications"; repoId: null; fileId: null }
  | { mode: "search"; repoId: null; fileId: null }
  | { mode: "assistant"; repoId: null; fileId: null }
  | { mode: "repository"; repoId: number; fileId: null }
  | {
      mode: "permissions";
      repoId: number;
      fileId: number | null;
      targetType: PermissionTargetType;
      targetId: number;
    }
  | {
      mode: "new";
      repoId: number;
      fileId: null;
      defaultTitle: string;
      versionNo: number;
    }
  | { mode: "edit"; repoId: number; fileId: number };

export interface ToastState {
  message: string;
  success: boolean;
}
