package com.auction.server.service;

import com.auction.common.request.BidRequest;
import com.auction.common.response.BidResponse;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.BidDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.model.*;
import com.auction.server.exception.AuctionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DisplayName("AuctionService Validation Tests")
class AuctionServiceTest {

    private AuctionService auctionService;
    private AuctionDAO mockAuctionDAO;
    private BidDAO mockBidDAO;
    private UserDAO mockUserDAO;
    private ConcurrentHashMap<Integer, Auction> mockCache;

    // Helper tạo user có số dư
    private User userWithBalance(int id, BigDecimal balance) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setBalance(balance);
        return u;
    }

    // Helper tạo phiên RUNNING hợp lệ
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

    // Helper tạo phiên sắp hết giờ (còn < 30 giây) để test anti-snipe
    private Auction almostExpiredAuction(int id, int sellerId, BigDecimal currentPrice) {
        Auction a = new Auction();
        a.setId(id);
        a.setSellerId(sellerId);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusHours(1));
        a.setEndTime(LocalDateTime.now().plusSeconds(15)); // còn 15 giây
        a.setCurrentPrice(currentPrice);
        return a;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockAuctionDAO = mock(AuctionDAO.class);
        mockBidDAO     = mock(BidDAO.class);
        mockUserDAO    = mock(UserDAO.class);

        when(mockAuctionDAO.findById(anyInt())).thenReturn(null);

        auctionService = new AuctionService(mockAuctionDAO, mockBidDAO);

        // Inject mock UserDAO qua reflection (field private final không inject qua constructor)
        Field userDAOField = AuctionService.class.getDeclaredField("userDAO");
        userDAOField.setAccessible(true);
        userDAOField.set(auctionService, mockUserDAO);

        // Lấy cache để dùng auction trực tiếp trong test
        Field cacheField = AuctionService.class.getDeclaredField("auctionCache");
        cacheField.setAccessible(true);
        mockCache = (ConcurrentHashMap<Integer, Auction>) cacheField.get(auctionService);
    }

    // 1) Validate đầu vào cơ bản

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
    @DisplayName("Từ chối khi amount là null")
    void testPlaceBid_NullAmount_ShouldFail() {
        BidRequest req = new BidRequest(10, "1", null);
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertEquals("Giao dịch bị từ chối: Số tiền đặt giá không hợp lệ.", res.getMessage());
    }

    // 2) Phiên không tồn tại

    @Test
    @DisplayName("Báo lỗi khi phiên không tồn tại trong cache lẫn DB")
    void testPlaceBidFailsWhenAuctionNotFound() {
        BidRequest req = new BidRequest(10, "999", new BigDecimal("500000"));
        BidResponse res = auctionService.placeBid(req);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Phiên đấu giá không tồn tại"));
    }

    @Test
    @DisplayName("Tìm phiên từ DB khi không có trong cache")
    void testPlaceBid_FallbackToDBWhenNotInCache() throws SQLException {
        Auction a = runningAuction(50, 1, new BigDecimal("500000"));
        when(mockAuctionDAO.findById(50)).thenReturn(a);
        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        BidRequest req = new BidRequest(10, "50", new BigDecimal("502000"));
        BidResponse res = auctionService.placeBid(req);
        assertTrue(res.isSuccess());
    }

    // 3) Trạng thái phiên

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái FINISHED")
    void testPlaceBid_StatusFinished_ShouldFail() {
        Auction a = runningAuction(5, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.FINISHED);
        mockCache.put(5, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "5", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Kết thúc"));
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái CANCELED")
    void testPlaceBid_StatusCanceled_ShouldFail() {
        Auction a = runningAuction(6, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.CANCELED);
        mockCache.put(6, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "6", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Đã hủy"));
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái PENDING")
    void testPlaceBid_StatusPending_ShouldFail() {
        Auction a = runningAuction(7, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.PENDING);
        mockCache.put(7, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "7", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Chờ duyệt"));
    }

    @Test
    @DisplayName("Từ chối khi phiên ở trạng thái PAUSED")
    void testPlaceBid_StatusPaused_ShouldFail() {
        Auction a = runningAuction(12, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.PAUSED);
        mockCache.put(12, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "12", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Đang tạm dừng"));
    }

    // 4) Kiểm tra thời gian phiên

    @Test
    @DisplayName("Từ chối khi phiên đã kết thúc (endTime đã qua)")
    void testPlaceBidFailsWhenAuctionEnded() {
        Auction a = new Auction();
        a.setId(3); a.setSellerId(1);
        a.setStatus(AuctionStatus.RUNNING);
        a.setStartTime(LocalDateTime.now().minusDays(2));
        a.setEndTime(LocalDateTime.now().minusDays(1));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(3, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "3", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("đã kết thúc"));
    }

    @Test
    @DisplayName("Từ chối khi phiên chưa bắt đầu (startTime chưa đến)")
    void testPlaceBid_AuctionNotStartedYet_ShouldFail() {
        Auction a = new Auction();
        a.setId(4); a.setSellerId(1);
        a.setStatus(AuctionStatus.OPEN);
        a.setStartTime(LocalDateTime.now().plusHours(2));
        a.setEndTime(LocalDateTime.now().plusHours(5));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(4, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "4", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("chưa bắt đầu"));
    }

    // 5) Nghiệp vụ đặt giá

    @Test
    @DisplayName("Chặn người bán tự đấu giá sản phẩm của mình")
    void testPlaceBidFailsWhenSellerBidsOnOwnItem() {
        Auction a = runningAuction(1, 5, new BigDecimal("500000"));
        mockCache.put(1, a);
        BidResponse res = auctionService.placeBid(new BidRequest(5, "1", new BigDecimal("600000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Người bán không thể tự đấu giá"));
    }

    @Test
    @DisplayName("Từ chối khi giá đặt thấp hơn giá hiện tại")
    void testPlaceBidFailsWhenPriceTooLow() {
        Auction a = runningAuction(2, 1, new BigDecimal("500000"));
        mockCache.put(2, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "2", new BigDecimal("400000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Mức giá đề xuất phải cao hơn"));
    }

    @Test
    @DisplayName("Từ chối khi giá đặt bằng giá hiện tại")
    void testPlaceBid_PriceEqualCurrent_ShouldFail() {
        Auction a = runningAuction(8, 1, new BigDecimal("500000"));
        mockCache.put(8, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "8", new BigDecimal("500000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Mức giá đề xuất phải cao hơn"));
    }

    @Test
    @DisplayName("Từ chối khi giá đặt cao hơn nhưng không đủ bước giá tối thiểu")
    void testPlaceBid_BelowMinIncrement_ShouldFail() {
        Auction a = runningAuction(13, 1, new BigDecimal("500000"));
        a.setMinBidIncrement(new BigDecimal("5000"));
        mockCache.put(13, a);
        // Cần >= 505000, chỉ đặt 501000 → fail
        BidResponse res = auctionService.placeBid(new BidRequest(10, "13", new BigDecimal("501000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("tối thiểu"));
    }

    @Test
    @DisplayName("Chấp nhận khi giá đặt đúng bằng currentPrice + minIncrement")
    void testPlaceBid_ExactMinIncrement_ShouldSucceed() throws SQLException {
        Auction a = runningAuction(14, 1, new BigDecimal("500000"));
        a.setMinBidIncrement(new BigDecimal("5000"));
        mockCache.put(14, a);
        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        BidResponse res = auctionService.placeBid(new BidRequest(10, "14", new BigDecimal("505000")));
        assertTrue(res.isSuccess());
    }

    @Test
    @DisplayName("Từ chối khi người đang dẫn đầu tự nâng giá")
    void testPlaceBid_SelfRaise_ShouldFail() {
        Auction a = runningAuction(11, 1, new BigDecimal("500000"));
        a.setHighestBidderId(10);
        mockCache.put(11, a);
        BidResponse res = auctionService.placeBid(new BidRequest(10, "11", new BigDecimal("700000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("đang dẫn đầu"));
    }

    @Test
    @DisplayName("Từ chối khi số dư không đủ")
    void testPlaceBid_InsufficientBalance_ShouldFail() throws SQLException {
        Auction a = runningAuction(15, 1, new BigDecimal("500000"));
        mockCache.put(15, a);
        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("100")));

        BidResponse res = auctionService.placeBid(new BidRequest(10, "15", new BigDecimal("502000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Số dư tài khoản không đủ"));
    }

    @Test
    @DisplayName("Từ chối khi user không tồn tại")
    void testPlaceBid_UserNotFound_ShouldFail() throws SQLException {
        Auction a = runningAuction(16, 1, new BigDecimal("500000"));
        mockCache.put(16, a);
        when(mockUserDAO.findById(anyInt())).thenReturn(null);

        BidResponse res = auctionService.placeBid(new BidRequest(10, "16", new BigDecimal("502000")));
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Tài khoản không tồn tại"));
    }

    // NHÓM 6: Luồng thực thi bid thành công

    @Test
    @DisplayName("Bid thành công: trừ tiền đúng, cập nhật giá, lưu transaction")
    void testPlaceBid_Success_FullFlow() throws SQLException {
        Auction a = runningAuction(20, 1, new BigDecimal("500000"));
        mockCache.put(20, a);

        User bidder = userWithBalance(10, new BigDecimal("1000000"));
        when(mockUserDAO.findById(10)).thenReturn(bidder);
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        BidResponse res = auctionService.placeBid(new BidRequest(10, "20", new BigDecimal("502000")));

        assertTrue(res.isSuccess());
        assertEquals(new BigDecimal("502000"), res.getCurrentHighestBid());
        assertTrue(res.getMessage().contains("502000"));

        // Verify DB được gọi đúng
        verify(mockAuctionDAO).updateBidPrice(20, 10, new BigDecimal("502000"));
        verify(mockBidDAO).saveBid(any(BidTransaction.class));
        verify(mockUserDAO).updateBalance(eq(10), any());
    }

    @Test
    @DisplayName("Bid thành công: cache được cập nhật giá mới")
    void testPlaceBid_Success_CacheUpdated() throws SQLException {
        Auction a = runningAuction(21, 1, new BigDecimal("500000"));
        mockCache.put(21, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        auctionService.placeBid(new BidRequest(10, "21", new BigDecimal("502000")));

        Auction cached = mockCache.get(21);
        assertNotNull(cached);
        assertEquals(new BigDecimal("502000"), cached.getCurrentPrice());
        assertEquals(10, cached.getHighestBidderId());
        assertEquals(AuctionStatus.RUNNING, cached.getStatus());
    }

    @Test
    @DisplayName("Bid thành công: hoàn tiền người dẫn đầu cũ")
    void testPlaceBid_Success_RefundPreviousBidder() throws SQLException {
        Auction a = runningAuction(22, 1, new BigDecimal("500000"));
        a.setHighestBidderId(99); // user 99 đang dẫn đầu
        mockCache.put(22, a);

        User previousBidder = userWithBalance(99, new BigDecimal("0")); // tiền đã bị trừ trước đó
        User newBidder = userWithBalance(10, new BigDecimal("999999"));
        when(mockUserDAO.findById(99)).thenReturn(previousBidder);
        when(mockUserDAO.findById(10)).thenReturn(newBidder);
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        auctionService.placeBid(new BidRequest(10, "22", new BigDecimal("502000")));

        // Verify hoàn tiền cho user 99 (500000) và trừ tiền user 10 (502000)
        verify(mockUserDAO).updateBalance(eq(99), eq(new BigDecimal("500000")));
        verify(mockUserDAO).updateBalance(eq(10), eq(new BigDecimal("497999")));
    }

    @Test
    @DisplayName("Bid thành công khi phiên ở trạng thái OPEN (chưa có bid nào)")
    void testPlaceBid_StatusOpen_ShouldSucceed() throws SQLException {
        Auction a = new Auction();
        a.setId(23); a.setSellerId(1);
        a.setStatus(AuctionStatus.OPEN);
        a.setStartTime(LocalDateTime.now().minusMinutes(5));
        a.setEndTime(LocalDateTime.now().plusHours(1));
        a.setCurrentPrice(new BigDecimal("500000"));
        mockCache.put(23, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        BidResponse res = auctionService.placeBid(new BidRequest(10, "23", new BigDecimal("502000")));
        assertTrue(res.isSuccess());
    }

    // 7) Race condition — DB updateBidPrice trả về false

    @Test
    @DisplayName("Rollback đúng khi DB cập nhật thất bại (race condition)")
    void testPlaceBid_DBUpdateFails_ShouldRollback() throws SQLException {
        Auction a = runningAuction(30, 1, new BigDecimal("500000"));
        mockCache.put(30, a);

        User bidder = userWithBalance(10, new BigDecimal("999999"));
        when(mockUserDAO.findById(10)).thenReturn(bidder);
        // DB trả false → ai đó vừa bid cao hơn
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(false);
        Auction freshAuction = runningAuction(30, 1, new BigDecimal("510000"));
        when(mockAuctionDAO.findById(30)).thenReturn(freshAuction);

        BidResponse res = auctionService.placeBid(new BidRequest(10, "30", new BigDecimal("502000")));

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Đã có người đặt giá cao hơn"));
        // Verify tiền được hoàn lại cho bidder
        verify(mockUserDAO, atLeastOnce()).updateBalance(eq(10), any());
    }

    @Test
    @DisplayName("Cache được làm mới từ DB sau khi race condition")
    void testPlaceBid_DBUpdateFails_CacheRefreshed() throws SQLException {
        Auction a = runningAuction(31, 1, new BigDecimal("500000"));
        mockCache.put(31, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(false);

        Auction freshAuction = runningAuction(31, 1, new BigDecimal("600000"));
        when(mockAuctionDAO.findById(31)).thenReturn(freshAuction);

        auctionService.placeBid(new BidRequest(10, "31", new BigDecimal("502000")));

        // Cache phải chứa giá fresh từ DB (600000), không phải giá stale (500000)
        assertEquals(new BigDecimal("600000"), mockCache.get(31).getCurrentPrice());
    }

    // 8) Anti-sniping

    @Test
    @DisplayName("Anti-snipe: kích hoạt gia hạn khi bid trong 30 giây cuối")
    void testPlaceBid_AntiSnipe_ShouldExtend() throws SQLException {
        Auction a = almostExpiredAuction(40, 1, new BigDecimal("500000"));
        LocalDateTime originalEnd = a.getEndTime();
        mockCache.put(40, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        auctionService.placeBid(new BidRequest(10, "40", new BigDecimal("502000")));

        // endTime phải được gia hạn thêm 60 giây
        assertTrue(mockCache.get(40).getEndTime().isAfter(originalEnd));
        verify(mockAuctionDAO).extendEndTime(eq(40), any());
    }

    @Test
    @DisplayName("Anti-snipe: KHÔNG gia hạn khi còn nhiều thời gian")
    void testPlaceBid_NoAntiSnipe_WhenTimeRemaining() throws SQLException {
        Auction a = runningAuction(41, 1, new BigDecimal("500000")); // còn 30 phút
        LocalDateTime originalEnd = a.getEndTime();
        mockCache.put(41, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        auctionService.placeBid(new BidRequest(10, "41", new BigDecimal("502000")));

        // endTime không thay đổi
        assertEquals(originalEnd, mockCache.get(41).getEndTime());
        verify(mockAuctionDAO, never()).extendEndTime(anyInt(), any());
    }

    @Test
    @DisplayName("Anti-snipe: extensionCount tăng thêm 1 sau mỗi lần gia hạn")
    void testPlaceBid_AntiSnipe_ExtensionCountIncremented() throws SQLException {
        Auction a = almostExpiredAuction(42, 1, new BigDecimal("500000"));
        a.setExtensionCount(2); // đã gia hạn 2 lần rồi
        mockCache.put(42, a);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        auctionService.placeBid(new BidRequest(10, "42", new BigDecimal("502000")));

        assertEquals(3, mockCache.get(42).getExtensionCount());
    }

    // 9) closeAuction

    @Test
    @DisplayName("closeAuction: trạng thái chuyển sang FINISHED")
    void testCloseAuction_StatusFinished() {
        Auction a = runningAuction(60, 1, new BigDecimal("500000"));
        a.setHighestBidderId(0); // không có ai bid
        mockCache.put(60, a);

        auctionService.closeAuction(a);

        assertEquals(AuctionStatus.FINISHED, a.getStatus());
        verify(mockAuctionDAO).updateStatus(60, AuctionStatus.FINISHED);
    }

    @Test
    @DisplayName("closeAuction: xóa phiên khỏi cache sau khi đóng")
    void testCloseAuction_RemovedFromCache() {
        Auction a = runningAuction(61, 1, new BigDecimal("500000"));
        a.setHighestBidderId(0);
        mockCache.put(61, a);

        auctionService.closeAuction(a);

        assertNull(mockCache.get(61));
    }

    @Test
    @DisplayName("closeAuction: cộng tiền cho seller khi có người thắng đạt giá sàn")
    void testCloseAuction_PaySeller_WhenReserveMet() throws SQLException {
        Auction a = runningAuction(62, 1, new BigDecimal("500000"));
        a.setHighestBidderId(10);
        a.setReservePrice(new BigDecimal("400000")); // đạt giá sàn
        mockCache.put(62, a);

        User seller = userWithBalance(1, new BigDecimal("0"));
        when(mockUserDAO.findById(1)).thenReturn(seller);

        auctionService.closeAuction(a);

        verify(mockUserDAO).updateBalance(eq(1), eq(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("closeAuction: không có giao dịch tài chính khi không có ai bid")
    void testCloseAuction_NoBidder_NoFinancialTransaction() throws SQLException {
        Auction a = runningAuction(64, 1, new BigDecimal("500000"));
        a.setHighestBidderId(0); // không có ai bid
        mockCache.put(64, a);

        auctionService.closeAuction(a);

        verify(mockUserDAO, never()).updateBalance(anyInt(), any());
    }

    // 10) approveAuction / rejectAuction / Admin actions

    @Test
    @DisplayName("approveAuction: PENDING → OPEN thành công")
    void testApproveAuction_Success() {
        Auction a = new Auction();
        a.setId(70); a.setStatus(AuctionStatus.PENDING);
        when(mockAuctionDAO.findById(70)).thenReturn(a);
        when(mockAuctionDAO.updateStatus(70, AuctionStatus.OPEN)).thenReturn(true);

        boolean result = auctionService.approveAuction(70, 1);

        assertTrue(result);
        assertEquals(AuctionStatus.OPEN, a.getStatus());
        verify(mockAuctionDAO).updateStatus(70, AuctionStatus.OPEN);
        assertNotNull(mockCache.get(70)); // phiên phải được đưa vào cache
    }

    @Test
    @DisplayName("approveAuction: thất bại khi phiên không tồn tại")
    void testApproveAuction_NotFound_ShouldFail() {
        when(mockAuctionDAO.findById(999)).thenReturn(null);
        assertFalse(auctionService.approveAuction(999, 1));
    }

    @Test
    @DisplayName("approveAuction: thất bại khi phiên không ở PENDING")
    void testApproveAuction_WrongStatus_ShouldFail() {
        Auction a = runningAuction(71, 1, new BigDecimal("500000")); // RUNNING
        when(mockAuctionDAO.findById(71)).thenReturn(a);

        assertFalse(auctionService.approveAuction(71, 1));
        verify(mockAuctionDAO, never()).updateStatus(anyInt(), any());
    }

    @Test
    @DisplayName("rejectAuction: PENDING → CANCELED thành công")
    void testRejectAuction_Success() {
        Auction a = new Auction();
        a.setId(72); a.setStatus(AuctionStatus.PENDING);
        when(mockAuctionDAO.findById(72)).thenReturn(a);

        boolean result = auctionService.rejectAuction(72, 1, "Sản phẩm vi phạm");

        assertTrue(result);
        assertEquals(AuctionStatus.CANCELED, a.getStatus());
        verify(mockAuctionDAO).updateStatus(72, AuctionStatus.CANCELED);
    }

    @Test
    @DisplayName("rejectAuction: thất bại khi phiên không ở PENDING")
    void testRejectAuction_WrongStatus_ShouldFail() {
        Auction a = runningAuction(73, 1, new BigDecimal("500000"));
        when(mockAuctionDAO.findById(73)).thenReturn(a);

        assertFalse(auctionService.rejectAuction(73, 1, "lý do"));
    }

    @Test
    @DisplayName("pauseAuction: RUNNING → PAUSED thành công")
    void testPauseAuction_Success() {
        Auction a = runningAuction(74, 1, new BigDecimal("500000"));
        mockCache.put(74, a);
        when(mockAuctionDAO.updateStatus(74, AuctionStatus.PAUSED)).thenReturn(true);

        boolean result = auctionService.pauseAuction(74, 1);

        assertTrue(result);
        assertEquals(AuctionStatus.PAUSED, a.getStatus());
    }

    @Test
    @DisplayName("pauseAuction: thất bại khi phiên không ở RUNNING")
    void testPauseAuction_NotRunning_ShouldFail() {
        Auction a = runningAuction(75, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.PAUSED);
        mockCache.put(75, a);

        assertFalse(auctionService.pauseAuction(75, 1));
    }

    @Test
    @DisplayName("resumeAuction: PAUSED → RUNNING thành công")
    void testResumeAuction_Success() {
        Auction a = runningAuction(76, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.PAUSED);
        mockCache.put(76, a);
        when(mockAuctionDAO.updateStatus(76, AuctionStatus.RUNNING)).thenReturn(true);

        boolean result = auctionService.resumeAuction(76, 1);

        assertTrue(result);
        assertEquals(AuctionStatus.RUNNING, a.getStatus());
    }

    @Test
    @DisplayName("cancelAuction: xóa phiên khỏi cache sau khi hủy")
    void testCancelAuction_RemovedFromCache() {
        Auction a = runningAuction(77, 1, new BigDecimal("500000"));
        mockCache.put(77, a);
        when(mockAuctionDAO.updateStatus(77, AuctionStatus.CANCELED)).thenReturn(true);

        auctionService.cancelAuction(77, 1, "lý do");

        assertNull(mockCache.get(77));
    }

    @Test
    @DisplayName("cancelAuction: thất bại khi phiên đã FINISHED")
    void testCancelAuction_AlreadyFinished_ShouldFail() {
        Auction a = runningAuction(78, 1, new BigDecimal("500000"));
        a.setStatus(AuctionStatus.FINISHED);
        mockCache.put(78, a);

        assertFalse(auctionService.cancelAuction(78, 1, "lý do"));
    }

    // 11) createAuction validation

    @Test
    @DisplayName("createAuction: ném exception khi endTime trước startTime")
    void testCreateAuction_EndBeforeStart_ShouldThrow() {
        assertThrows(AuctionException.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("500000"), null,
                        LocalDateTime.now().plusHours(2),
                        LocalDateTime.now().plusHours(1)));
    }

    @Test
    @DisplayName("createAuction: ném exception khi startingPrice = 0")
    void testCreateAuction_ZeroPrice_ShouldThrow() {
        assertThrows(AuctionException.class, () ->
                auctionService.createAuction(1, 2, BigDecimal.ZERO, null,
                        LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusHours(2)));
    }

    @Test
    @DisplayName("createAuction: ném exception khi startingPrice âm")
    void testCreateAuction_NegativePrice_ShouldThrow() {
        assertThrows(AuctionException.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("-1000"), null,
                        LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusHours(2)));
    }

    @Test
    @DisplayName("createAuction: ném exception khi startTime hoặc endTime null")
    void testCreateAuction_NullTime_ShouldThrow() {
        assertThrows(AuctionException.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("500000"), null, null, null));
    }

    @Test
    @DisplayName("createAuction: ném exception khi endTime bằng startTime")
    void testCreateAuction_EndEqualsStart_ShouldThrow() {
        LocalDateTime time = LocalDateTime.now().plusHours(1);
        assertThrows(AuctionException.class, () ->
                auctionService.createAuction(1, 2, new BigDecimal("500000"), null, time, time));
    }

    @Test
    @DisplayName("createAuction: thành công, gọi saveAuction và trả về trạng thái PENDING")
    void testCreateAuction_Valid_ShouldSaveAndReturnPending() {
        Auction result = auctionService.createAuction(1, 2, new BigDecimal("500000"), null,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3));

        assertEquals(AuctionStatus.PENDING, result.getStatus());
        verify(mockAuctionDAO).saveAuction(any(Auction.class));
    }

    // 12) Concurrency — nhiều thread bid cùng lúc

    @Test
    @DisplayName("Concurrency: chỉ đúng 1 bid thắng khi 10 thread bid cùng lúc vào 1 phiên")
    void testPlaceBid_Concurrent_OnlyOneWins() throws InterruptedException, SQLException {
        Auction a = runningAuction(90, 1, new BigDecimal("500000"));
        mockCache.put(90, a);

        // Mỗi user có đủ tiền
        for (int i = 10; i < 20; i++) {
            int uid = i;
            when(mockUserDAO.findById(uid)).thenReturn(userWithBalance(uid, new BigDecimal("999999")));
        }

        // Chỉ thread đầu tiên thắng DB, còn lại trả false
        AtomicInteger dbCallCount = new AtomicInteger(0);
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any()))
                .thenAnswer(inv -> dbCallCount.incrementAndGet() == 1);
        when(mockAuctionDAO.findById(90)).thenReturn(runningAuction(90, 1, new BigDecimal("502000")));

        int threadCount = 10;
        CountDownLatch ready  = new CountDownLatch(threadCount);
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 10; i < 10 + threadCount; i++) {
            int uid = i;
            new Thread(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                BidResponse res = auctionService.placeBid(
                        new BidRequest(uid, "90", new BigDecimal("502000")));
                if (res.isSuccess()) successCount.incrementAndGet();
                done.countDown();
            }).start();
        }

        ready.await();
        start.countDown(); // tất cả thread bắt đầu cùng lúc
        done.await(5, TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Chỉ đúng 1 bid được phép thắng");
    }

    @Test
    @DisplayName("Concurrency: 2 phiên khác nhau có thể bid độc lập cùng lúc (không chặn nhau)")
    void testPlaceBid_Concurrent_DifferentAuctions_Independent() throws InterruptedException, SQLException {
        // Phiên 91 và 92 hoàn toàn độc lập, lock không ảnh hưởng nhau
        Auction a91 = runningAuction(91, 1, new BigDecimal("500000"));
        Auction a92 = runningAuction(92, 1, new BigDecimal("500000"));
        mockCache.put(91, a91);
        mockCache.put(92, a92);

        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockUserDAO.findById(20)).thenReturn(userWithBalance(20, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(anyInt(), anyInt(), any())).thenReturn(true);

        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        new Thread(() -> {
            if (auctionService.placeBid(new BidRequest(10, "91", new BigDecimal("502000"))).isSuccess())
                successCount.incrementAndGet();
            done.countDown();
        }).start();

        new Thread(() -> {
            if (auctionService.placeBid(new BidRequest(20, "92", new BigDecimal("502000"))).isSuccess())
                successCount.incrementAndGet();
            done.countDown();
        }).start();

        done.await(5, TimeUnit.SECONDS);
        assertEquals(2, successCount.get(), "2 phiên khác nhau phải bid thành công độc lập");
    }

    @Test
    @DisplayName("Concurrency: nhiều bid tuần tự hợp lệ — giá tăng dần đúng thứ tự")
    void testPlaceBid_Sequential_PriceIncreases() throws SQLException {
        Auction a = runningAuction(93, 1, new BigDecimal("500000"));
        mockCache.put(93, a);

        // User 10 bid 502000
        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("999999")));
        when(mockAuctionDAO.updateBidPrice(eq(93), eq(10), eq(new BigDecimal("502000")))).thenReturn(true);
        BidResponse r1 = auctionService.placeBid(new BidRequest(10, "93", new BigDecimal("502000")));
        assertTrue(r1.isSuccess());

        // User 20 bid 504000 (vượt user 10)
        when(mockUserDAO.findById(20)).thenReturn(userWithBalance(20, new BigDecimal("999999")));
        when(mockUserDAO.findById(10)).thenReturn(userWithBalance(10, new BigDecimal("497999")));
        when(mockAuctionDAO.updateBidPrice(eq(93), eq(20), eq(new BigDecimal("504000")))).thenReturn(true);
        BidResponse r2 = auctionService.placeBid(new BidRequest(20, "93", new BigDecimal("504000")));
        assertTrue(r2.isSuccess());

        assertEquals(new BigDecimal("504000"), mockCache.get(93).getCurrentPrice());
        assertEquals(20, mockCache.get(93).getHighestBidderId());
    }
}