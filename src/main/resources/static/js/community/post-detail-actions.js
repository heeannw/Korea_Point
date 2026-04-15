window.__reportTarget = null;

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
        throw new Error(payload?.message || "요청을 처리하지 못했습니다.");
    }
    return payload;
}

function reportPost(button) {
    window.__reportTarget = {
        type: "POST",
        id: button.getAttribute("data-post-id")
    };
    document.getElementById("reportModal").classList.remove("hidden");
    resetReportModalState();
}

function reportComment(button) {
    document.querySelectorAll(".comment-menu").forEach((menu) => menu.classList.add("hidden"));
    const commentId = button.getAttribute("data-comment-id");
    window.__reportTarget = {
        type: "COMMENT",
        id: commentId
    };
    document.getElementById("reportModal").classList.remove("hidden");
    resetReportModalState();
}

function resetReportModalState() {
    document.querySelectorAll('#reportModal input[type="radio"]').forEach((radio) => {
        radio.checked = false;
    });
    const submitButton = document.getElementById("reportSubmitBtn");
    submitButton.disabled = true;
    submitButton.classList.add("opacity-40", "cursor-not-allowed");
    submitButton.classList.remove("cursor-pointer", "hover:bg-gray-700");
}

async function submitReport() {
    const reason = document.querySelector('input[name="reportReason"]:checked')?.value;
    if (!reason) {
        showAlert("신고 사유를 선택해 주세요.");
        return;
    }
    if (!window.__reportTarget?.id) {
        showAlert("신고 대상을 다시 선택해 주세요.");
        return;
    }

    const path = window.__reportTarget.type === "POST"
        ? `/api/reports/posts/${window.__reportTarget.id}`
        : `/api/reports/comments/${window.__reportTarget.id}`;

    try {
        await requestJson(path, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ reasonCode: reason })
        });
        closeReportModal();
        showAlert("신고가 접수되었습니다.\n검토 후 조치하겠습니다.");
    } catch (error) {
        showAlert(error.message);
    }
}

function closeReportModal() {
    document.getElementById("reportModal").classList.add("hidden");
}

async function submitTopComment(button) {
    const postId = button.getAttribute("data-post-id");
    const input = document.getElementById("commentInput");
    const content = input.value.trim();
    if (!content) {
        showAlert("댓글 내용을 입력해 주세요.");
        return;
    }

    try {
        await requestJson(`/api/comments/${postId}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ content, parentId: null })
        });
        location.reload();
    } catch (error) {
        showAlert(error.message);
    }
}

async function submitComment(button) {
    const postId = button.getAttribute("data-post-id");
    const parentId = button.getAttribute("data-parent-id");
    const form = button.closest(".reply-form");
    const input = form.querySelector("input");
    const content = input.value.trim();
    if (!content) {
        showAlert("댓글 내용을 입력해 주세요.");
        return;
    }

    try {
        await requestJson(`/api/comments/${postId}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                content,
                parentId: Number(parentId),
                replyToId: form.dataset.replyToId ? Number(form.dataset.replyToId) : null
            })
        });
        location.reload();
    } catch (error) {
        showAlert(error.message);
    }
}

async function saveEdit(button) {
    const editForm = button.closest(".edit-form");
    const textarea = editForm.querySelector("textarea");
    const commentId = textarea.getAttribute("data-comment-id");
    const content = textarea.value.trim();
    if (!content) {
        showAlert("댓글 내용을 입력해 주세요.");
        return;
    }

    try {
        await requestJson(`/api/comments/${commentId}`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ content })
        });
        location.reload();
    } catch (error) {
        showAlert(error.message);
    }
}

function deleteComment(button) {
    const commentId = button.getAttribute("data-comment-id");
    showConfirm("댓글을 삭제하시겠습니까?", async () => {
        try {
            await requestJson(`/api/comments/${commentId}`, { method: "DELETE" });
            location.reload();
        } catch (error) {
            showAlert(error.message);
        }
    });
}
