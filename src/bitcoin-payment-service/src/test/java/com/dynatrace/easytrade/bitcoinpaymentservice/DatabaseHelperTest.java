package com.dynatrace.easytrade.bitcoinpaymentservice;

import com.dynatrace.easytrade.bitcoinpaymentservice.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseHelper Unit Tests")
public class DatabaseHelperTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private DatabaseHelper databaseHelper;

    @BeforeEach
    void setUp() {
        databaseHelper = new DatabaseHelper();
    }

    @Test
    @DisplayName("createWallet_ValidData_ReturnsWalletWithId")
    void createWallet_ValidData_ReturnsWalletWithId() throws SQLException {
        // Arrange
        BitcoinWallet wallet = new BitcoinWallet();
        wallet.setAccountId("ACC-001");
        wallet.setWalletAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        wallet.setBalance(0.0);

        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(preparedStatement);
        when(preparedStatement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("wallet-123");

        // Act
        BitcoinWallet result = databaseHelper.createWalletWithConnection(wallet, connection);

        // Assert
        assertNotNull(result);
        assertEquals("wallet-123", result.getWalletId());
        assertEquals("ACC-001", result.getAccountId());
        verify(preparedStatement, times(1)).setString(anyInt(), anyString());
        verify(preparedStatement, times(1)).setDouble(anyInt(), anyDouble());
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("getWalletByAccountId_WalletExists_ReturnsWallet")
    void getWalletByAccountId_WalletExists_ReturnsWallet() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("WalletId")).thenReturn("wallet-123");
        when(resultSet.getString("AccountId")).thenReturn("ACC-001");
        when(resultSet.getString("WalletAddress")).thenReturn("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        when(resultSet.getDouble("Balance")).thenReturn(0.05);
        when(resultSet.getTimestamp("CreatedAt")).thenReturn(new Timestamp(System.currentTimeMillis()));

        // Act
        BitcoinWallet result = databaseHelper.getWalletByAccountIdWithConnection("ACC-001", connection);

        // Assert
        assertNotNull(result);
        assertEquals("wallet-123", result.getWalletId());
        assertEquals("ACC-001", result.getAccountId());
        assertEquals(0.05, result.getBalance());
        verify(preparedStatement, times(1)).setString(1, "ACC-001");
        verify(preparedStatement, times(1)).executeQuery();
    }

    @Test
    @DisplayName("getWalletByAccountId_WalletNotExists_ReturnsNull")
    void getWalletByAccountId_WalletNotExists_ReturnsNull() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Act
        BitcoinWallet result = databaseHelper.getWalletByAccountIdWithConnection("ACC-999", connection);

        // Assert
        assertNull(result);
        verify(preparedStatement, times(1)).setString(1, "ACC-999");
    }

    @Test
    @DisplayName("createTransaction_ValidData_ReturnsTransactionWithId")
    void createTransaction_ValidData_ReturnsTransactionWithId() throws SQLException {
        // Arrange
        BitcoinTransaction transaction = new BitcoinTransaction();
        transaction.setWalletId("wallet-123");
        transaction.setTransactionHash("tx-hash-abc");
        transaction.setAmount(0.001);
        transaction.setTransactionType(TransactionType.PAYMENT);
        transaction.setStatus(PaymentStatus.PENDING);

        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(preparedStatement);
        when(preparedStatement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("txn-123");

        // Act
        BitcoinTransaction result = databaseHelper.createTransactionWithConnection(transaction, connection);

        // Assert
        assertNotNull(result);
        assertEquals("txn-123", result.getTransactionId());
        assertEquals("wallet-123", result.getWalletId());
        assertEquals(0.001, result.getAmount());
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("getTransactionById_TransactionExists_ReturnsTransaction")
    void getTransactionById_TransactionExists_ReturnsTransaction() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("TransactionId")).thenReturn("txn-123");
        when(resultSet.getString("WalletId")).thenReturn("wallet-123");
        when(resultSet.getString("TransactionHash")).thenReturn("tx-hash-abc");
        when(resultSet.getDouble("Amount")).thenReturn(0.001);
        when(resultSet.getString("TransactionType")).thenReturn("PAYMENT");
        when(resultSet.getString("Status")).thenReturn("PENDING");
        when(resultSet.getInt("Confirmations")).thenReturn(0);
        when(resultSet.getTimestamp("CreatedAt")).thenReturn(new Timestamp(System.currentTimeMillis()));

        // Act
        BitcoinTransaction result = databaseHelper.getTransactionByIdWithConnection("txn-123", connection);

        // Assert
        assertNotNull(result);
        assertEquals("txn-123", result.getTransactionId());
        assertEquals("wallet-123", result.getWalletId());
        assertEquals(PaymentStatus.PENDING, result.getStatus());
        assertEquals(TransactionType.PAYMENT, result.getTransactionType());
        verify(preparedStatement, times(1)).setString(1, "txn-123");
    }

    @Test
    @DisplayName("updateTransactionStatus_ValidData_UpdatesSuccessfully")
    void updateTransactionStatus_ValidData_UpdatesSuccessfully() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // Act
        databaseHelper.updateTransactionStatusWithConnection("txn-123", PaymentStatus.CONFIRMED, 6, connection);

        // Assert
        verify(preparedStatement, times(1)).setString(1, "CONFIRMED");
        verify(preparedStatement, times(1)).setInt(2, 6);
        verify(preparedStatement, times(1)).setString(3, "txn-123");
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    @DisplayName("getTransactionsByWalletId_MultipleTransactions_ReturnsList")
    void getTransactionsByWalletId_MultipleTransactions_ReturnsList() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("TransactionId")).thenReturn("txn-1", "txn-2");
        when(resultSet.getString("WalletId")).thenReturn("wallet-123");
        when(resultSet.getString("TransactionHash")).thenReturn("hash-1", "hash-2");
        when(resultSet.getDouble("Amount")).thenReturn(0.001, 0.002);
        when(resultSet.getString("TransactionType")).thenReturn("PAYMENT");
        when(resultSet.getString("Status")).thenReturn("PENDING", "CONFIRMED");
        when(resultSet.getInt("Confirmations")).thenReturn(0, 6);
        when(resultSet.getTimestamp("CreatedAt")).thenReturn(new Timestamp(System.currentTimeMillis()));

        // Act
        List<BitcoinTransaction> results = databaseHelper.getTransactionsByWalletIdWithConnection("wallet-123", connection);

        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("txn-1", results.get(0).getTransactionId());
        assertEquals("txn-2", results.get(1).getTransactionId());
        assertEquals(PaymentStatus.PENDING, results.get(0).getStatus());
        assertEquals(PaymentStatus.CONFIRMED, results.get(1).getStatus());
        verify(preparedStatement, times(1)).setString(1, "wallet-123");
    }

    @Test
    @DisplayName("getTransactionsByWalletId_NoTransactions_ReturnsEmptyList")
    void getTransactionsByWalletId_NoTransactions_ReturnsEmptyList() throws SQLException {
        // Arrange
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // Act
        List<BitcoinTransaction> results = databaseHelper.getTransactionsByWalletIdWithConnection("wallet-999", connection);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
