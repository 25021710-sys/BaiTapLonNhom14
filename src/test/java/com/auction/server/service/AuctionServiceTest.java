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

@DisplayName("AuctionService Validation Tests")
class AuctionServiceTest {

    private AuctionService auctionService;
    private ConcurrentHashMap<Integer, Auction> mockCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        AuctionDAO auctionDAO = new AuctionDAO();
        BidDAO bidDAO = new BidDAO();
        auctionService = new AuctionService(auctionDAO, bidDAO);

        // test toàn bộ Logic mà không cần bật Database MySQL
        Field cacheField = AuctionService.class.getDeclaredField("auctionCache");
        cacheField.setAccessible(true);
        mockCache = (ConcurrentHashMap<Integer, Auction>) cacheField.get(auctionService);
    }

    @Test
    @DisplayName("Từ chối số tiền âm hoặc bằng 0")
    void testPlaceBidFailsWhenAmountIsInvalid() {
        BidRequest req = new BidRequest(10, "1", new BigDecimal("-100"));
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

    @Test
    @DisplayName("Chặn người bán tự mua đồ của chính mình")
    void testPlaceBidFailsWhenSellerBidsOnOwnItem() {
        // Giả lập phiên đấu giá trong RAM: Người bán có ID = 5
        Auction a = new Auction();
        a.setId(1);
        a.setSellerId(5);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusMinutes(10));
        a.setEndTime(LocalDateTime.now().plusMinutes(10));
        mockCache.put(1, a);

        BidRequest req = new BidRequest(5, "1", new BigDecimal("200000"));
        BidResponse res = auctionService.placeBid(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Người bán không thể tự đấu giá"));
    }

    @Test
    @DisplayName("Từ chối khi mức giá đặt <= giá hiện tại")
    void testPlaceBidFailsWhenPriceTooLow() {
        // Giả lập phiên đấu giá: Giá hiện tại đang là 500k
        Auction a = new Auction();
        a.setId(2);
        a.setSellerId(1);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusMinutes(10));
        a.setEndTime(LocalDateTime.now().plusMinutes(10));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(2, a);

        BidRequest req = new BidRequest(10, "2", new BigDecimal("400000"));
        BidResponse res = auctionService.placeBid(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Mức giá đề xuất phải cao hơn"));
    }

    @Test
    @DisplayName("Ngoại lệ 5: Từ chối khi phiên đấu giá đã kết thúc")
    void testPlaceBidFailsWhenAuctionEnded() {
        // Giả lập phiên đấu giá đã hết hạn từ ngày hôm qua
        Auction a = new Auction();
        a.setId(3);
        a.setSellerId(1);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusDays(2));
        a.setEndTime(LocalDateTime.now().minusDays(1)); // Đã hết hạn
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(3, a);

        BidRequest req = new BidRequest(10, "3", new BigDecimal("600000"));
        BidResponse res = auctionService.placeBid(req);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Phiên đấu giá đã kết thúc"));
    }
}