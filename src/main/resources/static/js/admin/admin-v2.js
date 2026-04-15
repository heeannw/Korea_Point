Object.assign(REASON_LABELS, {
    SUSPENSION_POLICY: "정지 정책",
    REPORT_POLICY: "신고 처리 정책",
    PURGE_POLICY: "영구 삭제 정책"
});

Object.assign(TARGET_TYPE_LABELS, {
    REPORT: "신고"
});

Object.assign(ACTION_TYPE_LABELS, {
    POST_PURGE: "게시글 영구 삭제",
    COMMENT_PURGE: "댓글 영구 삭제",
    USER_SUSPEND: "사용자 정지",
    USER_ACTIVATE: "사용자 활성화",
    CATEGORY_ACTIVATE: "카테고리 활성화",
    CATEGORY_DEACTIVATE: "카테고리 비활성화",
    REPORT_REVIEWING: "신고 검토 중",
    REPORT_RESOLVE: "신고 해결",
    REPORT_REJECT: "신고 반려"
});

syncLabelOptions(REASON_OPTIONS, REASON_LABELS);
syncLabelOptions(TARGET_TYPE_OPTIONS, TARGET_TYPE_LABELS);
syncLabelOptions(ACTION_TYPE_OPTIONS, ACTION_TYPE_LABELS);
syncLabelOptions(ACCOUNT_STATUS_OPTIONS, ACCOUNT_STATUS_LABELS);
syncLabelOptions(REPORT_STATUS_OPTIONS, REPORT_STATUS_LABELS);
syncLabelOptions(REPORT_REASON_OPTIONS, REPORT_REASON_LABELS);
