package com.auction.server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho lớp Auction – kiểm tra logic nghiệp vụ cốt lõi
 * (không phụ thuộc DB hay network).
 */
@DisplayName("Auction Model Tests")
class AuctionTest {

    private Auction auction;

    @BeforeEach
    void setUp() {
        auction = new Auction(
                1,   // itemId
                2,   // sellerId
                new BigDecimal("1000000"),  // startingPrice
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1)
        );
        auction.setId(10);
        auction.setCurrentPrice(new BigDecimal("1000000"));
        auction.setStatus(AuctionStatus.OPEN);
        auction.setReservePrice(new BigDecimal("2000000"));
    }

    @Test
    @DisplayName("Trạng thái ban đầu phải là OPEN")
    void testInitialStatus() {
        Auction a = new Auction(1, 2, new BigDecimal("500000"),
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        assertEquals(AuctionStatus.OPEN, a.getStatus());
    }

    @Test
    @DisplayName("setCurrentPrice cập nhật đúng giá hiện tại")
    void testSetCurrentPrice() {
        BigDecimal newPrice = new BigDecimal("1500000");
        auction.setCurrentPrice(newPrice);
        assertEquals(newPrice, auction.getCurrentPrice());
    }

    @Test
    @DisplayName("Chuyển trạng thái OPEN → RUNNING thành công")
    void testStatusTransitionOpenToRunning() {
        auction.setStatus(AuctionStatus.RUNNING);
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    @DisplayName("Chuyển trạng thái RUNNING → FINISHED thành công")
    void testStatusTransitionRunningToFinished() {
        auction.setStatus(AuctionStatus.RUNNING);
        auction.setStatus(AuctionStatus.FINISHED);
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("setHighestBidderId cập nhật đúng người dẫn đầu")
    void testSetHighestBidderId() {
        auction.setHighestBidderId(5);
        assertEquals(5, auction.getHighestBidderId());
    }

    @Test
    @DisplayName("extensionCount tăng đúng khi gia hạn")
    void testExtensionCount() {
        assertEquals(0, auction.getExtensionCount());
        auction.setExtensionCount(auction.getExtensionCount() + 1);
        assertEquals(1, auction.getExtensionCount());
        auction.setExtensionCount(auction.getExtensionCount() + 1);
        assertEquals(2, auction.getExtensionCount());
    }

    @Test
    @DisplayName("printInfo không ném exception")
    void testPrintInfoDoesNotThrow() {
        assertDoesNotThrow(() -> auction.printInfo());
    }

    @Test
    @DisplayName("getMinBidIncrement trả về giá trị mặc định nếu chưa set")
    void testGetMinBidIncrementDefault() {
        Auction a = new Auction(1, 2, new BigDecimal("1000"),
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        assertNotNull(a.getMinBidIncrement());
        assertTrue(a.getMinBidIncrement().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Thời gian kết thúc phải sau thời gian bắt đầu")
    void testEndTimeAfterStartTime() {
        assertTrue(auction.getEndTime().isAfter(auction.getStartTime()));
    }

    @Test
    @DisplayName("reservePrice được set đúng")
    void testReservePriceSet() {
        assertEquals(new BigDecimal("2000000"), auction.getReservePrice());
    }

    @Test
    @DisplayName("currentPrice < reservePrice → chưa đạt giá sàn")
    void testCurrentPriceBelowReserve() {
        auction.setCurrentPrice(new BigDecimal("1500000"));
        assertTrue(auction.getCurrentPrice().compareTo(auction.getReservePrice()) < 0);
    }

    @Test
    @DisplayName("currentPrice >= reservePrice → đạt giá sàn")
    void testCurrentPriceMeetsReserve() {
        auction.setCurrentPrice(new BigDecimal("2000000"));
        assertTrue(auction.getCurrentPrice().compareTo(auction.getReservePrice()) >= 0);
    }

    @Test
    @DisplayName("sellerId được lưu đúng")
    void testSellerIdStored() {
        assertEquals(2, auction.getSellerId());
    }

    @Test
    @DisplayName("Phiên PENDING không thể đặt giá")
    void testPendingStatusIsNotBiddable() {
        auction.setStatus(AuctionStatus.PENDING);
        assertNotEquals(AuctionStatus.OPEN, auction.getStatus());
        assertNotEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    @DisplayName("Phiên CANCELED không thể đặt giá")
    void testCanceledStatusIsNotBiddable() {
        auction.setStatus(AuctionStatus.CANCELED);
        assertNotEquals(AuctionStatus.OPEN, auction.getStatus());
        assertNotEquals(AuctionStatus.RUNNING, auction.getStatus());
    }
}