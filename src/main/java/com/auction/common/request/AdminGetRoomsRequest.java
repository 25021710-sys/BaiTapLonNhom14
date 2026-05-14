package com.auction.common.request;

import java.io.Serializable;

/**
 * Request để Admin lấy danh sách tất cả phòng đấu giá đang hoạt động
 * (OPEN + RUNNING), kèm thông tin realtime (giá hiện tại, số bid, thời gian còn lại).
 */
public class AdminGetRoomsRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Lọc theo trạng thái. Null = lấy tất cả OPEN + RUNNING. */
    private String statusFilter;

    /** Từ khoá tìm kiếm (tên item hoặc roomId). Null = không lọc. */
    private String keyword;

    public AdminGetRoomsRequest() {}

    public AdminGetRoomsRequest(String statusFilter, String keyword) {
        this.statusFilter = statusFilter;
        this.keyword = keyword;
    }

    public String getStatusFilter() { return statusFilter; }
    public void setStatusFilter(String statusFilter) { this.statusFilter = statusFilter; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}