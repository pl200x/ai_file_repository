export interface DemoUser {
  id: number;
  name: string;
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

export interface FileDocument {
  id: number;
  repositoryId: number;
  ownerId: number;
  title: string;
  content: string;
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
