package com.dynatrace.easytrade.bitcoinpaymentservice;

import com.dynatrace.easytrade.bitcoinpaymentservice.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseHelper {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHelper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Wallet queries
    private static final String GET_WALLET_BY_ACCOUNT = 
        "SELECT * FROM BitcoinWallets WHERE AccountId = ?";
    private static final String INSERT_WALLET = 
        "INSERT INTO BitcoinWallets (Id, AccountId, WalletAddress, Balance, CreatedAt, UpdatedAt) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_WALLET_BALANCE = 
        "UPDATE BitcoinWallets SET Balance = ?, UpdatedAt = ? WHERE AccountId = ?";

    // Transaction queries
    private static final String INSERT_TRANSACTION = 
        "INSERT INTO BitcoinTransactions (Id, AccountId, WalletAddress, Amount, Type, Status, Purpose, Metadata, CreatedAt, UpdatedAt, ExpiresAt, Confirmations) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String GET_TRANSACTION_BY_ID = 
        "SELECT * FROM BitcoinTransactions WHERE Id = ?";
    private static final String GET_TRANSACTIONS_BY_ACCOUNT = 
        "SELECT * FROM BitcoinTransactions WHERE AccountId = ? ORDER BY CreatedAt DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    private static final String COUNT_TRANSACTIONS_BY_ACCOUNT = 
        "SELECT COUNT(*) FROM BitcoinTransactions WHERE AccountId = ?";
    private static final String UPDATE_TRANSACTION_STATUS = 
        "UPDATE BitcoinTransactions SET Status = ?, Confirmations = ?, UpdatedAt = ?, TransactionHash = ?, ConfirmedAt = ? WHERE Id = ?";
    private static final String GET_PENDING_TRANSACTIONS = 
        "SELECT * FROM BitcoinTransactions WHERE Status IN ('PENDING', 'CONFIRMING') AND ExpiresAt > ?";

    // Status history queries
    private static final String INSERT_STATUS = 
        "INSERT INTO BitcoinPaymentStatus (TransactionId, Status, Timestamp, Details) VALUES (?, ?, ?, ?)";
    private static final String GET_STATUS_HISTORY = 
        "SELECT * FROM BitcoinPaymentStatus WHERE TransactionId = ? ORDER BY Timestamp DESC";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(System.getenv("MSSQL_CONNECTIONSTRING"));
    }

    // Wallet operations
    public Optional<BitcoinWallet> getWalletByAccount(Connection conn, Integer accountId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(GET_WALLET_BY_ACCOUNT)) {
            stmt.setInt(1, accountId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapWalletFromResultSet(rs));
            }
        }
        return Optional.empty();
    }

    public String createWallet(Connection conn, Integer accountId, String walletAddress) throws SQLException {
        String walletId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        try (PreparedStatement stmt = conn.prepareStatement(INSERT_WALLET)) {
            stmt.setString(1, walletId);
            stmt.setInt(2, accountId);
            stmt.setString(3, walletAddress);
            stmt.setBigDecimal(4, BigDecimal.ZERO);
            stmt.setObject(5, now);
            stmt.setObject(6, now);
            stmt.executeUpdate();
        }

        logger.info("Created wallet {} for account {}", walletAddress, accountId);
        return walletId;
    }

    public void updateWalletBalance(Connection conn, Integer accountId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_WALLET_BALANCE)) {
            stmt.setBigDecimal(1, newBalance);
            stmt.setObject(2, OffsetDateTime.now());
            stmt.setInt(3, accountId);
            stmt.executeUpdate();
        }
        logger.info("Updated balance for account {} to {}", accountId, newBalance);
    }

    // Transaction operations
    public String createTransaction(Connection conn, CreatePaymentRequest request, String walletAddress) throws SQLException {
        String transactionId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(24);

        String metadataJson = null;
        if (request.getMetadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (Exception e) {
                logger.warn("Failed to serialize metadata", e);
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRANSACTION)) {
            stmt.setString(1, transactionId);
            stmt.setInt(2, request.getAccountId());
            stmt.setString(3, walletAddress);
            stmt.setBigDecimal(4, request.getAmount());
            stmt.setString(5, TransactionType.PAYMENT.getValue());
            stmt.setString(6, TransactionStatus.PENDING.getValue());
            stmt.setString(7, request.getPurpose());
            stmt.setString(8, metadataJson);
            stmt.setObject(9, now);
            stmt.setObject(10, now);
            stmt.setObject(11, expiresAt);
            stmt.setInt(12, 0);
            stmt.executeUpdate();
        }

        // Insert initial status
        insertStatus(conn, transactionId, TransactionStatus.PENDING, "Payment created");

        logger.info("Created transaction {} for account {}, amount: {}", transactionId, request.getAccountId(), request.getAmount());
        return transactionId;
    }

    public Optional<BitcoinTransaction> getTransactionById(Connection conn, String transactionId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(GET_TRANSACTION_BY_ID)) {
            stmt.setString(1, transactionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapTransactionFromResultSet(rs));
            }
        }
        return Optional.empty();
    }

    public List<BitcoinTransaction> getTransactionsByAccount(Connection conn, Integer accountId, int offset, int limit) throws SQLException {
        List<BitcoinTransaction> transactions = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(GET_TRANSACTIONS_BY_ACCOUNT)) {
            stmt.setInt(1, accountId);
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(mapTransactionFromResultSet(rs));
            }
        }
        return transactions;
    }

    public int countTransactionsByAccount(Connection conn, Integer accountId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(COUNT_TRANSACTIONS_BY_ACCOUNT)) {
            stmt.setInt(1, accountId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<BitcoinTransaction> getPendingTransactions(Connection conn) throws SQLException {
        List<BitcoinTransaction> transactions = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(GET_PENDING_TRANSACTIONS)) {
            stmt.setObject(1, OffsetDateTime.now());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                transactions.add(mapTransactionFromResultSet(rs));
            }
        }
        return transactions;
    }

    public void updateTransactionStatus(Connection conn, String transactionId, TransactionStatus status, 
                                       int confirmations, String transactionHash, String details) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime confirmedAt = (status == TransactionStatus.CONFIRMED) ? now : null;

        try (PreparedStatement stmt = conn.prepareStatement(UPDATE_TRANSACTION_STATUS)) {
            stmt.setString(1, status.getValue());
            stmt.setInt(2, confirmations);
            stmt.setObject(3, now);
            stmt.setString(4, transactionHash);
            stmt.setObject(5, confirmedAt);
            stmt.setString(6, transactionId);
            stmt.executeUpdate();
        }

        // Insert status history
        insertStatus(conn, transactionId, status, details);

        logger.info("Updated transaction {} to status {}, confirmations: {}", transactionId, status, confirmations);
    }

    public void insertStatus(Connection conn, String transactionId, TransactionStatus status, String details) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_STATUS)) {
            stmt.setString(1, transactionId);
            stmt.setString(2, status.getValue());
            stmt.setObject(3, OffsetDateTime.now());
            stmt.setString(4, details);
            stmt.executeUpdate();
        }
    }

    // Helper methods to map ResultSet to objects
    private BitcoinWallet mapWalletFromResultSet(ResultSet rs) throws SQLException {
        return BitcoinWallet.builder()
                .id(rs.getString("Id"))
                .accountId(rs.getInt("AccountId"))
                .walletAddress(rs.getString("WalletAddress"))
                .balance(rs.getBigDecimal("Balance"))
                .createdAt(rs.getObject("CreatedAt", OffsetDateTime.class))
                .updatedAt(rs.getObject("UpdatedAt", OffsetDateTime.class))
                .build();
    }

    private BitcoinTransaction mapTransactionFromResultSet(ResultSet rs) throws SQLException {
        return BitcoinTransaction.builder()
                .id(rs.getString("Id"))
                .accountId(rs.getInt("AccountId"))
                .walletAddress(rs.getString("WalletAddress"))
                .transactionHash(rs.getString("TransactionHash"))
                .amount(rs.getBigDecimal("Amount"))
                .type(TransactionType.valueOf(rs.getString("Type")))
                .status(TransactionStatus.valueOf(rs.getString("Status")))
                .confirmations(rs.getInt("Confirmations"))
                .purpose(rs.getString("Purpose"))
                .metadata(rs.getString("Metadata"))
                .createdAt(rs.getObject("CreatedAt", OffsetDateTime.class))
                .updatedAt(rs.getObject("UpdatedAt", OffsetDateTime.class))
                .confirmedAt(rs.getObject("ConfirmedAt", OffsetDateTime.class))
                .expiresAt(rs.getObject("ExpiresAt", OffsetDateTime.class))
                .build();
    }
}
