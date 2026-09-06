package com.example.demo.admin.entity;

public enum AdminActionType {
    POST_HIDE,
    POST_RESTORE,
    POST_PURGE,
    /** 홍보 글을 광고 자리에서 내린다. 글 자체는 목록에 남는다. */
    PROMO_TAKEDOWN,
    COMMENT_HIDE,
    COMMENT_RESTORE,
    COMMENT_PURGE,
    USER_ROLE_CHANGE,
    USER_POINT_ADJUST,
    USER_SUSPEND,
    USER_ACTIVATE,
    CATEGORY_CREATE,
    CATEGORY_UPDATE,
    CATEGORY_REORDER,
    REPORT_REVIEWING,
    REPORT_RESOLVE,
    REPORT_REJECT
}
