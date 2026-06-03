package com.auction.server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Model Tests")
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setBalance(new BigDecimal("1000000"));
    }

    // deposit

    @Test
    @DisplayName("Nạp tiền hợp lệ → số dư tăng đúng")
    void testDeposit_ValidAmount_BalanceIncreases() {
        user.deposit(new BigDecimal("500000"));
        assertEquals(new BigDecimal("1500000"), user.getBalance());
    }

    @Test
    @DisplayName("Nạp tiền âm → ném IllegalArgumentException")
    void testDeposit_NegativeAmount_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> user.deposit(new BigDecimal("-100")));
    }

    @Test
    @DisplayName("Nạp tiền bằng 0 → ném IllegalArgumentException")
    void testDeposit_ZeroAmount_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> user.deposit(BigDecimal.ZERO));
    }

    // withdraw

    @Test
    @DisplayName("Rút tiền hợp lệ → số dư giảm đúng")
    void testWithdraw_ValidAmount_BalanceDecreases() {
        user.withdraw(new BigDecimal("300000"));
        assertEquals(new BigDecimal("700000"), user.getBalance());
    }

    @Test
    @DisplayName("Rút tiền bằng đúng số dư → số dư về 0")
    void testWithdraw_ExactBalance_ResultZero() {
        user.withdraw(new BigDecimal("1000000"));
        assertEquals(BigDecimal.ZERO, user.getBalance());
    }

    @Test
    @DisplayName("Rút tiền âm → ném IllegalArgumentException")
    void testWithdraw_NegativeAmount_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> user.withdraw(new BigDecimal("-100")));
    }

    @Test
    @DisplayName("Rút tiền bằng 0 → ném IllegalArgumentException")
    void testWithdraw_ZeroAmount_ShouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> user.withdraw(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Rút quá số dư → ném IllegalStateException")
    void testWithdraw_ExceedsBalance_ShouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> user.withdraw(new BigDecimal("9999999")));
    }

    // Role & Default values

    @Test
    @DisplayName("Role mặc định khi tạo User mới là USER")
    void testDefaultRole_IsUser() {
        assertEquals(UserRole.USER, new User().getRole());
    }

    @Test
    @DisplayName("Số dư mặc định khi tạo User mới là 0")
    void testDefaultBalance_IsZero() {
        assertEquals(BigDecimal.ZERO, new User().getBalance());
    }

    @Test
    @DisplayName("setUsername và getUsername hoạt động đúng")
    void testSetGetUsername() {
        user.setUsername("auction_user");
        assertEquals("auction_user", user.getUsername());
    }

    @Test
    @DisplayName("setEmail và getEmail hoạt động đúng")
    void testSetGetEmail() {
        user.setEmail("test@auction.com");
        assertEquals("test@auction.com", user.getEmail());
    }
}