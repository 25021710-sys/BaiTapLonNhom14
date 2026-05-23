package com.auction.client.util;

import javafx.scene.image.Image;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Tiện ích load ảnh từ data URI hoặc URL thông thường.
 * JavaFX Image không đọc được "data:image/jpeg;base64,..." trực tiếp.
 */
public class ImageUtil {

  /**
   * Load Image từ bất kỳ URL nào:
   *   - "data:image/jpeg;base64,..."  → decode Base64 → ByteArrayInputStream
   *   - "http://...", "https://..."   → load bình thường
   *   - "file:///..."                 → load bình thường
   *
   * @return Image nếu thành công, null nếu lỗi
   */
  public static Image loadImage(String url) {
    if (url == null || url.isBlank()) return null;

    try {
      if (url.startsWith("data:")) {
        // Tách phần base64 sau dấu phẩy
        int commaIdx = url.indexOf(',');
        if (commaIdx < 0) return null;
        String b64 = url.substring(commaIdx + 1);
        byte[] bytes = Base64.getDecoder().decode(b64);
        return new Image(new ByteArrayInputStream(bytes));
      } else {
        return new Image(url, true); // background loading cho URL thông thường
      }
    } catch (Exception e) {
      System.err.println("[ImageUtil] Không load được ảnh: " + e.getMessage());
      return null;
    }
  }

  /**
   * Load thumbnail nhỏ với kích thước cụ thể.
   * Dùng cho Dashboard card.
   */
  public static Image loadThumbnail(String url, double width, double height) {
    if (url == null || url.isBlank()) return null;

    try {
      if (url.startsWith("data:")) {
        int commaIdx = url.indexOf(',');
        if (commaIdx < 0) return null;
        String b64 = url.substring(commaIdx + 1);
        byte[] bytes = Base64.getDecoder().decode(b64);
        return new Image(new ByteArrayInputStream(bytes),
            width, height, true, true);
      } else {
        return new Image(url, width, height, true, true, true);
      }
    } catch (Exception e) {
      System.err.println("[ImageUtil] Không load thumbnail: " + e.getMessage());
      return null;
    }
  }
}