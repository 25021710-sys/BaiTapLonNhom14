package com.auction.server.service;

import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.model.Auction;
import com.auction.server.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DisplayName("AuctionService Validation Tests")
class AuctionServiceTest {

    private AuctionService auctionService;
    private ConcurrentHashMap<Integer, Auction> mockCache;

    // Helper tạo phiên hợp lệ đang RUNNING
    private Auction runningAuction(int id, int sellerId, BigDecimal currentPrice) {
        Auction a = new Auction();
        a.setId(id);
        a.setSellerId(sellerId);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusMinutes(10));
        a.setEndTime(LocalDateTime.now().plusMinutes(30));
        a.setCurrentPrice(currentPrice);
        return a;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        AuctionDAO auctionDAO = mock(AuctionDAO.class);
        BidDAO bidDAO = mock(BidDAO.class);
        // Giả lập findById trả null (auction không tồn tại) cho mọi id
        when(auctionDAO.findById(anyInt())).thenReturn(null);

        auctionService = new AuctionService(auctionDAO, bidDAO);

        Field cacheField = AuctionService.class.getDeclaredField("auctionCache");
        cacheField.setAccessible(true);
        mockCache = (ConcurrentHashMap<Integer, Auction>) cacheField.get(auctionService);
    }

    // 1) đầu vào cơ bản
    @Test
    @DisplayName("Từ chối số tiền âm")
    void testPlaceBid_NegativeAmount_ShouldFail() {
        BidRequest req = new BidRequest(10, "1", new BigDecimal("-100"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertEquals("Giao dịch bị từ chối: Số tiền đặt giá không hợp lệ.", res.getMessage());
    }

    @Test
    @DisplayName("Từ chối số tiền bằng 0")
    void testPlaceBid_ZeroAmount_ShouldFail() {
        BidRequest req = new BidRequest(10, "1", BigDecimal.ZERO);
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertEquals("Giao dịch bị từ chối: Số tiền đặt giá không hợp lệ.", res.getMessage());
    }

    @Test
    @DisplayName("Báo lỗi khi phiên đấu giá không tồn tại")
    void testPlaceBidFailsWhenAuctionNotFound() {
        BidRequest req = new BidRequest(10, "999", new BigDecimal("500000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Phiên đấu giá không tồn tại"));
    }

    // 2) trạng thái và thời gian phiên
    @Test
    @DisplayName("Từ chối khi phiên đã kết thúc (endTime đã qua)")
    void testPlaceBidFailsWhenAuctionEnded() {
        Auction a = new Auction();
        a.setId(3);
        a.setSellerId(1);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusDays(2));
        a.setEndTime(LocalDateTime.now().minusDays(1));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(3, a);

        BidRequest req = new BidRequest(10, "3", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("đã kết thúc"));
    }

    @Test
    @DisplayName("Từ chối khi phiên chưa bắt đầu")
    void testPlaceBid_AuctionNotStartedYet_ShouldFail() {
        Auction a = new Auction();
        a.setId(4);
        a.setSellerId(1);
        a.setStatus(AuctionStatus.OPEN);
        a.setStartTime(LocalDateTime.now().plusHours(2));
        a.setEndTime(LocalDateTime.now().plusHours(5));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(4, a);

        BidRequest req = new BidRequest(10, "4", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("chưa bắt đầu"));
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái FINISHED")
    void testPlaceBid_StatusFinished_ShouldFail() {
        Auction a = runningAuction(5, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.FINISHED);
        mockCache.put(5, a);

        BidRequest req = new BidRequest(10, "5", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái CANCELED")
    void testPlaceBid_StatusCanceled_ShouldFail() {
        Auction a = runningAuction(6, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.CANCELED);
        mockCache.put(6, a);

        BidRequest req = new BidRequest(10, "6", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái PENDING")
    void testPlaceBid_StatusPending_ShouldFail() {
        Auction a = runningAuction(7, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.PENDING);
        mockCache.put(7, a);

        BidRequest req = new BidRequest(10, "7", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
    }

    // 3) nghiệp vụ đặt giá
    @Test
    @DisplayName("Chặn người bán tự đấu giá sản phẩm của mình")
    void testPlaceBidFailsWhenSellerBidsOnOwnItem() {
        Auction a = runningAuction(1, 5, new BigDecimal("500000"));
        mockCache.put(1, a);

        BidRequest req = new BidRequest(5, "1", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Người bán không thể tự đấu giá"));
    }

    @Test
    @DisplayName("Từ chối khi giá đặt thấp hơn giá hiện tại")
    void testPlaceBidFailsWhenPriceTooLow() {
        Auction a = runningAuction(2, 1, new BigDecimal("500000"));
        mockCache.put(2, a);

        BidRequest req = new BidRequest(10, "2", new BigDecimal("400000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Mức giá đề xuất phải cao hơn"));
    }

    @Test
    @DisplayName("Từ chối khi giá đặt bằng giá hiện tại")
    void testPlaceBid_PriceEqualCurrent_ShouldFail() {
        Auction a = runningAuction(8, 1, new BigDecimal("500000"));
        mockCache.put(8, a);

        BidRequest req = new BidRequest(10, "8", new BigDecimal("500000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Mức giá đề xuất phải cao hơn"));
    }

    @Test
    @DisplayName("Từ chối khi người đang dẫn đầu tự nâng giá thêm")
    void testPlaceBid_SelfRaise_ShouldFail() {
        Auction a = runningAuction(11, 1, new BigDecimal("500000"));
        a.setHighestBidderId(10);
        mockCache.put(11, a);

        BidRequest req = new BidRequest(10, "11", new BigDecimal("700000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("đang dẫn đầu"));
    }

    // 4) createAuction validation
    @Test
    @DisplayName("createAuction: ném exception khi endTime trước startTime")
    void testCreateAuction_EndBeforeStart_ShouldThrow() {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        LocalDateTime end   = LocalDateTime.now().plusHours(1);
        assertThrows(Exception.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("500000"), null, start, end));
    }

    @Test
    @DisplayName("createAuction: ném exception khi startingPrice <= 0")
    void testCreateAuction_InvalidPrice_ShouldThrow() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end   = LocalDateTime.now().plusHours(2);
        assertThrows(Exception.class, () ->
                auctionService.createAuction(1, 2, BigDecimal.ZERO, null, start, end));
    }

    @Test
    @DisplayName("createAuction: ném exception khi startTime hoặc endTime null")
    void testCreateAuction_NullTime_ShouldThrow() {
        assertThrows(Exception.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("500000"), null, null, null));
    }
}