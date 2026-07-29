import type {
  AddFileRequest,
  AddFileVersionRequest,
  BaseResponse,
  DataResponse,
  FileDocument,
  FileVersion,
  FileWriteResult,
  InvitationRequest,
  KnowledgeRepository,
  PermissionOperationRequest,
  PermissionRequest,
  PermissionTargetType,
  UpdateFileRequest,
  UserPermission,
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: number,
    readonly httpStatus: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T extends BaseResponse>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);

  let payload: T;
  try {
    payload = (await response.json()) as T;
  } catch {
    throw new Error(`服务返回了无法识别的内容（HTTP ${response.status}）`);
  }

  if (!response.ok || !payload.success) {
    throw new ApiError(
      payload.errorMessage ||
        `请求失败（HTTP ${response.status}，业务码 ${payload.code}）`,
      payload.code,
      response.status,
    );
  }

  return payload;
}

function post<T extends BaseResponse, TBody>(
  path: string,
  body: TBody,
  signal?: AbortSignal,
): Promise<T> {
  return request<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal,
  });
}

function put<T extends BaseResponse, TBody>(
  path: string,
  body: TBody,
): Promise<T> {
  return request<T>(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export const api = {
  listRepositories(
    tenantId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<KnowledgeRepository[]>> {
    return request(
      `/api/repository/list?tenantId=${tenantId}`,
      signal ? { signal } : undefined,
    );
  },

  listFiles(
    tenantId: number,
    repositoryId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileDocument[]>> {
    return request(
      `/api/file/list?tenantId=${tenantId}&repositoryId=${repositoryId}`,
      signal ? { signal } : undefined,
    );
  },

  listTrashBin(
    tenantId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileDocument[]>> {
    return request(
      `/api/file/trash_bin?tenantId=${tenantId}`,
      signal ? { signal } : undefined,
    );
  },

  queryFile(
    fileId: number,
    userId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileDocument | null>> {
    return request(
      `/api/file/query?id=${fileId}&userId=${userId}`,
      signal ? { signal } : undefined,
    );
  },

  listVersions(
    fileId: number,
    userId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileVersion[]>> {
    return request(
      `/api/file/versions?fileId=${fileId}&userId=${userId}`,
      signal ? { signal } : undefined,
    );
  },

  queryVersionDetail(
    fileId: number,
    versionNo: number,
    userId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileVersion>> {
    return request(
      `/api/file/version_detail?fileId=${fileId}&versionNo=${versionNo}&userId=${userId}`,
      signal ? { signal } : undefined,
    );
  },

  listDraftVersions(
    tenantId: number,
    repositoryId: number,
    defaultTitle: string,
    userId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<FileVersion[]>> {
    return request(
      `/api/file/versions_by_default_title?defaultTitle=${encodeURIComponent(defaultTitle)}&repositoryId=${repositoryId}&tenantId=${tenantId}&userId=${userId}`,
      signal ? { signal } : undefined,
    );
  },

  addFile(
    body: AddFileRequest,
  ): Promise<DataResponse<FileWriteResult>> {
    return post("/api/file/addfile", body);
  },

  updateFile(
    body: UpdateFileRequest,
  ): Promise<DataResponse<FileWriteResult>> {
    return post("/api/file/updatefile", body);
  },

  moveToTrashBin(
    fileId: number,
    userId: number,
  ): Promise<DataResponse<null>> {
    return request(
      `/api/file/move_to_trash_bin?id=${fileId}&latestModifiedUserId=${userId}`,
      { method: "POST" },
    );
  },

  restoreFromTrashBin(
    fileId: number,
    userId: number,
  ): Promise<DataResponse<null>> {
    return request(
      `/api/file/restore_from_trash_bin?id=${fileId}&latestModifiedUserId=${userId}`,
      { method: "POST" },
    );
  },

  deleteFile(
    fileId: number,
    userId: number,
  ): Promise<DataResponse<null>> {
    return request(
      `/api/file/delete_file?id=${fileId}&latestModifiedUserId=${userId}`,
      { method: "DELETE" },
    );
  },

  initializeFileVersion(
    body: AddFileVersionRequest,
  ): Promise<DataResponse<FileWriteResult>> {
    return post("/api/file/initialize_file_version", body);
  },

  addFileVersion(
    body: AddFileVersionRequest,
  ): Promise<DataResponse<FileWriteResult>> {
    return post("/api/file/add_file_version", body);
  },

  listTargetPermissions(
    requestUserId: number,
    targetType: PermissionTargetType,
    targetId: number,
    signal?: AbortSignal,
  ): Promise<DataResponse<UserPermission[]>> {
    const query = new URLSearchParams({
      requestUserId: String(requestUserId),
      targetType,
      targetId: String(targetId),
    });
    return request(
      `/api/permission_management/get_repository_permission_list?${query}`,
      signal ? { signal } : undefined,
    );
  },

  inviteUser(
    body: InvitationRequest,
  ): Promise<DataResponse<null>> {
    return post("/api/permission_management/invite_user", body);
  },

  requestPermission(
    body: PermissionRequest,
  ): Promise<DataResponse<null>> {
    return post("/api/permission_management/request_permission", body);
  },

  approvePermission(
    body: PermissionOperationRequest,
  ): Promise<DataResponse<null>> {
    return put("/api/permission_management/approve_permission", body);
  },

  rejectPermission(
    body: PermissionOperationRequest,
  ): Promise<DataResponse<null>> {
    return put("/api/permission_management/reject_permission", body);
  },

  revokePermission(
    body: PermissionOperationRequest,
  ): Promise<DataResponse<null>> {
    return put("/api/permission_management/revoke_permission", body);
  },
};
