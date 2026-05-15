package com.auction.common.response;

import com.auction.common.dto.AdminRoomDTO;

import java.io.Serializable;

/**
 * Response trả về chi tiết một phòng đấu giá (kèm participants + bid log gần nhất).
 * Được gửi khi Admin chọn một phòng cụ thể trong bảng.
 */
public class AdminRoomDetailResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private AdminRoomDTO room; // Có đầy đủ participantUsernames + recentBidLogs

    public AdminRoomDetailResponse() {}

    public AdminRoomDetailResponse(boolean success, String message, AdminRoomDTO room) {
        this.success = success;
        this.message = message;
        this.room = room;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public AdminRoomDTO getRoom() { return room; }
    public void setRoom(AdminRoomDTO room) { this.room = room; }
}