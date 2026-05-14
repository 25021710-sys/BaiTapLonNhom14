package com.auction.common.response;

import com.auction.common.dto.UserDTO;
import java.io.Serializable;

public class GetUserProfileResponse implements Serializable {
  private static final long serialVersionUID = 1L;

  private boolean success;
  private String message;
  private UserDTO user;
  private int itemCount;

  public GetUserProfileResponse(boolean success, String message, UserDTO user, int itemCount) {
    this.success   = success;
    this.message   = message;
    this.user      = user;
    this.itemCount = itemCount;
  }

  public boolean isSuccess()  { return success;    }
  public String getMessage()  { return message;    }
  public UserDTO getUser()    { return user;       }
  public int getItemCount()   { return itemCount;  }
}