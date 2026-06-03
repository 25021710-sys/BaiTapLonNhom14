package com.auction.server.service;

import com.auction.common.request.BalanceRequest;
import com.auction.common.request.LoginRequest;
import com.auction.common.request.RegisterRequest;
import com.auction.common.response.BalanceResponse;
import com.auction.common.response.LoginResponse;
import com.auction.common.response.RegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthService Validation Tests")
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
    }

    // Register: validate đầu vào (không cần DB)

    @Test
    @DisplayName("Register: trả về lỗi khi request null")
    void testRegister_NullRequest_ShouldFail() throws Exception {
        RegisterResponse res = authService.register(null);
        assertFalse(res.isSuccess());
        assertEquals("Dữ liệu không hợp lệ", res.getMessage());
    }

    @Test
    @DisplayName("Register: trả về lỗi khi email null")
    void testRegister_NullEmail_ShouldFail() throws Exception {
        // RegisterRequest(username, email, password)
        RegisterResponse res = authService.register(
                new RegisterRequest("user1", null, "pass123"));
        assertFalse(res.isSuccess());
        assertEquals("Dữ liệu không hợp lệ", res.getMessage());
    }

    @Test
    @DisplayName("Register: trả về lỗi khi password null")
    void testRegister_NullPassword_ShouldFail() throws Exception {
        RegisterResponse res = authService.register(
                new RegisterRequest("user1", "user@test.com", null));
        assertFalse(res.isSuccess());
        assertEquals("Dữ liệu không hợp lệ", res.getMessage());
    }

    @Test
    @DisplayName("Register: trả về lỗi khi username null")
    void testRegister_NullUsername_ShouldFail() throws Exception {
        RegisterResponse res = authService.register(
                new RegisterRequest(null, "user@test.com", "pass123"));
        assertFalse(res.isSuccess());
        assertEquals("Dữ liệu không hợp lệ", res.getMessage());
    }

    // handleBalance: validate đầu vào (không cần DB)

    @Test
    @DisplayName("handleBalance: trả về lỗi khi userId không tồn tại")
    void testHandleBalance_UserNotFound_ShouldFail() {
        // BalanceRequest(userId, amount, type)
        BalanceRequest req = new BalanceRequest(99999, new BigDecimal("100000"), "DEPOSIT");
        BalanceResponse res = authService.handleBalance(req);
        assertFalse(res.isSuccess());
        assertEquals("User không tồn tại", res.getMessage());
    }

    @Test
    @DisplayName("handleBalance: không crash khi amount null")
    void testHandleBalance_NullAmount_DoesNotCrash() {
        BalanceRequest req = new BalanceRequest(99999, null, "DEPOSIT");
        BalanceResponse res = authService.handleBalance(req);
        assertNotNull(res);
        assertFalse(res.isSuccess());
    }

    // Login

    @Test
    @DisplayName("Login: email/password không khớp → thất bại, user null")
    void testLogin_InvalidCredentials_ShouldFail() throws Exception {
        LoginRequest req = new LoginRequest("notexist@nowhere.com", "wrongpassword");
        LoginResponse res = authService.login(req);
        assertFalse(res.isSuccess());
        assertEquals("Sai tài khoản", res.getMessage());
        assertNull(res.getUser());
    }

    @Test
    @DisplayName("Login: response không bao giờ null dù thành công hay thất bại")
    void testLogin_ResponseNeverNull() throws Exception {
        LoginRequest req = new LoginRequest("any@email.com", "anypassword");
        LoginResponse res = authService.login(req);
        assertNotNull(res);
        assertNotNull(res.getMessage());
    }
}