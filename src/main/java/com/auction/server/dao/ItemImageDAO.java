package com.auction.server.dao;

import com.auction.server.config.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ItemImageDAO – lưu và đọc ảnh sản phẩm từ DB.
 *
 * Mỗi ảnh được lưu 2 phiên bản:
 *   thumbnail  (~200x150) – dùng cho Dashboard card và Admin duyệt
 *   full_image (~800x600) – dùng cho gallery trong AuctionRoom
 *
 * Lưu dưới dạng Base64 MEDIUMTEXT → JavaFX Image đọc qua "data:image/jpeg;base64,..."
 */
public class ItemImageDAO {

  private static final Logger logger = LoggerFactory.getLogger(ItemImageDAO.class);

  private static final int   THUMB_W      = 200;
  private static final int   THUMB_H      = 150;
  private static final int   FULL_W       = 800;
  private static final int   FULL_H       = 600;
  private static final float JPEG_QUALITY = 0.82f;

  private Connection getConn() throws SQLException {
    return DatabaseConnection.getConnection();
  }

  // ── SAVE ──────────────────────────────────────────────────────────────────

  /**
   * Lưu danh sách ảnh Base64 cho một auction.
   * Mỗi ảnh được resize thành thumbnail + full trước khi lưu.
   */
  public void saveImages(int auctionId, List<String> imagesB64) {
    if (imagesB64 == null || imagesB64.isEmpty()) return;

    String sql = "INSERT INTO item_images (auction_id, sort_order, thumbnail, full_image) VALUES (?, ?, ?, ?)";

    try (Connection c = getConn();
         PreparedStatement ps = c.prepareStatement(sql)) {

      for (int i = 0; i < imagesB64.size(); i++) {
        String b64 = imagesB64.get(i);
        if (b64 == null || b64.isBlank()) continue;

        try {
          byte[] originalBytes = Base64.getDecoder().decode(b64);
          String thumbB64 = resizeToBase64(originalBytes, THUMB_W, THUMB_H);
          String fullB64  = resizeToBase64(originalBytes, FULL_W,  FULL_H);

          ps.setInt(1, auctionId);
          ps.setInt(2, i);
          ps.setString(3, thumbB64);
          ps.setString(4, fullB64);
          ps.addBatch();

        } catch (Exception e) {
          logger.warn("Bỏ qua ảnh [{}] auction {}: {}", i, auctionId, e.getMessage());
        }
      }

      int[] results = ps.executeBatch();
      logger.info("Đã lưu {}/{} ảnh cho auction {}", results.length, imagesB64.size(), auctionId);

    } catch (SQLException e) {
      logger.error("Lỗi lưu ảnh auction {}: {}", auctionId, e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  // ── READ ──────────────────────────────────────────────────────────────────

  /**
   * Lấy data URI thumbnail của ảnh đại diện (sort_order = 0).
   * Dùng cho Dashboard card và màn hình Admin duyệt.
   */
  public String getThumbnailUrl(int auctionId) {
    String sql = "SELECT thumbnail FROM item_images WHERE auction_id = ? AND sort_order = 0 LIMIT 1";
    try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return toDataUri(rs.getString("thumbnail"));
      }
    } catch (SQLException e) {
      logger.error("Lỗi getThumbnailUrl auction {}: {}", auctionId, e.getMessage());
    }
    return null;
  }

  public List<String> getThumbnailUrls(int auctionId) {
    String sql = "SELECT thumbnail FROM item_images WHERE auction_id = ? ORDER BY sort_order ASC";
    List<String> urls = new ArrayList<>();
    try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) urls.add(toDataUri(rs.getString("thumbnail")));
      }
    } catch (SQLException e) {
      logger.error("Lỗi getThumbnailUrls auction {}: {}", auctionId, e.getMessage());
    }
    return urls;
  }

  /**
   * Lấy danh sách data URI ảnh full size theo thứ tự sort_order.
   * Dùng cho gallery trong AuctionRoom.
   */
  public List<String> getFullImageUrls(int auctionId) {
    String sql = "SELECT full_image FROM item_images WHERE auction_id = ? ORDER BY sort_order ASC";
    List<String> urls = new ArrayList<>();
    try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) urls.add(toDataUri(rs.getString("full_image")));
      }
    } catch (SQLException e) {
      logger.error("Lỗi getFullImageUrls auction {}: {}", auctionId, e.getMessage());
    }
    return urls;
  }

  /**
   * Kiểm tra auction đã có ảnh chưa.
   */
  public boolean hasImages(int auctionId) {
    String sql = "SELECT COUNT(*) FROM item_images WHERE auction_id = ?";
    try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, auctionId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt(1) > 0;
      }
    } catch (SQLException e) {
      logger.error("Lỗi hasImages auction {}: {}", auctionId, e.getMessage());
    }
    return false;
  }

  // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

  /**
   * Resize ảnh về tối đa maxW x maxH (giữ tỉ lệ), encode sang Base64 JPEG.
   * Nếu ảnh đã nhỏ hơn target thì không phóng to.
   */
  private String resizeToBase64(byte[] originalBytes, int maxW, int maxH) throws Exception {
    BufferedImage original = javax.imageio.ImageIO.read(new ByteArrayInputStream(originalBytes));
    if (original == null) throw new IllegalArgumentException("Không đọc được ảnh");

    int origW = original.getWidth();
    int origH = original.getHeight();
    double scale = Math.min((double) maxW / origW, (double) maxH / origH);

    // Không phóng to ảnh nhỏ
    if (scale >= 1.0) {
      return Base64.getEncoder().encodeToString(compressToJpeg(original));
    }

    int newW = (int) (origW * scale);
    int newH = (int) (origH * scale);

    BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = resized.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE); // fill trắng tránh nền đen với PNG có alpha
    g.fillRect(0, 0, newW, newH);
    g.drawImage(original, 0, 0, newW, newH, null);
    g.dispose();

    return Base64.getEncoder().encodeToString(compressToJpeg(resized));
  }

  private byte[] compressToJpeg(BufferedImage img) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
    javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(JPEG_QUALITY);
    javax.imageio.stream.ImageOutputStream ios =
        javax.imageio.ImageIO.createImageOutputStream(baos);    writer.setOutput(ios);
    writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
    writer.dispose();
    ios.close();
    return baos.toByteArray();
  }

  private String toDataUri(String base64) {
    if (base64 == null || base64.isBlank()) return null;
    return "data:image/jpeg;base64," + base64;
  }
}