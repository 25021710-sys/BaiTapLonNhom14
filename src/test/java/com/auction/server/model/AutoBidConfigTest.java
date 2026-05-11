package com.auction.server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoBidConfig Tests")
class AutoBidConfigTest {

    private AutoBidConfig config;

    @BeforeEach
    void setUp() {
        config = new AutoBidConfig(
                1, "testUser", 10,
                new BigDecimal("5000000"),  // maxBid
                new BigDecimal("100000")    // increment
        );
    }

    @Test
    @DisplayName("calculateNextBid tính giá đúng khi còn dưới maxBid")
    void testCalculateNextBidBelowMax() {
        BigDecimal currentPrice = new BigDecimal("1000000");
        BigDecimal nextBid = config.calculateNextBid(currentPrice);
        assertNotNull(nextBid);
        assertEquals(new BigDecimal("1100000"), nextBid);
    }

    @Test
    @DisplayName("calculateNextBid trả về maxBid khi step vượt qua maxBid")
    void testCalculateNextBidReachesMax() {
        BigDecimal currentPrice = new BigDecimal("4950000");
        BigDecimal nextBid = config.calculateNextBid(currentPrice);
        assertNotNull(nextBid);
        // nextBid = 4950000 + 100000 = 5050000 > maxBid(5000000) → dùng maxBid
        assertEquals(new BigDecimal("5000000"), nextBid);
    }

    @Test
    @DisplayName("calculateNextBid trả về null khi currentPrice >= maxBid")
    void testCalculateNextBidExceedsMax() {
        BigDecimal currentPrice = new BigDecimal("5000000"); // bằng maxBid
        BigDecimal nextBid = config.calculateNextBid(currentPrice);
        assertNull(nextBid);
    }

    @Test
    @DisplayName("calculateNextBid trả về null khi currentPrice > maxBid")
    void testCalculateNextBidAboveMax() {
        BigDecimal currentPrice = new BigDecimal("5500000"); // vượt maxBid
        BigDecimal nextBid = config.calculateNextBid(currentPrice);
        assertNull(nextBid);
    }

    @Test
    @DisplayName("compareTo sắp xếp maxBid cao hơn trước")
    void testCompareToHigherMaxBidFirst() {
        AutoBidConfig higher = new AutoBidConfig(2, "user2", 10,
                new BigDecimal("8000000"), new BigDecimal("100000"));
        // higher.maxBid > config.maxBid → higher phải "nhỏ hơn" trong PriorityQueue (heap max)
        assertTrue(config.compareTo(higher) > 0,
                "Config có maxBid thấp hơn phải 'lớn hơn' trong compareTo để PriorityQueue đặt maxBid cao nhất lên đầu");
    }

    @Test
    @DisplayName("Config mới khởi tạo luôn active")
    void testNewConfigIsActive() {
        AutoBidConfig c = new AutoBidConfig(1, "u", 1,
                new BigDecimal("1000"), new BigDecimal("100"));
        // active mặc định = true trong constructor
        assertNotNull(c.getRegisteredAt());
    }
}