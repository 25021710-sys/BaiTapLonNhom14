package com.auction.common.response;

import com.auction.common.dto.AdminRoomDTO;

import java.io.Serializable;
import java.util.List;

/**
 * Response trả về danh sách phòng đấu giá cho màn hình Admin Room Monitoring.
 */
public class AdminGetRoomsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private List<AdminRoomDTO> rooms;

    public AdminGetRoomsResponse() {}

    public AdminGetRoomsResponse(boolean success, String message, List<AdminRoomDTO> rooms) {
        this.success = success;
        this.message = message;
        this.rooms = rooms;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<AdminRoomDTO> getRooms() { return rooms; }
    public void setRooms(List<AdminRoomDTO> rooms) { this.rooms = rooms; }
}
