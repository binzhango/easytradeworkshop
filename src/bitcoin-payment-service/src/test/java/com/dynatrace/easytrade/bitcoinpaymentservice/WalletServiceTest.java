package com.dynatrace.easytrade.bitcoinpaymentservice;

import com.dynatrace.easytrade.bitcoinpaymentservice.models.BitcoinWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
public class WalletServiceTest {

    @Mock
    private DatabaseHelper databaseHelper;

    @InjectMocks
    private WalletService walletService;

    private BitcoinWallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = new BitcoinWallet();
        testWallet.setWalletId("test-wallet-123");
        testWallet.setAccountId("ACC-001");
        testWallet.setWalletAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        testWallet.setBalance(0.0);
        testWallet.setCreatedAt(new Timestamp(System.currentTimeMillis()));
    }

    @Test
    @DisplayName("getOrCreateWallet_NewWallet_CreatesAndReturnsWallet")
    void getOrCreateWallet_NewWallet_CreatesAndReturnsWallet() throws SQLException {
        // Arrange
        when(databaseHelper.getWalletByAccountId(anyString())).thenReturn(null);
        when(databaseHelper.createWallet(any(BitcoinWallet.class))).thenReturn(testWallet);

        // Act
        BitcoinWallet result = walletService.getOrCreateWallet("ACC-001");

        // Assert
        assertNotNull(result);
        assertEquals("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", result.getWalletAddress());
        verify(databaseHelper, times(1)).getWalletByAccountId("ACC-001");
        verify(databaseHelper, times(1)).createWallet(any(BitcoinWallet.class));
    }

    @Test
    @DisplayName("getOrCreateWallet_ExistingWallet_ReturnsExistingWallet")
    void getOrCreateWallet_ExistingWallet_ReturnsExistingWallet() throws SQLException {
        // Arrange
        when(databaseHelper.getWalletByAccountId(anyString())).thenReturn(testWallet);

        // Act
        BitcoinWallet result = walletService.getOrCreateWallet("ACC-001");

        // Assert
        assertNotNull(result);
        assertEquals(testWallet.getWalletAddress(), result.getWalletAddress());
        assertEquals(testWallet.getAccountId(), result.getAccountId());
        verify(databaseHelper, times(1)).getWalletByAccountId("ACC-001");
        verify(databaseHelper, never()).createWallet(any(BitcoinWallet.class));
    }

    @Test
    @DisplayName("generateBitcoinAddress_ReturnsValidFormat")
    void generateBitcoinAddress_ReturnsValidFormat() {
        // Act
        String address = walletService.generateBitcoinAddress();

        // Assert
        assertNotNull(address);
        assertTrue(address.startsWith("bc1q"), "Address should start with bc1q (Bech32 format)");
        assertEquals(42, address.length(), "Address should be 42 characters long");
        assertTrue(address.matches("^bc1q[a-z0-9]{38}$"), "Address should match Bech32 pattern");
    }

    @Test
    @DisplayName("generateBitcoinAddress_GeneratesUniqueAddresses")
    void generateBitcoinAddress_GeneratesUniqueAddresses() {
        // Act
        String address1 = walletService.generateBitcoinAddress();
        String address2 = walletService.generateBitcoinAddress();
        String address3 = walletService.generateBitcoinAddress();

        // Assert
        assertNotEquals(address1, address2);
        assertNotEquals(address2, address3);
        assertNotEquals(address1, address3);
    }

    @Test
    @DisplayName("generateQRCodeData_ReturnsCorrectBIP21Format")
    void generateQRCodeData_ReturnsCorrectBIP21Format() {
        // Arrange
        String address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";
        double amount = 0.001;

        // Act
        String qrData = walletService.generateQRCodeData(address, amount);

        // Assert
        assertNotNull(qrData);
        assertTrue(qrData.startsWith("bitcoin:"));
        assertTrue(qrData.contains("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"));
        assertTrue(qrData.contains("?amount=0.001"));
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.001", qrData);
    }

    @Test
    @DisplayName("generateQRCodeData_WithZeroAmount_ReturnsCorrectFormat")
    void generateQRCodeData_WithZeroAmount_ReturnsCorrectFormat() {
        // Arrange
        String address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh";
        double amount = 0.0;

        // Act
        String qrData = walletService.generateQRCodeData(address, amount);

        // Assert
        assertEquals("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.0", qrData);
    }

    @Test
    @DisplayName("getOrCreateWallet_DatabaseError_ThrowsSQLException")
    void getOrCreateWallet_DatabaseError_ThrowsSQLException() throws SQLException {
        // Arrange
        when(databaseHelper.getWalletByAccountId(anyString()))
                .thenThrow(new SQLException("Database connection failed"));

        // Act & Assert
        assertThrows(SQLException.class, () -> {
            walletService.getOrCreateWallet("ACC-001");
        });
        verify(databaseHelper, times(1)).getWalletByAccountId("ACC-001");
        verify(databaseHelper, never()).createWallet(any(BitcoinWallet.class));
    }
}
