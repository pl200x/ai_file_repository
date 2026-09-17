import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type Language = "zh-CN" | "en";

const STORAGE_KEY = "ai-knowledge-repository-language";

// Chinese source phrases are stable message IDs. Dynamic phrases use named
// placeholders so messages and their English equivalents live together here.
const english: Record<string, string> = {
  "AI 知识库": "AI Knowledge Base",
  "语": "AI",
  "{name}（ID {id}）": "{name} (ID {id})",
  "语言": "Language",
  "简体中文": "Chinese (Simplified)",
  "当前用户": "Current user",
  "正在加载用户…": "Loading users…",
  "用户 ID {id}": "User ID {id}",
  "知识库导航": "Repository navigation",
  "知识库": "Repositories",
  "知识库 #{id}": "Repository #{id}",
  "个人": "Personal",
  "暂无知识库": "No repositories yet",
  "正在加载知识库…": "Loading repositories…",
  "智能客服": "AI assistant",
  "语义搜索": "Semantic search",
  "消息通知": "Notifications",
  "{count} 条未读通知": "{count} unread notifications",
  "回收站": "Trash",
  "文档": "Documents",
  "文档 #{id}": "Document #{id}",
  "文档列表": "Document list",
  "管理知识库权限": "Manage repository permissions",
  "权限": "Permissions",
  "上传中…": "Uploading…",
  "上传 PDF": "Upload PDF",
  "上传 Markdown": "Upload Markdown",
  "选择要上传的 PDF": "Choose a PDF to upload",
  "选择要上传的 Markdown 文件": "Choose a Markdown file to upload",
  "创建中…": "Creating…",
  "＋ 添加": "+ Add",
  "更新于": "Updated",
  "还没有文档，点击「添加文档」新建": "No documents yet. Select Add to create one.",
  "正在加载文档…": "Loading documents…",
  "选择左侧文档开始编辑，或点击「添加文档」新建": "Select a document to edit, or choose Add to create one.",
  "调整文档列表宽度": "Resize document list",
  "用户列表加载失败：{detail}": "Could not load users: {detail}",
  "知识库加载失败：{detail}": "Could not load repositories: {detail}",
  "文档列表加载失败：{detail}": "Could not load documents: {detail}",
  "回收站加载失败：{detail}": "Could not load trash: {detail}",
  "通知加载失败：{detail}": "Could not load notifications: {detail}",
  "请先选择知识库": "Select a repository first",
  "服务未返回草稿标识": "The service did not return a draft identifier",
  "服务未返回有效的初始版本号": "The service did not return a valid initial version number",
  "草稿创建失败：{detail}": "Could not create draft: {detail}",
  "文档已移入回收站": "Document moved to trash",
  "移入回收站失败：{detail}": "Could not move to trash: {detail}",
  "文档已永久删除": "Document permanently deleted",
  "永久删除失败：{detail}": "Could not permanently delete: {detail}",
  "永久删除后无法恢复，确定继续吗？": "This cannot be undone. Permanently delete the document?",
  "文档已恢复": "Document restored",
  "恢复失败：{detail}": "Could not restore: {detail}",
  "搜索失败：{detail}": "Search failed: {detail}",
  "没有获得有效回答，请换个说法再试一次。": "No answer was returned. Try rephrasing your question.",
  "抱歉，这次没有完成检索：{detail}": "Sorry, the search could not be completed: {detail}",
  "抱歉，这次没有完成检索，请稍后重试。": "Sorry, the search could not be completed. Try again later.",
  "智能客服请求失败：{detail}": "AI assistant request failed: {detail}",
  "请稍后重试": "Please try again later",
  "更新已读状态失败：{detail}": "Could not update read status: {detail}",
  "无法定位该文档所属的知识库": "Could not find this document's repository",
  "服务未返回新建文档的 id": "The service did not return the new document ID",
  "PDF 上传成功": "PDF uploaded",
  "PDF 上传失败：{detail}": "PDF upload failed: {detail}",
  "Markdown 上传成功": "Markdown uploaded",
  "Markdown 上传失败：{detail}": "Markdown upload failed: {detail}",
  "发生未知错误": "An unknown error occurred",
  "服务返回了无法识别的内容（HTTP {status}）": "The service returned an unrecognized response (HTTP {status})",
  "请求失败（HTTP {status}，业务码 {code}）": "Request failed (HTTP {status}, code {code})",
  "未命名文档": "Untitled document",
  "没有找到相关内容": "No relevant content found",
  "{count} 条相关片段": "{count} relevant passages",
  "{count} 条相关片段 · {hidden} 条无权限查看": "{count} relevant passages · {hidden} unavailable without permission",
  "第 {index} 段": "Passage {index}",
  "权限申请": "Permission request",
  "点赞": "Like",
  "评论": "Comment",
  "可阅读": "Can read",
  "可编辑": "Can edit",
  "可管理": "Can manage",
  "无权限": "No access",
  "待审批": "Pending",
  "已生效": "Active",
  "已拒绝": "Rejected",
  "已撤销": "Revoked",
  "可以查看内容": "Can view content",
  "可以查看和修改内容": "Can view and edit content",
  "可以管理内容与成员权限": "Can manage content and member access",
  "1 天": "1 day",
  "3 天": "3 days",
  "1 个月": "1 month",
  "1 年": "1 year",
  "{name} 申请 {target}的{level}权限": "{name} requested {level} access to {target}",
  "{name} 申请 {target}的权限": "{name} requested access to {target}",
  "知识库《{title}》": "repository “{title}”",
  "文档《{title}》": "document “{title}”",
  "{name} · {target} · {content}": "{name} · {target} · {content}",
  "{name} · {target}": "{name} · {target}",
  "文档格式预览": "Formatted document preview",
  "版本历史": "Version history",
  "未命名版本": "Untitled version",
  "正在加载版本…": "Loading versions…",
  "版本加载失败：{detail}": "Could not load versions: {detail}",
  "暂无版本": "No versions yet",
  "加载中…": "Loading…",
  "正在加载…": "Loading…",
  "刷新中…": "Refreshing…",
  "刷新": "Refresh",
  "返回": "Back",
  "取消": "Cancel",
  "提交中…": "Submitting…",
  "恢复中…": "Restoring…",
  "恢复": "Restore",
  "永久删除中…": "Deleting permanently…",
  "永久删除": "Delete permanently",
  "回收站是空的": "Trash is empty",
  "这里展示已被软删除的文档。永久删除后将无法恢复。": "Deleted documents appear here. Permanent deletion cannot be undone.",
  "{count} 个文档": "{count} documents",
  "正在加载回收站…": "Loading trash…",
  "移入于": "Moved to trash",
  "别人对你管理的文档和知识库发起的操作会出现在这里。": "Activity on documents and repositories you manage appears here.",
  "{unread} 条未读 / 共 {total} 条": "{unread} unread / {total} total",
  "正在加载通知…": "Loading notifications…",
  "暂时没有通知": "No notifications yet",
  "处理中…": "Processing…",
  "标为未读": "Mark unread",
  "标为已读": "Mark read",
  "上一页": "Previous",
  "下一页": "Next",
  "第 {page} / {total} 页": "Page {page} of {total}",
  "如何申请一个文件的读取权限？": "How do I request read access to a file?",
  "文档删除后还能恢复吗？": "Can a deleted document be restored?",
  "帮我总结知识库里的权限规则": "Summarize the repository's permission rules",
  "把知识库里关于访问权限的说明扩写得更详细一些": "Expand the repository's access guidance in more detail",
  "基于你有权限访问的知识库内容回答问题": "Answers based on repository content you can access",
  "{name} 的权限视角": "Viewing as {name}",
  "新对话": "New conversation",
  "客服对话": "Assistant conversation",
  "你好，我是知识库智能客服": "Hello, I'm your knowledge base assistant",
  "我会检索与你问题最相关的文档片段，并且只使用你有权限查看的内容作答。": "I'll find relevant passages and answer using only content you can access.",
  "快捷问题": "Suggested questions",
  "正在思考": "Thinking",
  "输入你想了解的文档、流程或权限问题…": "Ask about a document, process, or permission…",
  "发送给智能客服": "Message the AI assistant",
  "发送消息": "Send message",
  "Enter 发送 · Shift + Enter 换行": "Enter to send · Shift + Enter for a new line",
  "按意思检索文档内容，而不是按关键词匹配。": "Search document meaning, not just keywords.",
  "描述你想找的内容，例如：权限过期之后会怎么样": "Describe what you need, e.g. what happens when access expires",
  "搜索内容": "Search content",
  "返回条数": "Number of results",
  "{count} 条": "{count} results",
  "搜索中…": "Searching…",
  "搜索": "Search",
  "正在检索…": "Searching documents…",
  "输入一句话，找到相关的文档片段": "Enter a question to find relevant passages",
  "没有找到相关内容，换个说法试试": "No relevant content found. Try a different description.",
  "{count} 个片段": "{count} passages",
  "收起": "Collapse",
  "展开全文": "Show full passage",
  "你没有这篇文档的访问权限，内容不会显示": "You don't have access to this document, so its content is hidden",
  "图片上传或转换失败，请重试": "Image upload or conversion failed. Try again.",
  "文档正文编辑器": "Document editor",
  "图片处理中…": "Processing image…",
  "插入图片": "Insert image",
  "PNG、JPG、GIF、WebP，单张不超过": "PNG, JPG, GIF, WebP; maximum size",
  "选择要插入的图片": "Choose an image to insert",
  "文档图片": "Document image",
  "删除这张图片": "Remove this image",
  "删除图片": "Remove image",
  "删除": "Remove",
  "在图片下方继续输入…": "Continue typing below the image…",
  "文档正文文本区域 {index}": "Document text area {index}",
  "确定将文档移入回收站吗？": "Move this document to trash?",
  "新建文档": "New document",
  "编辑文档": "Edit document",
  "存在未保存更改": "Unsaved changes",
  "内容已保存": "Changes saved",
  "权限管理": "Manage permissions",
  "正在移入…": "Moving…",
  "移入回收站": "Move to trash",
  "下载当前已保存版本的 PDF": "Download the current saved version as PDF",
  "下载 PDF": "Download PDF",
  "按 Markdown 语法渲染正文格式": "Render content as Markdown",
  "编辑": "Edit",
  "预览格式": "Preview formatting",
  "提 交": "Submit",
  "请输入标题": "Enter a title",
  "文档标题": "Document title",
  "正在加载文档内容…": "Loading document content…",
  "开始书写正文…": "Start writing…",
  "邀请已提交，等待审批": "Invitation submitted for approval",
  "权限已授予": "Permission granted",
  "邀请失败：{detail}": "Invitation failed: {detail}",
  "权限申请已提交": "Permission request submitted",
  "申请失败：{detail}": "Request failed: {detail}",
  "确定撤销 {name} 的权限吗？": "Revoke {name}'s permission?",
  "权限已批准": "Permission approved",
  "权限已拒绝": "Permission rejected",
  "权限已撤销": "Permission revoked",
  "批准": "Approve",
  "拒绝": "Reject",
  "撤销": "Revoke",
  "{action}失败：{detail}": "{action} failed: {detail}",
  "文档权限": "Document permissions",
  "知识库权限": "Repository permissions",
  "管理成员访问权限，或为当前用户提交权限申请。": "Manage member access or request access for the current user.",
  "成员权限": "Member permissions",
  "待审批的申请会优先展示。": "Pending requests appear first.",
  "暂时无法查看成员权限": "Member permissions are unavailable right now",
  "正在加载成员权限…": "Loading member permissions…",
  "当前目标还没有权限记录": "No permission records for this item yet",
  "你": "You",
  "到期": "Expires",
  "未设置": "Not set",
  "批准中…": "Approving…",
  "拒绝中…": "Rejecting…",
  "撤销中…": "Revoking…",
  "邀请成员": "Invite members",
  "需要当前用户具有可管理权限。": "The current user needs manage access.",
  "邀请用户": "Invite user",
  "暂无可邀请用户": "No users available to invite",
  "立即生效": "Take effect immediately",
  "关闭时将创建待审批记录": "Turn off to create a request awaiting approval",
  "发送邀请中…": "Sending invitation…",
  "发送邀请": "Send invitation",
  "申请权限": "Request permission",
  "为当前用户 user{id} 提交申请。": "Submit a request for user{id}.",
  "提交申请中…": "Submitting request…",
  "提交申请": "Submit request",
  "权限级别": "Permission level",
  "有效期": "Duration",
  "权限申请已提交，等待管理员审批": "Permission request submitted for administrator approval",
  "权限申请失败：{detail}": "Permission request failed: {detail}",
  "你还没有访问权限": "You don't have access yet",
  "选择所需权限并提交申请。管理员批准后，即可打开这份文档。": "Choose the access you need and submit a request. You can open this document once an administrator approves it.",
  "申请已提交。你可以等待审批，或修改选项后更新申请。": "Request submitted. Wait for approval, or change the options and update it.",
  "更新申请": "Update request",
  "提交权限申请": "Submit permission request",
  "正在检查权限…": "Checking access…",
  "重新检查权限": "Check access again",
  "已恢复本地草稿，内容变化后每 30 秒自动保存版本": "Local draft restored. Changes are saved every 30 seconds.",
  "草稿已创建，内容变化后每 30 秒自动保存版本": "Draft created. Changes are saved every 30 seconds.",
  "草稿标识缺失": "Draft identifier missing",
  "自动保存中…": "Autosaving…",
  "上次自动保存 {time}": "Last autosaved at {time}",
  "自动保存冲突：服务器已有更新，请刷新文档后再继续编辑": "Autosave conflict: the server has a newer version. Refresh before editing further.",
  "自动保存失败，将重试：{detail}": "Autosave failed; retrying: {detail}",
  "已从自动保存版本 v{version} 恢复": "Restored from autosaved version v{version}",
  "草稿恢复失败：{detail}": "Draft recovery failed: {detail}",
  "已恢复你未提交的修改（自动保存 v{version}），内容变化后每 30 秒自动保存": "Restored your unsubmitted changes (autosave v{version}). Changes are saved every 30 seconds.",
  "已加载最新正式内容，内容变化后每 30 秒自动保存": "Latest submitted content loaded. Changes are saved every 30 seconds.",
  "等待访问权限": "Waiting for access permission",
  "文档加载失败：{detail}": "Could not load document: {detail}",
  "已提交 {time}": "Submitted at {time}",
  "文档创建成功": "Document created",
  "提交成功": "Submitted successfully",
  "创建": "Create",
  "提交": "Submit",
  "服务返回了无效的版本号": "The service returned an invalid version number",
  "版本链中没有可用版本": "No usable version in the version history",
  "版本链缺少 canonical defaultTitle": "Version history is missing canonical defaultTitle",
  "版本链 defaultTitle 与当前文档不一致": "Version history defaultTitle does not match the current document",
  "未找到已初始化的草稿版本": "Initialized draft version not found",
  "草稿版本链缺少 canonical defaultTitle": "Draft history is missing canonical defaultTitle",
  "草稿版本链 defaultTitle 与当前路由不一致": "Draft history defaultTitle does not match the current route",
  "目标文档不存在": "Document not found",
  "目标文档没有可恢复的版本": "No restorable version for this document",
  "目标文档版本链缺少 canonical defaultTitle": "Document history is missing canonical defaultTitle",
  "最新文档版本不存在": "Latest document version not found",
  "最新文档版本缺少 canonical defaultTitle": "Latest document version is missing canonical defaultTitle",
  "版本列表与最新版本的 defaultTitle 不一致": "Version list defaultTitle does not match the latest version",
  "缺少草稿标识，请重新点击添加文档": "Draft identifier missing. Select Add again.",
  "服务未返回新文档 ID": "The service did not return a new document ID",
  "缺少目标文档 ID": "Target document ID missing",
  "服务未返回目标文档 ID": "The service did not return a target document ID",
  "图片数据格式无效，无法插入文档": "Invalid image data; could not insert it into the document",
  "不支持该图片格式，请选择 PNG、JPEG、JPG、GIF 或 WebP 图片": "Unsupported image format. Choose a PNG, JPEG, JPG, GIF, or WebP image.",
  "图片文件为空或大小无效": "Image is empty or has an invalid size",
  "图片过大，单张图片不能超过 5 MiB": "Image is too large; maximum size is 5 MiB",
  "读取图片失败，请重新选择图片后再试": "Could not read the image. Choose it again and retry.",
  "读取图片失败：图片内容为空": "Could not read the image: its content is empty",
  "图片内容与文件格式不匹配，请选择有效的 PNG、JPEG、JPG、GIF 或 WebP 图片": "Image content does not match its format. Choose a valid PNG, JPEG, JPG, GIF, or WebP image.",
  "图片转换为 Base64 失败，请重新选择图片后再试": "Could not convert the image. Choose it again and retry.",
  "只支持上传 PDF 文件": "Only PDF files can be uploaded",
  "PDF 文件为空或大小无效": "PDF is empty or has an invalid size",
  "PDF 过大，单个文件不能超过 {size} MB": "PDF is too large; maximum size is {size} MB",
  "读取 PDF 失败，请重新选择文件后再试": "Could not read the PDF. Choose it again and retry.",
  "文件内容不是有效的 PDF，请检查后重新选择": "This is not a valid PDF. Check the file and choose it again.",
  "只支持上传 .md / .markdown 文件": "Only .md or .markdown files can be uploaded",
  "Markdown 文件为空或大小无效": "Markdown file is empty or has an invalid size",
  "Markdown 文件过大，单个文件不能超过 {size} MB": "Markdown file is too large; maximum size is {size} MB",
};

const requestLevelEnglish: Record<string, string> = {
  "可阅读": "read",
  "可编辑": "edit",
  "可管理": "manage",
};

const patterns = Object.entries(english)
  .filter(([key]) => key.includes("{"))
  .sort(([a], [b]) => b.length - a.length)
  .map(([key, value]) => {
    const names: string[] = [];
    const expression = key
      .split(/(\{\w+\})/g)
      .map((part) => {
        if (part.startsWith("{") && part.endsWith("}")) {
          names.push(part.slice(1, -1));
          return "(.+?)";
        }
        return part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      })
      .join("");
    return { regex: new RegExp(`^${expression}$`), names, value };
  });

export function translate(
  source: string,
  language: Language,
  parameters: Record<string, string | number> = {},
): string {
  let template = language === "en" ? english[source] : source;
  let values: Record<string, string | number> = parameters;
  if (language === "en" && template === undefined) {
    const match = patterns
      .map((entry) => ({ entry, result: source.match(entry.regex) }))
      .find(({ result }) => result !== null);
    if (match?.result) {
      template = match.entry.value;
      values = Object.fromEntries(
        match.entry.names.map((name, index) => [name, match.result![index + 1] ?? ""]),
      );
    }
  }
  return (template ?? source).replace(/\{(\w+)\}/g, (_, name: string) => {
    const value = String(values[name] ?? `{${name}}`);
    if (language === "en" && name === "level") {
      return requestLevelEnglish[value] ?? value;
    }
    const nestedMessage = ["detail", "target", "level", "action"].includes(name)
      || (name === "name" && /^用户 ID \d+$/.test(value));
    return language === "en" && nestedMessage && value !== source
      ? translate(value, language)
      : value;
  });
}

function initialLanguage(): Language {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === "en" ? "en" : "zh-CN";
  } catch {
    return "zh-CN";
  }
}

interface I18nValue {
  language: Language;
  setLanguage: (language: Language) => void;
  t: (source: string, parameters?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<Language>(initialLanguage);
  useEffect(() => {
    document.documentElement.lang = language;
    document.title = translate("AI 知识库", language);
    document.querySelector('meta[name="description"]')?.setAttribute(
      "content",
      language === "en"
        ? "Collaborative AI knowledge base and document editor"
        : "面向团队协作的 AI 知识库文档管理与版本编辑界面",
    );
    try {
      window.localStorage.setItem(STORAGE_KEY, language);
    } catch {
      // Private browsing may disable storage; the in-memory preference still works.
    }
  }, [language]);
  const value = useMemo<I18nValue>(
    () => ({
      language,
      setLanguage,
      t: (source, parameters) => translate(source, language, parameters),
    }),
    [language],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useTranslation(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error("I18nProvider is missing");
  return context;
}
