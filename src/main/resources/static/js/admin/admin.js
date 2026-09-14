const adminState = {
    activePage: document.body.dataset.page,
    currentUserId: document.body.dataset.currentUserId,
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

// 화면에 보이는 이름은 그대로다. 값 이름만 뜻에 맞게 바꿨다.
// 전에는 성적 콘텐츠가 SPAM, 자살·자해가 MISINFORMATION, 스팸이 MISLEADING 으로
// 저장돼서, DB 를 직접 보는 사람만 다르게 읽었다. 순서는 신고 창과 같다.
const REPORT_REASON_LABELS = {
    SEXUAL: "성적 콘텐츠",
    ABUSE: "폭력·혐오 콘텐츠",
    HATE: "증오·악의적 콘텐츠",
    HARASSMENT: "괴롭힘·폭력",
    HARMFUL: "유해·위험 행위",
    SELF_HARM: "자살·자해",
    MISINFORMATION: "잘못된 정보",
    CHILD_ABUSE: "아동 학대",
    TERRORISM: "테러 조장",
    SPAM: "스팸·혼동 콘텐츠",
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
    document.getElementById("showAllUsersButton")?.addEventListener("click", () => {
        loadAllUsers().catch((error) => showAlert(error.message, "error"));
    });
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
    bindMoreMenus();
}

/**
 * 주 동작 하나만 내놓고 나머지는 ⋯ 안에 접는다.
 *
 * 버튼 네댓 개를 같은 크기로 늘어놓으면 매번 읽고 고르게 된다. 가장 자주 하는
 * 하나만 눈에 띄게 두면 손이 먼저 간다. 반려·정지·영구 삭제처럼 되돌리기 어려운
 * 것이 한 번 더 눌러야 닿는 자리로 가는 것도 덤이다.
 */
function withMoreMenu(primaryHtml, moreHtmlList) {
    const more = (moreHtmlList || []).filter(Boolean);
    if (!more.length) {
        return `<div class="action-group">${primaryHtml}</div>`;
    }
    return `
        <div class="action-group">
            ${primaryHtml}
            <div class="more-menu">
                <button type="button" class="more-btn" aria-haspopup="true" aria-expanded="false" aria-label="다른 조치">&#8943;</button>
                <div class="more-pop" hidden>${more.join("")}</div>
            </div>
        </div>
    `;
}

/** 열려 있는 ⋯ 메뉴를 모두 닫는다. */
function closeMoreMenus(except) {
    document.querySelectorAll(".more-pop:not([hidden])").forEach((pop) => {
        if (pop === except) {
            return;
        }
        pop.hidden = true;
        pop.previousElementSibling?.setAttribute("aria-expanded", "false");
    });
}

/**
 * ⋯ 메뉴를 화면 기준(fixed)으로 띄운다.
 *
 * 표를 감싼 상자가 overflow:hidden 이라 행 안에 absolute 로 두면 메뉴가 잘린다.
 * 실제로 잘렸고, 표에 세로 스크롤바까지 생겼다. 화면 기준으로 띄우면 어떤 상자에도
 * 갇히지 않는다. 아래 공간이 모자라면 위로 연다.
 */
function positionMorePop(trigger, pop) {
    // 크기를 재려면 그려져 있어야 하므로 잠깐 보이지 않게만 해 둔다
    pop.style.visibility = "hidden";
    pop.hidden = false;

    const button = trigger.getBoundingClientRect();
    const menu = pop.getBoundingClientRect();
    const gap = 6;
    const margin = 8;

    const openUp = window.innerHeight - button.bottom < menu.height + gap + margin;
    const top = openUp ? button.top - menu.height - gap : button.bottom + gap;
    const left = Math.min(
        Math.max(margin, button.right - menu.width),
        window.innerWidth - menu.width - margin
    );

    pop.style.top = `${Math.max(margin, top)}px`;
    pop.style.left = `${left}px`;
    pop.style.visibility = "";
}

/** ⋯ 메뉴는 목록을 다시 그려도 살아 있어야 해서 document 에 한 번만 건다. */
function bindMoreMenus() {
    document.addEventListener("click", (event) => {
        const trigger = event.target.closest(".more-btn");
        if (!trigger) {
            closeMoreMenus();
            return;
        }

        const pop = trigger.nextElementSibling;
        const wasOpen = !pop.hidden;
        closeMoreMenus(pop);

        if (wasOpen) {
            pop.hidden = true;
            trigger.setAttribute("aria-expanded", "false");
            return;
        }
        positionMorePop(trigger, pop);
        trigger.setAttribute("aria-expanded", "true");
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeMoreMenus();
        }
    });

    // 화면 기준으로 띄운 메뉴는 스크롤을 따라오지 않는다. 그럴 땐 닫는 게 맞다.
    window.addEventListener("scroll", () => closeMoreMenus(), true);
    window.addEventListener("resize", () => closeMoreMenus());
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
    // 사이드바 배지는 어느 화면에 있든 보여야 한다. 대시보드면 그쪽에서 같이 채우므로
    // 두 번 부르지 않는다.
    if (adminState.activePage !== "dashboard") {
        refreshNavBadges();
    }

    switch (adminState.activePage) {
        case "dashboard":
            await loadDashboard();
            break;
        case "users":
            await loadAllUsers();
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

/**
 * 처리 대기 큐.
 *
 * 대시보드에 "열린 신고 3"만 띄우고 누르면 신고 화면으로 보내는 방식은,
 * 결국 한 단계를 더 거치게 만든다. 밀린 건을 여기에 그대로 늘어놓고
 * 조치 버튼도 같이 둔다. 오래 기다린 것이 위로 온다.
 */
async function loadWorkQueue() {
    const queue = await apiRequest("/admin/api/work-queue");
    setNavBadges(queue);

    const total = (queue.openReportCount || 0) + (queue.pendingOwnerCount || 0);
    setText("queueTotal", numberFormat(total) + "건");

    const container = document.getElementById("workQueue");
    if (!container) {
        return;
    }

    // 조치 버튼이 대상·작성자를 다시 물어보지 않아도 되게 들고 있는다
    adminState.reportsById = {};
    (queue.reports || []).forEach((item) => { adminState.reportsById[item.id] = item; });

    if (!total) {
        container.innerHTML = `<div class="empty-state">밀린 처리가 없습니다.</div>`;
        return;
    }

    const reports = queue.reports || [];
    const owners = queue.ownerApplications || [];
    container.innerHTML = reports.map(renderQueueReport).join("")
        + owners.map(renderQueueOwner).join("")
        + renderQueueMore(queue, reports.length + owners.length, total);

    container.querySelectorAll("[data-report-target]").forEach((button) => {
        button.addEventListener("click", () => openReportTargetModal(adminState.reportsById[button.dataset.reportTarget]));
    });
    container.querySelectorAll("[data-report-author]").forEach((button) => {
        button.addEventListener("click", () => openReportAuthorModal(adminState.reportsById[button.dataset.reportAuthor]));
    });
    container.querySelectorAll("[data-report-status]").forEach((button) => {
        button.addEventListener("click", () => openReportStatusModal(button.dataset.reportStatus, button.dataset.nextStatus, button.dataset.targetSummary));
    });
    container.querySelectorAll("[data-owner-approve]").forEach((button) => {
        button.addEventListener("click", () => openOwnerApplicationModal(button.dataset.ownerApprove, button.dataset.storeName, "approve"));
    });
    container.querySelectorAll("[data-owner-reject]").forEach((button) => {
        button.addEventListener("click", () => openOwnerApplicationModal(button.dataset.ownerReject, button.dataset.storeName, "reject"));
    });
}

/**
 * 큐는 여덟 건까지만 싣는다. 그보다 밀렸으면 나머지가 몇 건인지 알려 준다.
 * 머리에는 20건이라고 떠 있는데 목록은 여덟 줄뿐이면 나머지가 어디 갔는지 알 수 없다.
 */
function renderQueueMore(queue, shown, total) {
    if (total <= shown) {
        return "";
    }
    const links = [];
    if ((queue.openReportCount || 0) > (queue.reports || []).length) {
        links.push(`<a class="content-link" href="/admin/reports">신고 전체 보기</a>`);
    }
    if ((queue.pendingOwnerCount || 0) > (queue.ownerApplications || []).length) {
        links.push(`<a class="content-link" href="/admin/owner-applications">사업자 신청 전체 보기</a>`);
    }
    return `
        <div class="queue-more">
            <span class="muted">여기 보이지 않는 ${numberFormat(total - shown)}건이 더 밀려 있습니다.</span>
            ${links.join("")}
        </div>
    `;
}

function renderQueueReport(item) {
    const postId = item.targetType === "POST" ? item.targetId : item.postId;
    const marks = [
        item.openCountForTarget > 1 ? `신고 ${item.openCountForTarget}건` : "",
        item.targetHidden ? "숨김됨" : "",
        item.targetAuthorSuspended ? "작성자 정지됨" : ""
    ].filter(Boolean);

    return `
        <div class="queue-row">
            <span class="queue-kind is-report">신고</span>
            <div class="queue-body">
                <div class="queue-title">
                    <strong>${escapeHtml(labelForReportReason(item.reasonCode))}</strong>
                    <a class="content-link" href="/community/post/${postId}" target="_blank" rel="noopener">${escapeHtml(item.targetSummary)}</a>
                </div>
                <div class="queue-meta">
                    ${escapeHtml(labelForTarget(item.targetType))}
                    ${item.targetAuthorName ? ` · 작성자 ${escapeHtml(item.targetAuthorName)}` : ""}
                    · ${escapeHtml(waitedLabel(item.createdAt))}
                    ${marks.length ? ` · ${escapeHtml(marks.join(" · "))}` : ""}
                </div>
            </div>
            ${withMoreMenu(
                `<button type="button" class="primary-button" data-report-status="${item.id}" data-next-status="RESOLVED" data-target-summary="${escapeHtml(item.targetSummary)}">해결</button>`,
                [
                    `<button type="button" class="tiny-button" data-report-target="${item.id}">${item.targetHidden ? "글 복구" : "글 숨김"}</button>`,
                    item.targetAuthorId ? `<button type="button" class="tiny-button" data-report-author="${item.id}">${item.targetAuthorSuspended ? "작성자 정지 해제" : "작성자 정지"}</button>` : "",
                    `<button type="button" class="danger-button" data-report-status="${item.id}" data-next-status="REJECTED" data-target-summary="${escapeHtml(item.targetSummary)}">반려</button>`
                ]
            )}
        </div>
    `;
}

function renderQueueOwner(item) {
    return `
        <div class="queue-row">
            <span class="queue-kind is-owner">신청</span>
            <div class="queue-body">
                <div class="queue-title">
                    <strong>${escapeHtml(item.storeName)}</strong>
                    <span class="muted">${escapeHtml(item.businessRegistrationNumber)}</span>
                </div>
                <div class="queue-meta">
                    ${item.ownerName ? `대표 ${escapeHtml(item.ownerName)} · ` : ""}${escapeHtml(item.userName)} 신청 · ${escapeHtml(waitedLabel(item.createdAt))}
                </div>
                <div class="queue-docs">
                    ${documentLink(item.id, item.businessRegistrationFilePath, "registration", "사업자등록증")}
                    ${documentLink(item.id, item.businessLicenseFilePath, "license", "영업신고증")}
                    ${documentLink(item.id, item.menuFilePath, "menu", "메뉴")}
                </div>
            </div>
            <div class="action-group">
                <button type="button" class="primary-button" data-owner-approve="${item.id}" data-store-name="${escapeHtml(item.storeName)}">승인</button>
                <button type="button" class="danger-button" data-owner-reject="${item.id}" data-store-name="${escapeHtml(item.storeName)}">반려</button>
            </div>
        </div>
    `;
}

/** 며칠째 기다렸는지. 오래 밀린 건이 눈에 띄어야 한다. */
function waitedLabel(createdAt) {
    if (!createdAt) {
        return "";
    }
    const days = Math.floor((Date.now() - new Date(createdAt).getTime()) / 86400000);
    if (days <= 0) {
        return "오늘 접수";
    }
    return `${days}일 대기`;
}

/**
 * 사이드바의 밀린 건수 배지.
 *
 * 0 이면 아예 감춘다. "0" 이 빨갛게 떠 있으면 할 일이 있는 것처럼 보인다.
 */
function setNavBadges(queue) {
    setNavBadge("navReportsBadge", queue.openReportCount);
    setNavBadge("navOwnerBadge", queue.pendingOwnerCount);
}

function setNavBadge(id, count) {
    const badge = document.getElementById(id);
    if (!badge) {
        return;
    }
    badge.textContent = numberFormat(count);
    badge.hidden = !count;
}

/** 대시보드가 아닌 화면에서 배지만 채운다. 실패해도 화면은 그대로 쓸 수 있어야 한다. */
async function refreshNavBadges() {
    try {
        const queue = await apiRequest("/admin/api/work-queue");
        setNavBadges(queue);
    } catch (error) {
        // 배지는 곁다리다. 못 채웠다고 화면을 막지 않는다.
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
    setText("summaryHide7d", numberFormat(data.summary.recent7dHideActions));
    setText("summaryRestore7d", numberFormat(data.summary.recent7dRestoreActions));

    // 밀린 일은 숫자가 아니라 목록으로 보여 준다. 배지도 거기서 같이 채운다.
    await loadWorkQueue();

    renderActivityList("dashboardUsers", data.recentUsers, (item) => `
        <div class="activity-item">
            <div class="activity-title">${escapeHtml(item.name)}</div>
            <div class="activity-sub">${escapeHtml(item.email)} / ${escapeHtml(item.role)} / ${escapeHtml(labelForAccountStatus(item.accountStatus || "ACTIVE"))}</div>
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

async function loadAllUsers() {
    document.getElementById("usersFilterForm")?.reset();
    adminState.usersPage = 0;
    await loadUsers();
}

async function loadUsers() {
    const query = new URLSearchParams({ page: String(adminState.usersPage), size: "10" });
    appendIfPresent(query, "keyword", document.getElementById("usersKeyword")?.value);
    appendIfPresent(query, "role", document.getElementById("usersRole")?.value);
    appendIfPresent(query, "accountStatus", document.getElementById("usersAccountStatus")?.value);
    appendIfPresent(query, "dateFrom", document.getElementById("usersDateFrom")?.value);
    appendIfPresent(query, "dateTo",   document.getElementById("usersDateTo")?.value);

    const data = await apiRequest(`/admin/api/users?${query.toString()}`);
    const filtered = ["keyword", "role", "accountStatus", "dateFrom", "dateTo"].some((key) => query.has(key));
    setText("usersResultCount", `${filtered ? "검색 결과" : "전체 회원"} ${numberFormat(data.totalElements)}명 · 현재 페이지 ${data.items.length}명`);
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
                    ${withMoreMenu(
                        // 자기 계정을 정지시키면 그 길로 로그아웃되고 다시 못 들어온다. 서버도 막지만
                        // 눌러서 알게 하는 것보다 아예 내지 않는 편이 낫다.
                        String(item.id) === String(adminState.currentUserId)
                            ? `<span class="muted">본인 계정</span>`
                            : `<button type="button" class="tiny-button ${item.accountStatus === "SUSPENDED" ? "" : "danger-button"}" data-user-status="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-status="${item.accountStatus}">${item.accountStatus === "SUSPENDED" ? "정지 해제" : "정지"}</button>`,
                        [
                            `<button type="button" class="tiny-button" data-user-role="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-role="${item.role}" data-current-point="${item.point}">권한 변경</button>`,
                            `<button type="button" class="tiny-button" data-user-point="${item.id}" data-user-name="${escapeHtml(item.name)}" data-current-point="${item.point}">포인트 조정</button>`
                        ]
                    )}
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
    if (!document.getElementById("ownerApplicationsTableBody")) {
        return;
    }
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
                    ${renderOwnerIdentity(item)}
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

/**
 * 국세청 진위확인에 쓴 대표자명과 개업일자를 보여준다.
 *
 * 가입자 이름과 다르다고 경고하지는 않는다. 대표자 본인만 신청할 수 있는 게
 * 아니라 직원이나 가족이 대신 넣는 경우가 흔하다. 정상 신청에 경고가 자꾸 뜨면
 * 관리자가 경고를 무시하게 되고, 정작 봐야 할 때도 안 보게 된다.
 * 서류와 대조할 값을 그대로 보여주는 것까지가 화면의 몫이다.
 */
function renderOwnerIdentity(item) {
    if (!item.ownerName) {
        // 진위확인이 생기기 전에 접수된 신청. 값 자체가 없다.
        return `<span class="muted">대표자명 미확인 · 진위확인 도입 전 접수</span>`;
    }
    return `<span class="muted">대표 ${escapeHtml(item.ownerName)}`
         + ` · 개업 ${escapeHtml(formatBusinessStartDate(item.startDate))}</span>`;
}

/** 20160610 을 2016-06-10 으로 읽기 좋게. */
function formatBusinessStartDate(value) {
    if (!value || value.length !== 8) {
        return value || "-";
    }
    return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
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
                    ${item.promoActive ? `<span class="badge owner" style="margin-top:4px;" title="${formatDate(item.promoUntil)}까지 사이드바 노출">광고 노출중</span>` : ""}
                </td>
                <td class="muted">조회 ${numberFormat(item.viewCount)} / 댓글 ${numberFormat(item.commentCount)}</td>
                <td>
                    ${withMoreMenu(
                        `<button type="button" class="${item.deletedAt ? "primary-button" : "danger-button"}" data-post-action="${item.id}" data-post-mode="${item.deletedAt ? "restore" : "hide"}" data-post-title="${escapeHtml(item.title)}">${item.deletedAt ? "복구" : "숨김"}</button>`,
                        [
                            item.promoActive ? `<button type="button" data-promo-takedown="${item.id}" data-post-title="${escapeHtml(item.title)}" data-promo-until="${item.promoUntil || ""}" data-promo-refund="${item.promoRefundAmount || 0}">광고 내리기</button>` : "",
                            item.deletedAt && item.purgeEligible ? `<button type="button" class="danger-button" data-post-purge="${item.id}" data-post-title="${escapeHtml(item.title)}">영구 삭제</button>` : ""
                        ]
                    )}
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
    body.querySelectorAll("[data-promo-takedown]").forEach((button) => {
        button.addEventListener("click", () => openPromoTakedownModal(
            button.dataset.promoTakedown,
            button.dataset.postTitle,
            button.dataset.promoUntil,
            Number(button.dataset.promoRefund || 0)
        ));
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

/**
 * 홍보 글을 광고 자리에서 내린다.
 *
 * 글을 지우는 것과 다르다 — 노출 기간만 지금으로 끝내서 사이드바에서 빠지고,
 * 글은 목록과 홍보 탭에 그대로 남는다.
 *
 * 환불은 자동으로 정하지 않는다. 규칙 위반이면 돌려주지 않고, 우리 쪽 문제면
 * 돌려주는 게 맞는데 그 판단은 사람이 해야 한다. 고른 결과는 활동 로그에 남는다.
 */
function openPromoTakedownModal(postId, title, promoUntil, refundAmount) {
    openActionModal({
        title: "광고 노출 내리기",
        summary: `"${title}" 글을 광고 자리에서 내립니다. 글 자체는 목록에 그대로 남습니다.`
            + (promoUntil ? ` (원래 노출 만료: ${formatDate(promoUntil)})` : ""),
        confirmText: "내리기",
        reasonOptions: toReasonOptions(["SPAM", "ABUSE", "OFF_TOPIC", "POLICY", "DUPLICATE", "OTHER"]),
        extraHtml: `
            <label class="checkbox-row">
                <input type="checkbox" id="modalPromoRefund">
                <span>게시 비용 ${numberFormat(refundAmount)}P 를 환불합니다</span>
            </label>
            <p class="muted" style="margin:4px 0 0;">규칙 위반이면 환불하지 않고, 우리 쪽 문제라면 환불합니다.</p>
        `,
        getExtraPayload: () => ({ refund: document.getElementById("modalPromoRefund")?.checked === true }),
        submit: async (payload) => {
            await apiRequest(`/admin/api/posts/${postId}/promo-takedown`, {
                method: "POST",
                body: JSON.stringify(payload)
            });
            await loadPosts();
            await refreshDashboardSilently();
            return payload.refund
                ? `광고를 내리고 ${numberFormat(refundAmount)}P를 환불했습니다.`
                : "광고를 내렸습니다. (환불 없음)";
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
                    ${withMoreMenu(
                        `<button type="button" class="${item.deletedAt ? "primary-button" : "danger-button"}" data-comment-action="${item.id}" data-comment-mode="${item.deletedAt ? "restore" : "hide"}">${item.deletedAt ? "복구" : "숨김"}</button>`,
                        [item.deletedAt && item.purgeEligible ? `<button type="button" class="danger-button" data-comment-purge="${item.id}">영구 삭제</button>` : ""]
                    )}
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
    // 대시보드 큐에서 조치하면 이 함수도 불린다. 신고 화면이 아니면 부를 일이 없다.
    if (!document.getElementById("reportsTableBody")) {
        return;
    }
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

    // 조치 버튼이 대상·작성자 정보를 다시 물어보지 않아도 되게 이번 화면 분을 들고 있는다.
    // 데이터 예닐곱 개를 data- 속성으로 흩뿌리는 것보다 낫다.
    adminState.reportsById = {};
    data.items.forEach((item) => { adminState.reportsById[item.id] = item; });

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
                        ${item.targetAuthorName ? `<span class="muted">작성자 ${escapeHtml(item.targetAuthorName)}</span>` : ""}
                        <div class="badge-row">
                            ${item.openCountForTarget > 1 ? `<span class="open-count-badge">신고 ${item.openCountForTarget}건</span>` : ""}
                            ${item.targetHidden ? `<span class="badge deleted">숨김됨</span>` : ""}
                            ${item.targetAuthorSuspended ? `<span class="badge danger">작성자 정지됨</span>` : ""}
                        </div>
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
                    ${withMoreMenu(
                        item.status !== "RESOLVED"
                            ? `<button type="button" class="primary-button" data-report-status="${item.id}" data-next-status="RESOLVED" data-target-summary="${escapeHtml(item.targetSummary)}">해결</button>`
                            : `<span class="muted">처리됨</span>`,
                        [
                            `<button type="button" class="tiny-button" data-report-target="${item.id}">${item.targetHidden ? "글 복구" : "글 숨김"}</button>`,
                            item.targetAuthorId ? `<button type="button" class="tiny-button" data-report-author="${item.id}">${item.targetAuthorSuspended ? "작성자 정지 해제" : "작성자 정지"}</button>` : "",
                            item.status !== "REVIEWING" ? `<button type="button" class="tiny-button" data-report-status="${item.id}" data-next-status="REVIEWING" data-target-summary="${escapeHtml(item.targetSummary)}">검토 중으로</button>` : "",
                            item.status !== "REJECTED" ? `<button type="button" class="danger-button" data-report-status="${item.id}" data-next-status="REJECTED" data-target-summary="${escapeHtml(item.targetSummary)}">반려</button>` : ""
                        ]
                    )}
                </td>
            </tr>
        `).join("");
    }

    body.querySelectorAll("[data-report-status]").forEach((button) => {
        button.addEventListener("click", () => openReportStatusModal(button.dataset.reportStatus, button.dataset.nextStatus, button.dataset.targetSummary));
    });
    body.querySelectorAll("[data-report-target]").forEach((button) => {
        button.addEventListener("click", () => openReportTargetModal(adminState.reportsById[button.dataset.reportTarget]));
    });
    body.querySelectorAll("[data-report-author]").forEach((button) => {
        button.addEventListener("click", () => openReportAuthorModal(adminState.reportsById[button.dataset.reportAuthor]));
    });

    renderPagination("reportsPagination", data, (page) => {
        adminState.reportsPage = page;
        loadReports().catch((error) => showAlert(error.message, "error"));
    });
}

/**
 * 신고된 글·댓글을 신고 목록에서 바로 숨기거나 되돌린다.
 *
 * 예전에는 한 건을 처리하려고 신고 탭에서 대상을 확인하고 → 게시글 탭으로 가서
 * 찾아 숨기고 → 사용자 탭에서 작성자를 정지시키고 → 다시 신고 탭으로 돌아와
 * 해결을 눌러야 했다. 화면을 네 번 옮기던 일을 이 자리에서 끝낸다.
 */
function openReportTargetModal(item) {
    if (!item) {
        return;
    }
    const isPost = item.targetType === "POST";
    const isRestore = item.targetHidden;
    const mode = isRestore ? "restore" : "hide";
    const what = isPost ? "게시글" : "댓글";

    openActionModal({
        title: `${what} ${isRestore ? "복구" : "숨김"}`,
        summary: `"${item.targetSummary}" ${what}을 ${isRestore ? "복구" : "숨김"}합니다.`,
        confirmText: isRestore ? "복구" : "숨김",
        reasonOptions: toReasonOptions(isRestore
            ? ["MANUAL_RESTORE", "REPORT_POLICY", "POLICY", "OTHER"]
            : ["SPAM", "ABUSE", "OFF_TOPIC", "POLICY", "DUPLICATE", "REPORT_POLICY", "OTHER"]),
        submit: async (reasonPayload) => {
            await apiRequest(`/admin/api/${isPost ? "posts" : "comments"}/${item.targetId}/${mode}`, {
                method: "POST",
                body: JSON.stringify(reasonPayload)
            });
            await loadReports();
            await refreshDashboardSilently();
            return `${what}이 ${isRestore ? "복구" : "숨김 처리"}되었습니다.`;
        }
    });
}

/** 신고된 글의 작성자를 신고 목록에서 바로 정지시키거나 푼다. */
function openReportAuthorModal(item) {
    if (!item || !item.targetAuthorId) {
        return;
    }
    const nextStatus = item.targetAuthorSuspended ? "ACTIVE" : "SUSPENDED";

    openActionModal({
        title: "작성자 계정 상태 변경",
        summary: `${item.targetAuthorName} 사용자를 ${labelForAccountStatus(nextStatus)} 상태로 변경합니다.`,
        confirmText: item.targetAuthorSuspended ? "정지 해제" : "계정 정지",
        reasonOptions: toReasonOptions(nextStatus === "SUSPENDED"
            ? ["SUSPENSION_POLICY", "SPAM", "ABUSE", "POLICY", "REPORT_POLICY", "OTHER"]
            : ["MANUAL_RESTORE", "SUSPENSION_POLICY", "REPORT_POLICY", "OTHER"]),
        getExtraPayload: () => ({ accountStatus: nextStatus }),
        submit: async (reasonPayload) => {
            const response = await apiRequest(`/admin/api/users/${item.targetAuthorId}/account-status`, {
                method: "PATCH",
                body: JSON.stringify(reasonPayload)
            });
            await loadReports();
            await refreshDashboardSilently();
            return `계정 상태가 ${labelForAccountStatus(response.accountStatus)}로 변경되었습니다.`;
        }
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
    // 남긴 스냅샷이 없는 조치도 있다. 그때 펼침 버튼을 그려 두면 눌러야 빈 것을 알게 된다.
    if (!snapshotJson) {
        return "";
    }
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
