const adminState = {
    activePage: document.body.dataset.page,
    usersPage: 0,
    ownerApplicationsStatus: "PENDING",
    postsPage: 0,
    commentsPage: 0,
    reportsPage: 0,
    logsPage: 0,
    modal: null
};

const REASON_LABELS = {
    SPAM: "스팸/광고",
    ABUSE: "욕설/비방",
    OFF_TOPIC: "주제와 무관",
    POLICY: "운영 정책 위반",
    DUPLICATE: "중복 게시물",
    MANUAL_RESTORE: "관리자 복구",
    ROLE_POLICY: "권한 정책",
    POINT_ADJUSTMENT: "포인트 조정",
    CATEGORY_POLICY: "카테고리 정책",
    SUSPENSION_POLICY: "정지 정책",
    REPORT_POLICY: "신고 처리 정책",
    PURGE_POLICY: "영구 삭제 정책",
    OTHER: "기타"
};

const TARGET_TYPE_LABELS = {
    USER: "사용자",
    POST: "게시글",
    COMMENT: "댓글",
    CATEGORY: "카테고리",
    REPORT: "신고"
};

const ACTION_TYPE_LABELS = {
    POST_HIDE: "게시글 숨김",
    POST_RESTORE: "게시글 복구",
    POST_PURGE: "게시글 영구 삭제",
    COMMENT_HIDE: "댓글 숨김",
    COMMENT_RESTORE: "댓글 복구",
    COMMENT_PURGE: "댓글 영구 삭제",
    USER_ROLE_CHANGE: "사용자 권한 변경",
    USER_POINT_ADJUST: "사용자 포인트 조정",
    USER_SUSPEND: "사용자 정지",
    USER_ACTIVATE: "사용자 활성화",
    CATEGORY_CREATE: "카테고리 생성",
    CATEGORY_UPDATE: "카테고리 수정",
    CATEGORY_REORDER: "카테고리 순서 변경",
    REPORT_REVIEWING: "신고 검토 중",
    REPORT_RESOLVE: "신고 해결",
    REPORT_REJECT: "신고 반려"
};

const ACCOUNT_STATUS_LABELS = {
    ACTIVE: "활성",
    SUSPENDED: "정지"
};

const REPORT_STATUS_LABELS = {
    OPEN: "접수",
    REVIEWING: "검토 중",
    RESOLVED: "해결",
    REJECTED: "반려"
};

const REPORT_REASON_LABELS = {
    SPAM: "성적 콘텐츠",
    ABUSE: "폭력·혐오 콘텐츠",
    HATE: "증오·악의적 콘텐츠",
    HARASSMENT: "괴롭힘·폭력",
    HARMFUL: "유해·위험 행위",
    MISINFORMATION: "자살·자해",
    WRONG_INFO: "잘못된 정보",
    CHILD_ABUSE: "아동 학대",
    TERRORISM: "테러 조장",
    MISLEADING: "스팸·혼동 콘텐츠",
    OTHER: "기타"
};

const REASON_OPTIONS = [];
const TARGET_TYPE_OPTIONS = [];
const ACTION_TYPE_OPTIONS = [];
const ACCOUNT_STATUS_OPTIONS = [];
const REPORT_STATUS_OPTIONS = [];
const REPORT_REASON_OPTIONS = [];

syncLabelOptions(REASON_OPTIONS, REASON_LABELS);
syncLabelOptions(TARGET_TYPE_OPTIONS, TARGET_TYPE_LABELS);
syncLabelOptions(ACTION_TYPE_OPTIONS, ACTION_TYPE_LABELS);
syncLabelOptions(ACCOUNT_STATUS_OPTIONS, ACCOUNT_STATUS_LABELS);
syncLabelOptions(REPORT_STATUS_OPTIONS, REPORT_STATUS_LABELS);
syncLabelOptions(REPORT_REASON_OPTIONS, REPORT_REASON_LABELS);

const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

document.addEventListener("DOMContentLoaded", () => {
    populateStaticOptions();
    bindStaticEvents();
    initializePage().catch((error) => showAlert(error.message, "error"));
});

class ApiError extends Error {
    constructor(message, { status = 0, errors = {} } = {}) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.errors = errors;
    }
}

function syncLabelOptions(options, labels) {
    options.splice(0, options.length, ...Object.entries(labels).map(([value, label]) => ({ value, label })));
}

function toReasonOptions(keys) {
    return keys.map((key) => ({ value: key, label: REASON_LABELS[key] || key }));
}

function populateStaticOptions() {
    fillSelect("modalReasonCode", REASON_OPTIONS, { firstLabel: "사유 코드를 선택해 주세요" });
    fillSelect("logsReasonCode", REASON_OPTIONS, { firstLabel: "전체" });
    fillSelect("logsTargetType", TARGET_TYPE_OPTIONS, { firstLabel: "전체" });
    fillSelect("logsActionType", ACTION_TYPE_OPTIONS, { firstLabel: "전체" });
    fillSelect("reportsTargetType", TARGET_TYPE_OPTIONS.filter((item) => item.value === "POST" || item.value === "COMMENT"), { firstLabel: "전체" });
    fillSelect("reportsStatus", REPORT_STATUS_OPTIONS, { firstLabel: "전체" });
    fillSelect("reportsReasonCode", REPORT_REASON_OPTIONS, { firstLabel: "전체" });
}

function fillSelect(id, options, { firstLabel = "전체" } = {}) {
    const select = document.getElementById(id);
    if (!select) {
        return;
    }
    select.innerHTML = `<option value="">${escapeHtml(firstLabel)}</option>` + options.map((item) =>
        `<option value="${item.value}">${escapeHtml(item.label)}</option>`
    ).join("");
}

function bindStaticEvents() {
    bindFilterForm("usersFilterForm", () => {
        adminState.usersPage = 0;
        return loadUsers();
    });
    bindFilterForm("postsFilterForm", () => {
        adminState.postsPage = 0;
        return loadPosts();
    });
    bindFilterForm("commentsFilterForm", () => {
        adminState.commentsPage = 0;
        return loadComments();
    });
    bindFilterForm("reportsFilterForm", () => {
        adminState.reportsPage = 0;
        return loadReports();
    });
    bindFilterForm("logsFilterForm", () => {
        adminState.logsPage = 0;
        return loadLogs();
    });

    document.getElementById("refreshDashboardButton")?.addEventListener("click", () => {
        loadDashboard().catch((error) => showAlert(error.message, "error"));
    });
    document.getElementById("refreshOwnerApplicationsButton")?.addEventListener("click", () => {
        loadOwnerApplications().catch((error) => showAlert(error.message, "error"));
    });
    document.querySelectorAll("[data-owner-status]").forEach((button) => {
        button.addEventListener("click", () => {
            adminState.ownerApplicationsStatus = button.dataset.ownerStatus || "PENDING";
            document.querySelectorAll("[data-owner-status]").forEach((tab) => {
                tab.classList.toggle("active", tab === button);
            });
            loadOwnerApplications().catch((error) => showAlert(error.message, "error"));
        });
    });

    document.getElementById("modalCloseButton")?.addEventListener("click", closeActionModal);
    document.getElementById("modalCancelButton")?.addEventListener("click", closeActionModal);
    document.querySelectorAll("[data-modal-close]").forEach((element) => {
        element.addEventListener("click", closeActionModal);
    });
    document.getElementById("actionModalForm")?.addEventListener("submit", handleModalSubmit);
}

function bindFilterForm(formId, handler) {
    const form = document.getElementById(formId);
    if (!form) {
        return;
    }
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        handler().catch((error) => showAlert(error.message, "error"));
    });
}

async function initializePage() {
    switch (adminState.activePage) {
        case "dashboard":
            await loadDashboard();
            break;
        case "users":
            await loadUsers();
            break;
        case "ownerApplications":
            await loadOwnerApplications();
            break;
        case "posts":
            await populateCategoryFilter();
            await loadPosts();
            break;
        case "comments":
            await loadComments();
            break;
        case "reports": {
            const statusSel = document.getElementById("reportsStatus");
            if (statusSel) statusSel.value = "OPEN";
            await loadReports();
            break;
        }
        case "logs":
            await loadLogs();
            break;
        default:
            break;
    }
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

async function loadDashboard() {
    const data = await apiRequest("/admin/api/dashboard");
    setText("summaryUsers", numberFormat(data.summary.totalUsers));
    setText("summaryAdmins", numberFormat(data.summary.adminUsers));
    setText("summaryPosts", numberFormat(data.summary.activePosts));
    setText("summaryComments", numberFormat(data.summary.activeComments));
    setText("summarySuspended", numberFormat(data.summary.suspendedUsers));
    setText("summaryReports", numberFormat(data.summary.openReports));
    setText("summaryHide7d", numberFormat(data.summary.recent7dHideActions));
    setText("summaryRestore7d", numberFormat(data.summary.recent7dRestoreActions));

    renderActivityList("dashboardUsers", data.recentUsers, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(item.name)}</div>
            <div class="activity-sub">${escapeHtml(item.email)} / ${escapeHtml(item.role)} / ${escapeHtml(labelForAccountStatus(item.accountStatus || "ACTIVE"))}</div>
        </div>
    `);
    renderActivityList("dashboardPosts", data.recentPosts, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(item.title)}</div>
            <div class="activity-sub">${escapeHtml(item.authorName)} / ${escapeHtml(item.categoryName || "미분류")} / ${formatDate(item.createdAt)}</div>
        </div>
    `);
    renderActivityList("dashboardComments", data.recentComments, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(item.contentPreview || "내용 없음")}</div>
            <div class="activity-sub">${escapeHtml(item.authorName)} / ${escapeHtml(item.postTitle || "게시글 #" + item.postId)} / ${formatDate(item.createdAt)}</div>
        </div>
    `);
    renderActivityList("dashboardReports", data.recentReports, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(item.targetSummary)}</div>
            <div class="activity-sub">${escapeHtml(item.reporterUserName)} / ${escapeHtml(labelForReportReason(item.reasonCode))} / ${escapeHtml(labelForReportStatus(item.status))}</div>
        </div>
    `);
    renderActivityList("dashboardLogs", data.recentLogs, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(labelForAction(item.actionType))}</div>
            <div class="activity-sub">${escapeHtml(item.adminUserName)} / ${escapeHtml(item.targetSummary)} / ${escapeHtml(labelForReason(item.reasonCode))}</div>
        </div>
    `);
}

function renderActivityList(containerId, items, renderer) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }
    if (!items || !items.length) {
        container.innerHTML = `<div class="empty-state">활동 내역이 없습니다.</div>`;
        return;
    }
    container.innerHTML = items.map(renderer).join("");
}
async function refreshDashboardSilently() {
    try {
        await loadDashboard();
    } catch (error) {
        console.error(error);
    }
}

async function loadUsers() {
    const query = new URLSearchParams({ page: String(adminState.usersPage), size: "10" });
    appendIfPresent(query, "keyword", document.getElementById("usersKeyword")?.value);
    appendIfPresent(query, "role", document.getElementById("usersRole")?.value);
    appendIfPresent(query, "accountStatus", document.getElementById("usersAccountStatus")?.value);
    appendIfPresent(query, "dateFrom", document.getElementById("usersDateFrom")?.value);
    appendIfPresent(query, "dateTo",   document.getElementById("usersDateTo")?.value);

    const data = await apiRequest(`/admin/api/users?${query.toString()}`);
    const body = document.getElementById("usersTableBody");
    if (!body) {
        return;
    }

    if (!data.items.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">조회 결과가 없습니다.</div></td></tr>`;
    } else {
        body.innerHTML = data.items.map((item) => `
            <tr>
                <td>
                    <div class="stack">
                        <strong>${escapeHtml(item.name)}</strong>
                        <span class="muted">${escapeHtml(item.email)}</span>
                        <span class="muted">${escapeHtml(item.provider)} · ID ${item.id}</span>
                    </div>
                </td>
                <td><span class="badge ${classForRole(item.role)}">${escapeHtml(item.role)}</span></td>
                <td><span class="badge ${item.accountStatus === "SUSPENDED" ? "danger" : "success"}">${escapeHtml(labelForAccountStatus(item.accountStatus))}</span></td>
                <td><strong>${numberFormat(item.point)}P</strong></td>
                <td class="muted">${formatDate(item.createdAt)}</td>
                <td>
                    <div class="action-group">
                        <button type="button" class="tiny-button" data-user-role="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-role="${item.role}" data-current-point="${item.point}">권한</button>
                        <button type="button" class="tiny-button" data-user-point="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-point="${item.point}">포인트</button>
                        <button type="button" class="tiny-button ${item.accountStatus === "SUSPENDED" ? "" : "danger-button"}" data-user-status="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-status="${item.accountStatus}">${item.accountStatus === "SUSPENDED" ? "정지 해제" : "정지"}</button>
                    </div>
                </td>
            </tr>
        `).join("");
    }

    body.querySelectorAll("[data-user-role]").forEach((button) => {
        button.addEventListener("click", () => openUserRoleModal(button.dataset.userRole, button.dataset.userName, button.dataset.currentRole));
    });
    body.querySelectorAll("[data-user-point]").forEach((button) => {
        button.addEventListener("click", () => openUserPointModal(button.dataset.userPoint, button.dataset.userName, button.dataset.currentPoint));
    });
    body.querySelectorAll("[data-user-status]").forEach((button) => {
        button.addEventListener("click", () => openUserStatusModal(button.dataset.userStatus, button.dataset.userName, button.dataset.currentStatus));
    });

    renderPagination("usersPagination", data, (page) => {
        adminState.usersPage = page;
        loadUsers().catch((error) => showAlert(error.message, "error"));
    });
}

function openUserRoleModal(userId, userName, currentRole) {
    openActionModal({
        title: "권한 변경",
        summary: `${userName} 사용자의 권한을 변경합니다. (현재: ${currentRole})`,
        confirmText: "권한 변경",
        reasonOptions: toReasonOptions(["ROLE_POLICY", "POLICY", "OTHER"]),
        extraHtml: `
            <label>변경할 역할
                <select id="modalRoleValue">
                    <option value="USER" ${currentRole === "USER" ? "selected" : ""}>USER — 일반 사용자</option>
                    <option value="OWNER" ${currentRole === "OWNER" ? "selected" : ""}>OWNER — 사업자</option>
                    <option value="ADMIN" ${currentRole === "ADMIN" ? "selected" : ""}>ADMIN — 관리자</option>
                </select>
            </label>
        `,
        getExtraPayload: () => ({ role: document.getElementById("modalRoleValue")?.value }),
        submit: async (reasonPayload) => {
            const response = await apiRequest(`/admin/api/users/${userId}/role`, {
                method: "PATCH",
                body: JSON.stringify(reasonPayload)
            });
            await loadUsers();
            return `권한이 ${response.role}로 변경되었습니다.`;
        }
    });
}

async function loadOwnerApplications() {
    const status = adminState.ownerApplicationsStatus || "PENDING";
    const data = await apiRequest(`/admin/api/owner-applications?status=${encodeURIComponent(status)}`);
    const body = document.getElementById("ownerApplicationsTableBody");
    if (!body) {
        return;
    }

    if (!data.length) {
        body.innerHTML = `<tr><td colspan="5"><div class="empty-state">${emptyOwnerApplicationMessage(status)}</div></td></tr>`;
        return;
    }

    body.innerHTML = data.map((item) => `
        <tr>
            <td>
                <div class="stack">
                    <strong>${escapeHtml(item.userName)}</strong>
                    <span class="muted">${escapeHtml(item.userEmail || "이메일 없음")}</span>
                    <span class="muted">사용자 ID ${item.userId} · 신청 ID ${item.id}</span>
                    <span class="muted">${escapeHtml(item.userRole || "-")} · ${numberFormat(item.userPoint)}P</span>
                </div>
            </td>
            <td>
                <div class="stack">
                    <strong>${escapeHtml(item.storeName)}</strong>
                    <span class="muted">${escapeHtml(item.businessRegistrationNumber)}</span>
                    <span class="badge ${classForOwnerStatus(item.status)}">${escapeHtml(labelForOwnerStatus(item.status))}</span>
                </div>
            </td>
            <td>
                <div class="stack">
                    ${documentLink(item.id, item.businessRegistrationFilePath, "registration", "사업자등록증")}
                    ${documentLink(item.id, item.businessLicenseFilePath, "license", "영업신고증")}
                    ${documentLink(item.id, item.menuFilePath, "menu", "메뉴 정보")}
                </div>
            </td>
            <td>
                <div class="stack">
                    <span class="muted">접수 ${formatDate(item.createdAt)}</span>
                    ${item.reviewedAt ? `<span class="muted">처리 ${formatDate(item.reviewedAt)}</span>` : ""}
                    ${item.reviewComment ? `<span class="muted">${escapeHtml(item.reviewComment)}</span>` : ""}
                </div>
            </td>
            <td>
                ${renderOwnerApplicationActions(item)}
            </td>
        </tr>
    `).join("");

    body.querySelectorAll("[data-owner-approve]").forEach((button) => {
        button.addEventListener("click", () => openOwnerApplicationModal(button.dataset.ownerApprove, button.dataset.storeName, "approve"));
    });
    body.querySelectorAll("[data-owner-reject]").forEach((button) => {
        button.addEventListener("click", () => openOwnerApplicationModal(button.dataset.ownerReject, button.dataset.storeName, "reject"));
    });
    body.querySelectorAll("[data-owner-point]").forEach((button) => {
        button.addEventListener("click", () => openOwnerPointModal(button.dataset.ownerPoint, button.dataset.userName, button.dataset.currentPoint));
    });
}

function renderOwnerApplicationActions(item) {
    if (item.status === "PENDING") {
        return `
            <div class="action-group">
                <button type="button" class="primary-button" data-owner-approve="${item.id}" data-store-name="${escapeHtml(item.storeName)}">승인</button>
                <button type="button" class="danger-button" data-owner-reject="${item.id}" data-store-name="${escapeHtml(item.storeName)}">반려</button>
            </div>
        `;
    }
    if (item.status === "APPROVED") {
        return `
            <div class="action-group">
                <button type="button" class="tiny-button" data-owner-point="${item.userId}" data-user-name="${escapeHtml(item.userName)}" data-current-point="${item.userPoint}">포인트 지급</button>
            </div>
        `;
    }
    return `<span class="muted">반려 완료</span>`;
}

function emptyOwnerApplicationMessage(status) {
    if (status === "APPROVED") {
        return "승인 완료된 사업자 신청이 없습니다.";
    }
    if (status === "REJECTED") {
        return "반려된 사업자 신청이 없습니다.";
    }
    return "검토 대기 중인 사업자 신청이 없습니다.";
}

function documentLink(requestId, path, type, label) {
    if (!path) {
        return `<span class="muted">${escapeHtml(label)} 없음</span>`;
    }
    return `<a class="content-link" href="/admin/api/owner-applications/${requestId}/documents/${type}" target="_blank" rel="noopener">${escapeHtml(label)}</a>`;
}

function openOwnerPointModal(userId, userName, currentPoint) {
    openActionModal({
        title: "사업자 포인트 지급",
        summary: `${userName} 사업자의 포인트를 조정합니다. (현재: ${numberFormat(currentPoint)}P)`,
        confirmText: "포인트 조정",
        reasonOptions: toReasonOptions(["POINT_ADJUSTMENT", "ROLE_POLICY", "POLICY", "OTHER"]),
        extraHtml: `
            <label>조정 값
                <input type="number" id="modalPointDelta" placeholder="지급: +1000 / 차감: -500">
            </label>
        `,
        getExtraPayload: () => ({ delta: Number(document.getElementById("modalPointDelta")?.value || 0) }),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/users/${userId}/points`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadOwnerApplications();
            return "사업자 포인트가 조정되었습니다.";
        }
    });
}

function openOwnerApplicationModal(requestId, storeName, mode) {
    const isApprove = mode === "approve";
    openActionModal({
        title: isApprove ? "사업자 신청 승인" : "사업자 신청 반려",
        summary: `${storeName} 사업자 신청을 ${isApprove ? "승인" : "반려"}합니다.`,
        confirmText: isApprove ? "승인" : "반려",
        requireReasonCode: false,
        showReasonDetail: !isApprove,
        requireReasonDetail: !isApprove,
        reasonDetailLabel: "반려 사유",
        reasonDetailPlaceholder: "신청자에게 보여줄 반려 사유를 입력해 주세요.",
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/owner-applications/${requestId}/${mode}`, {
                method: "POST",
                body: JSON.stringify({ reviewComment: reasonPayload.reasonDetail || null })
            });
            await loadOwnerApplications();
            await refreshDashboardSilently();
            return isApprove ? "사업자 신청을 승인했습니다." : "사업자 신청을 반려했습니다.";
        }
    });
}

function openUserPointModal(userId, userName, currentPoint) {
    openActionModal({
        title: "포인트 조정",
        summary: `${userName} 사용자의 포인트를 조정합니다. (현재: ${numberFormat(currentPoint)}P)`,
        confirmText: "포인트 조정",
        reasonOptions: toReasonOptions(["POINT_ADJUSTMENT", "SPAM", "ABUSE", "POLICY", "REPORT_POLICY", "OTHER"]),
        extraHtml: `
            <label>조정 값
                <input type="number" id="modalPointDelta" placeholder="지급: +100 / 차감: -500">
            </label>
        `,
        getExtraPayload: () => ({ delta: Number(document.getElementById("modalPointDelta")?.value || 0) }),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/users/${userId}/points`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadUsers();
            return "포인트가 조정되었습니다.";
        }
    });
}

function openUserStatusModal(userId, userName, currentStatus) {
    const nextStatus = currentStatus === "SUSPENDED" ? "ACTIVE" : "SUSPENDED";
    openActionModal({
        title: "계정 상태 변경",
        summary: `${userName} 사용자를 ${labelForAccountStatus(nextStatus)} 상태로 변경합니다.`,
        confirmText: currentStatus === "SUSPENDED" ? "정지 해제" : "계정 정지",
        reasonOptions: toReasonOptions(nextStatus === "SUSPENDED"
            ? ["SUSPENSION_POLICY", "SPAM", "ABUSE", "POLICY", "REPORT_POLICY", "OTHER"]
            : ["MANUAL_RESTORE", "SUSPENSION_POLICY", "REPORT_POLICY", "OTHER"]),
        getExtraPayload: () => ({ accountStatus: nextStatus }),
        submit: async (reasonPayload) => {
            const response = await apiRequest(`/admin/api/users/${userId}/account-status`, {
                method: "PATCH",
                body: JSON.stringify(reasonPayload)
            });
            await loadUsers();
            await refreshDashboardSilently();
            return `계정 상태가 ${labelForAccountStatus(response.accountStatus)}로 변경되었습니다.`;
        }
    });
}

async function populateCategoryFilter() {
    const categories = await apiRequest("/admin/api/post-categories");
    const select = document.getElementById("postsCategory");
    if (!select) {
        return;
    }
    select.innerHTML = `<option value="">전체</option>` + categories.map((item) =>
        `<option value="${item.id}">${escapeHtml(item.emoji)} ${escapeHtml(item.name)}</option>`
    ).join("");
}

async function loadPosts() {
    const query = new URLSearchParams({ page: String(adminState.postsPage), size: "10" });
    appendIfPresent(query, "keyword", document.getElementById("postsKeyword")?.value);
    appendIfPresent(query, "categoryId", document.getElementById("postsCategory")?.value);
    appendIfPresent(query, "status", document.getElementById("postsStatus")?.value);
    appendIfPresent(query, "purgeReady", document.getElementById("postsPurgeReady")?.value);
    appendIfPresent(query, "dateFrom", document.getElementById("postsDateFrom")?.value);
    appendIfPresent(query, "dateTo",   document.getElementById("postsDateTo")?.value);

    const data = await apiRequest(`/admin/api/posts?${query.toString()}`);
    const body = document.getElementById("postsTableBody");
    if (!body) {
        return;
    }

    if (!data.items.length) {
        body.innerHTML = `<tr><td colspan="5"><div class="empty-state">조회 결과가 없습니다.</div></td></tr>`;
    } else {
        body.innerHTML = data.items.map((item) => `
            <tr>
                <td>
                    <div class="stack">
                        <strong>
                            <a class="content-link" href="/community/post/${item.id}" target="_blank">${escapeHtml(item.title)}</a>
                        </strong>
                        <span class="muted">${escapeHtml(item.authorName)} · ID ${item.authorId}</span>
                        <span class="muted">${formatDate(item.createdAt)}${item.deletedAt ? " · 삭제 " + formatDate(item.deletedAt) : ""}</span>
                    </div>
                </td>
                <td><span class="badge user">${escapeHtml(item.categoryName || "미분류")}</span></td>
                <td>
                    <span class="badge ${item.deletedAt ? "deleted" : "active"}">${item.deletedAt ? "숨김" : "활성"}</span>
                    ${item.purgeEligible ? `<span class="badge danger" style="margin-top:4px;">영구삭제 가능</span>` : ""}
                </td>
                <td class="muted">조회 ${numberFormat(item.viewCount)} / 댓글 ${numberFormat(item.commentCount)}</td>
                <td>
                    <div class="action-group">
                        <button type="button" class="${item.deletedAt ? "primary-button" : "danger-button"}" data-post-action="${item.id}" data-post-mode="${item.deletedAt ? "restore" : "hide"}" data-post-title="${escapeHtml(item.title)}">${item.deletedAt ? "복구" : "숨김"}</button>
                        ${item.deletedAt && item.purgeEligible ? `<button type="button" class="danger-button" data-post-purge="${item.id}" data-post-title="${escapeHtml(item.title)}">영구 삭제</button>` : ""}
                    </div>
                </td>
            </tr>
        `).join("");
    }

    body.querySelectorAll("[data-post-action]").forEach((button) => {
        button.addEventListener("click", () => openPostActionModal(button.dataset.postAction, button.dataset.postMode, button.dataset.postTitle));
    });
    body.querySelectorAll("[data-post-purge]").forEach((button) => {
        button.addEventListener("click", () => openPostPurgeModal(button.dataset.postPurge, button.dataset.postTitle));
    });

    renderPagination("postsPagination", data, (page) => {
        adminState.postsPage = page;
        loadPosts().catch((error) => showAlert(error.message, "error"));
    });
}

function openPostActionModal(postId, mode, title) {
    const isRestore = mode === "restore";
    openActionModal({
        title: isRestore ? "게시글 복구" : "게시글 숨김",
        summary: `"${title}" 게시글을 ${isRestore ? "복구" : "숨김"}합니다.`,
        confirmText: isRestore ? "복구" : "숨김",
        reasonOptions: toReasonOptions(isRestore
            ? ["MANUAL_RESTORE", "REPORT_POLICY", "POLICY", "OTHER"]
            : ["SPAM", "ABUSE", "OFF_TOPIC", "POLICY", "DUPLICATE", "REPORT_POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/posts/${postId}/${mode}`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadPosts();
            await refreshDashboardSilently();
            return isRestore ? "게시글이 복구되었습니다." : "게시글이 숨김 처리되었습니다.";
        }
    });
}

function openPostPurgeModal(postId, title) {
    openActionModal({
        title: "게시글 영구 삭제",
        summary: `"${title}" 게시글을 복구할 수 없도록 영구 삭제합니다.`,
        confirmText: "영구 삭제",
        reasonOptions: toReasonOptions(["PURGE_POLICY", "SPAM", "ABUSE", "POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/posts/${postId}/purge`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadPosts();
            await refreshDashboardSilently();
            return "게시글이 영구 삭제되었습니다.";
        }
    });
}
async function loadComments() {
    const query = new URLSearchParams({ page: String(adminState.commentsPage), size: "10" });
    appendIfPresent(query, "keyword", document.getElementById("commentsKeyword")?.value);
    appendIfPresent(query, "status", document.getElementById("commentsStatus")?.value);
    appendIfPresent(query, "purgeReady", document.getElementById("commentsPurgeReady")?.value);
    appendIfPresent(query, "dateFrom", document.getElementById("commentsDateFrom")?.value);
    appendIfPresent(query, "dateTo",   document.getElementById("commentsDateTo")?.value);

    const data = await apiRequest(`/admin/api/comments?${query.toString()}`);
    const body = document.getElementById("commentsTableBody");
    if (!body) {
        return;
    }

    if (!data.items.length) {
        body.innerHTML = `<tr><td colspan="4"><div class="empty-state">조회 결과가 없습니다.</div></td></tr>`;
    } else {
        body.innerHTML = data.items.map((item) => `
            <tr>
                <td>
                    <div class="stack">
                        <strong>${escapeHtml(item.authorName)}</strong>
                        <span class="muted">${escapeHtml(item.contentPreview || "내용 없음")}</span>
                        <span class="muted">${formatDate(item.createdAt)}${item.deletedAt ? " · 삭제 " + formatDate(item.deletedAt) : ""}</span>
                    </div>
                </td>
                <td>
                    <div class="stack">
                        <a class="content-link" href="/community/post/${item.postId}" target="_blank">${escapeHtml(item.postTitle || "#" + item.postId)}</a>
                        ${item.parentId ? `<span class="muted">대댓글 (부모 #${item.parentId})</span>` : ""}
                    </div>
                </td>
                <td>
                    <span class="badge ${item.deletedAt ? "deleted" : "active"}">${item.deletedAt ? "숨김" : "활성"}</span>
                    ${item.purgeEligible ? `<span class="badge danger" style="margin-top:4px;">영구삭제 가능</span>` : ""}
                </td>
                <td>
                    <div class="action-group">
                        <button type="button" class="${item.deletedAt ? "primary-button" : "danger-button"}" data-comment-action="${item.id}" data-comment-mode="${item.deletedAt ? "restore" : "hide"}">${item.deletedAt ? "복구" : "숨김"}</button>
                        ${item.deletedAt && item.purgeEligible ? `<button type="button" class="danger-button" data-comment-purge="${item.id}">영구 삭제</button>` : ""}
                    </div>
                </td>
            </tr>
        `).join("");
    }

    body.querySelectorAll("[data-comment-action]").forEach((button) => {
        button.addEventListener("click", () => openCommentActionModal(button.dataset.commentAction, button.dataset.commentMode));
    });
    body.querySelectorAll("[data-comment-purge]").forEach((button) => {
        button.addEventListener("click", () => openCommentPurgeModal(button.dataset.commentPurge));
    });

    renderPagination("commentsPagination", data, (page) => {
        adminState.commentsPage = page;
        loadComments().catch((error) => showAlert(error.message, "error"));
    });
}

function openCommentActionModal(commentId, mode) {
    const isRestore = mode === "restore";
    openActionModal({
        title: isRestore ? "댓글 복구" : "댓글 숨김",
        summary: `댓글 #${commentId}를 ${isRestore ? "복구" : "숨김"}합니다.`,
        confirmText: isRestore ? "복구" : "숨김",
        reasonOptions: toReasonOptions(isRestore
            ? ["MANUAL_RESTORE", "REPORT_POLICY", "POLICY", "OTHER"]
            : ["SPAM", "ABUSE", "OFF_TOPIC", "POLICY", "DUPLICATE", "REPORT_POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/comments/${commentId}/${mode}`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadComments();
            await refreshDashboardSilently();
            return isRestore ? "댓글이 복구되었습니다." : "댓글이 숨김 처리되었습니다.";
        }
    });
}

function openCommentPurgeModal(commentId) {
    openActionModal({
        title: "댓글 영구 삭제",
        summary: `댓글 #${commentId}를 복구할 수 없도록 영구 삭제합니다.`,
        confirmText: "영구 삭제",
        reasonOptions: toReasonOptions(["PURGE_POLICY", "SPAM", "ABUSE", "POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/comments/${commentId}/purge`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadComments();
            await refreshDashboardSilently();
            return "댓글이 영구 삭제되었습니다.";
        }
    });
}

async function loadReports() {
    const query = new URLSearchParams({ page: String(adminState.reportsPage), size: "10" });
    appendIfPresent(query, "targetType", document.getElementById("reportsTargetType")?.value);
    appendIfPresent(query, "status", document.getElementById("reportsStatus")?.value);
    appendIfPresent(query, "reasonCode", document.getElementById("reportsReasonCode")?.value);
    appendIfPresent(query, "dateFrom",       document.getElementById("reportsDateFrom")?.value);
    appendIfPresent(query, "dateTo",         document.getElementById("reportsDateTo")?.value);

    const data = await apiRequest(`/admin/api/reports?${query.toString()}`);
    const body = document.getElementById("reportsTableBody");
    if (!body) {
        return;
    }

    if (!data.items.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">조회 결과가 없습니다.</div></td></tr>`;
    } else {
        body.innerHTML = data.items.map((item) => `
            <tr>
                <td class="muted">${formatDate(item.createdAt)}</td>
                <td>
                    <div class="stack">
                        <strong>${escapeHtml(item.reporterUserName)}</strong>
                        <span class="muted">ID ${item.reporterUserId}</span>
                    </div>
                </td>
                <td>
                    <div class="stack">
                        <strong>${escapeHtml(labelForTarget(item.targetType))}</strong>
                        <a class="content-link" href="/community/post/${item.targetType === "POST" ? item.targetId : item.postId}" target="_blank">${escapeHtml(item.targetSummary)}</a>
                        ${item.openCountForTarget > 1 ? `<span class="open-count-badge">신고 ${item.openCountForTarget}건</span>` : ""}
                    </div>
                </td>
                <td>
                    <div class="stack">
                        <strong>${escapeHtml(labelForReportReason(item.reasonCode))}</strong>
                        <span class="muted">${escapeHtml(item.reasonDetail || "상세 메모 없음")}</span>
                    </div>
                </td>
                <td>
                    <div class="stack">
                        <span class="badge ${item.status === "RESOLVED" ? "success" : item.status === "REJECTED" ? "deleted" : item.status === "REVIEWING" ? "admin" : "filter"}">${escapeHtml(labelForReportStatus(item.status))}</span>
                        <span class="muted">${escapeHtml(item.reviewedByAdminName || "미처리")}${item.reviewedAt ? " · " + formatDate(item.reviewedAt) : ""}</span>
                    </div>
                </td>
                <td>
                    <div class="action-group">
                        ${item.status !== "REVIEWING" ? `<button type="button" class="tiny-button" data-report-status="${item.id}" data-next-status="REVIEWING" data-target-summary="${escapeHtml(item.targetSummary)}">검토 중</button>` : ""}
                        ${item.status !== "RESOLVED" ? `<button type="button" class="primary-button" data-report-status="${item.id}" data-next-status="RESOLVED" data-target-summary="${escapeHtml(item.targetSummary)}">해결</button>` : ""}
                        ${item.status !== "REJECTED" ? `<button type="button" class="danger-button" data-report-status="${item.id}" data-next-status="REJECTED" data-target-summary="${escapeHtml(item.targetSummary)}">반려</button>` : ""}
                    </div>
                </td>
            </tr>
        `).join("");
    }

    body.querySelectorAll("[data-report-status]").forEach((button) => {
        button.addEventListener("click", () => openReportStatusModal(button.dataset.reportStatus, button.dataset.nextStatus, button.dataset.targetSummary));
    });

    renderPagination("reportsPagination", data, (page) => {
        adminState.reportsPage = page;
        loadReports().catch((error) => showAlert(error.message, "error"));
    });
}

function openReportStatusModal(reportId, nextStatus, targetSummary) {
    openActionModal({
        title: "신고 상태 변경",
        summary: `${targetSummary} 신고 상태를 ${labelForReportStatus(nextStatus)}로 변경합니다.`,
        confirmText: "상태 변경",
        reasonOptions: toReasonOptions(["REPORT_POLICY", "SPAM", "ABUSE", "OFF_TOPIC", "POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            const response = await apiRequest(`/admin/api/reports/${reportId}/status`, {
                method: "PATCH",
                body: JSON.stringify({ status: nextStatus, ...reasonPayload })
            });
            await loadReports();
            await refreshDashboardSilently();
            return `신고 상태가 ${labelForReportStatus(response.status)}로 변경되었습니다.`;
        }
    });
}

async function loadLogs() {
    const query = new URLSearchParams({ page: String(adminState.logsPage), size: "10" });
    appendIfPresent(query, "targetType", document.getElementById("logsTargetType")?.value);
    appendIfPresent(query, "actionType", document.getElementById("logsActionType")?.value);
    appendIfPresent(query, "reasonCode", document.getElementById("logsReasonCode")?.value);
    appendIfPresent(query, "dateFrom", document.getElementById("logsDateFrom")?.value);
    appendIfPresent(query, "dateTo",   document.getElementById("logsDateTo")?.value);

    const data = await apiRequest(`/admin/api/logs?${query.toString()}`);
    const body = document.getElementById("logsTableBody");
    if (!body) {
        return;
    }

    if (!data.items.length) {
        body.innerHTML = `<tr><td colspan="6"><div class="empty-state">조회 결과가 없습니다.</div></td></tr>`;
    } else {
        body.innerHTML = data.items.map((item) => `
            <tr>
                <td>${formatDate(item.createdAt)}</td>
                <td><div class="stack"><strong>${escapeHtml(item.adminUserName)}</strong><span class="muted">ID ${item.adminUserId}</span></div></td>
                <td><div class="stack"><strong>${escapeHtml(labelForTarget(item.targetType))}</strong><span class="muted">${escapeHtml(item.targetSummary)}${item.targetId ? ` / #${item.targetId}` : ""}</span></div></td>
                <td><span class="badge filter">${escapeHtml(labelForAction(item.actionType))}</span></td>
                <td><div class="stack"><strong>${escapeHtml(labelForReason(item.reasonCode))}</strong><span class="muted">${escapeHtml(item.reasonDetail || "상세 메모 없음")}</span></div></td>
                <td class="log-snapshot">
                    ${renderSnapshotDetails("변경 전", item.beforeSnapshotJson)}
                    ${renderSnapshotDetails("변경 후", item.afterSnapshotJson)}
                </td>
            </tr>
        `).join("");
    }

    renderPagination("logsPagination", data, (page) => {
        adminState.logsPage = page;
        loadLogs().catch((error) => showAlert(error.message, "error"));
    });
}

function renderSnapshotDetails(label, snapshotJson) {
    return `
        <details>
            <summary>${escapeHtml(label)}</summary>
            <pre>${escapeHtml(prettyJson(snapshotJson))}</pre>
        </details>
    `;
}

function prettyJson(value) {
    try {
        return JSON.stringify(JSON.parse(value || "{}"), null, 2);
    } catch (error) {
        return value || "{}";
    }
}
function openActionModal(config) {
    adminState.modal = config;
    document.getElementById("actionModalTitle").textContent = config.title;
    document.getElementById("actionModalSummary").textContent = config.summary;
    document.getElementById("modalConfirmButton").textContent = config.confirmText || "실행";
    const reasonCodeGroup = document.getElementById("modalReasonCodeGroup");
    const reasonDetailGroup = document.getElementById("modalReasonDetailGroup");
    const reasonDetail = document.getElementById("modalReasonDetail");
    reasonCodeGroup.hidden = config.requireReasonCode === false;
    reasonDetailGroup.hidden = config.showReasonDetail === false;
    fillSelect("modalReasonCode", config.reasonOptions || REASON_OPTIONS, { firstLabel: "사유 코드를 선택해 주세요" });
    document.getElementById("modalReasonCode").value = "";
    document.getElementById("modalReasonDetailLabel").textContent = config.reasonDetailLabel || "상세 메모";
    reasonDetail.placeholder = config.reasonDetailPlaceholder || "필요 시 조치 사유를 추가로 남겨 주세요. OTHER 선택 시 필수입니다.";
    reasonDetail.value = "";
    const extraFieldsEl = document.getElementById("modalExtraFields");
    if (extraFieldsEl) {
        extraFieldsEl.innerHTML = config.extraHtml || "";
    }
    clearModalErrors();
    setModalLoading(false);
    document.getElementById("actionModal").hidden = false;
    document.body.style.overflow = "hidden";
}

function closeActionModal() {
    adminState.modal = null;
    const modal = document.getElementById("actionModal");
    if (modal) {
        modal.hidden = true;
    }
    document.body.style.overflow = "";
    clearModalErrors();
}

async function handleModalSubmit(event) {
    event.preventDefault();
    if (!adminState.modal) {
        return;
    }

    const extraPayload = adminState.modal.getExtraPayload ? adminState.modal.getExtraPayload() : {};
    const payload = {
        reasonCode: document.getElementById("modalReasonCode").value,
        reasonDetail: document.getElementById("modalReasonDetail").value.trim(),
        ...extraPayload
    };
    const clientErrors = validateModalPayload(payload);
    if (Object.keys(clientErrors).length) {
        renderModalErrors({ message: "입력한 값을 확인해 주세요.", errors: clientErrors });
        return;
    }

    try {
        setModalLoading(true);
        clearModalErrors();
        const message = await adminState.modal.submit(payload);
        closeActionModal();
        showAlert(message || "작업이 완료되었습니다.");
    } catch (error) {
        renderModalErrors(error);
        setModalLoading(false);
    }
}

function validateModalPayload(payload) {
    const errors = {};
    const config = adminState.modal || {};
    if (config.requireReasonCode !== false && !payload.reasonCode) {
        errors.reasonCode = "사유 코드를 선택해 주세요.";
    }
    if (config.requireReasonDetail && !payload.reasonDetail) {
        errors.reasonDetail = config.reasonDetailRequiredMessage || "상세 메모를 입력해 주세요.";
    } else if (payload.reasonCode === "OTHER" && !payload.reasonDetail) {
        errors.reasonDetail = "기타 사유를 입력해 주세요.";
    }
    return errors;
}

function clearModalErrors() {
    document.getElementById("modalReasonCodeError").textContent = "";
    document.getElementById("modalReasonDetailError").textContent = "";
    const general = document.getElementById("modalGeneralError");
    general.textContent = "";
    general.hidden = true;
}

function renderModalErrors(error) {
    clearModalErrors();
    const general = document.getElementById("modalGeneralError");
    const message = error?.message || "처리 중 오류가 발생했습니다.";
    const errors = error?.errors || {};
    const extraErrors = Object.entries(errors)
        .filter(([field]) => field !== "reasonCode" && field !== "reasonDetail")
        .map(([, value]) => value);
    const generalLines = [message, ...extraErrors].filter(Boolean);
    if (generalLines.length) {
        general.textContent = generalLines.join(" ");
        general.hidden = false;
    }
    if (errors.reasonCode) {
        document.getElementById("modalReasonCodeError").textContent = errors.reasonCode;
    }
    if (errors.reasonDetail) {
        document.getElementById("modalReasonDetailError").textContent = errors.reasonDetail;
    }
}

function setModalLoading(isLoading) {
    const confirmButton = document.getElementById("modalConfirmButton");
    const cancelButton = document.getElementById("modalCancelButton");
    const closeButton = document.getElementById("modalCloseButton");
    const reasonCode = document.getElementById("modalReasonCode");
    const reasonDetail = document.getElementById("modalReasonDetail");
    const defaultText = adminState.modal?.confirmText || "실행";

    confirmButton.disabled = isLoading;
    cancelButton.disabled = isLoading;
    closeButton.disabled = isLoading;
    reasonCode.disabled = isLoading;
    reasonDetail.disabled = isLoading;
    confirmButton.textContent = isLoading ? "처리 중..." : defaultText;
}

function renderPagination(containerId, pageData, onPageChange) {
    const container = document.getElementById(containerId);
    if (!container) {
        return;
    }
    if (!pageData.totalPages || pageData.totalPages <= 1) {
        container.innerHTML = "";
        return;
    }

    container.innerHTML = `
        <button type="button" ${pageData.page <= 0 ? "disabled" : ""} data-page-move="prev">이전</button>
        <span class="muted">${pageData.page + 1} / ${pageData.totalPages}</span>
        <button type="button" ${pageData.page >= pageData.totalPages - 1 ? "disabled" : ""} data-page-move="next">다음</button>
    `;
    container.querySelector('[data-page-move="prev"]')?.addEventListener("click", () => onPageChange(pageData.page - 1));
    container.querySelector('[data-page-move="next"]')?.addEventListener("click", () => onPageChange(pageData.page + 1));
}

async function apiRequest(url, options = {}) {
    const method = options.method || "GET";
    const init = { ...options, headers: { ...(options.headers || {}) } };
    if (method !== "GET" && csrfToken && csrfHeader) {
        init.headers[csrfHeader] = csrfToken;
    }
    if (options.body) {
        init.headers["Content-Type"] = init.headers["Content-Type"] || "application/json";
    }

    const response = await fetch(url, init);
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
        throw new ApiError(payload?.message || "요청 처리에 실패했습니다.", {
            status: response.status,
            errors: payload?.errors || {}
        });
    }
    return payload;
}

function appendIfPresent(query, key, value) {
    if (value != null && String(value).trim() !== "") {
        query.append(key, String(value).trim());
    }
}

function showAlert(message, type = "success") {
    const banner = document.getElementById("alertBanner");
    if (!banner) {
        return;
    }
    banner.textContent = message;
    banner.className = `alert-banner show ${type}`;
    window.clearTimeout(showAlert.timeoutId);
    showAlert.timeoutId = window.setTimeout(() => {
        banner.className = "alert-banner";
    }, 3200);
}

function labelForReason(value) {
    return REASON_LABELS[value] || value || "-";
}

function labelForTarget(value) {
    return TARGET_TYPE_LABELS[value] || value || "-";
}

function labelForAction(value) {
    return ACTION_TYPE_LABELS[value] || value || "-";
}

function labelForAccountStatus(value) {
    return ACCOUNT_STATUS_LABELS[value] || value || "-";
}

function labelForOwnerStatus(value) {
    const labels = {
        PENDING: "검토 대기",
        APPROVED: "승인 완료",
        REJECTED: "반려"
    };
    return labels[value] || value || "-";
}

function classForOwnerStatus(status) {
    if (status === "APPROVED") {
        return "success";
    }
    if (status === "REJECTED") {
        return "deleted";
    }
    return "warning";
}

function classForRole(role) {
    if (role === "ADMIN") {
        return "admin";
    }
    if (role === "OWNER") {
        return "warning";
    }
    return "user";
}

function labelForReportReason(value) {
    return REPORT_REASON_LABELS[value] || value || "-";
}

function labelForReportStatus(value) {
    return REPORT_STATUS_LABELS[value] || value || "-";
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatDate(value) {
    if (!value) {
        return "-";
    }
    return new Intl.DateTimeFormat("ko-KR", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function numberFormat(value) {
    return new Intl.NumberFormat("ko-KR").format(value ?? 0);
}
