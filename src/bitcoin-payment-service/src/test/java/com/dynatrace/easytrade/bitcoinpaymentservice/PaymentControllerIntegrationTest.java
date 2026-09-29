package com.dynatrace.easytrade.bitcoinpaymentservice;

import com.dynatrace.easytrade.bitcoinpaymentservice.models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PaymentController Integration Tests")
public class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DatabaseHelper databaseHelper;

    @MockBean
    private WalletService walletService;

    @Test
    @DisplayName("POST /v1/payments - ValidRequest - ReturnsCreatedPayment")
    void createPayment_ValidRequest_ReturnsCreatedPayment() throws Exception {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAccountId("ACC-001");
        request.setAmount(0.001);
        request.setCurrency("BTC");

        BitcoinWallet wallet = new BitcoinWallet();
        wallet.setWalletId("wallet-123");
        wallet.setAccountId("ACC-001");
        wallet.setWalletAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        wallet.setBalance(0.0);
        wallet.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        BitcoinTransaction transaction = new BitcoinTransaction();
        transaction.setTransactionId("txn-123");
        transaction.setWalletId("wallet-123");
        transaction.setTransactionHash("generated-hash-abc");
        transaction.setAmount(0.001);
        transaction.setTransactionType(TransactionType.PAYMENT);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setConfirmations(0);
        transaction.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        when(walletService.getOrCreateWallet("ACC-001")).thenReturn(wallet);
        when(walletService.generateQRCodeData(anyString(), anyDouble()))
                .thenReturn("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.001");
        when(databaseHelper.createTransaction(any(BitcoinTransaction.class))).thenReturn(transaction);

        // Act & Assert
        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId", is("txn-123")))
                .andExpect(jsonPath("$.accountId", is("ACC-001")))
                .andExpect(jsonPath("$.amount", is(0.001)))
                .andExpect(jsonPath("$.currency", is("BTC")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.walletAddress", is("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")))
                .andExpect(jsonPath("$.qrCodeData", is("bitcoin:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh?amount=0.001")))
                .andExpect(jsonPath("$.confirmations", is(0)));

        verify(walletService, times(1)).getOrCreateWallet("ACC-001");
        verify(databaseHelper, times(1)).createTransaction(any(BitcoinTransaction.class));
    }

    @Test
    @DisplayName("POST /v1/payments - MissingAccountId - ReturnsBadRequest")
    void createPayment_MissingAccountId_ReturnsBadRequest() throws Exception {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(0.001);
        request.setCurrency("BTC");
        // accountId is null

        // Act & Assert
        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).getOrCreateWallet(anyString());
        verify(databaseHelper, never()).createTransaction(any(BitcoinTransaction.class));
    }

    @Test
    @DisplayName("POST /v1/payments - NegativeAmount - ReturnsBadRequest")
    void createPayment_NegativeAmount_ReturnsBadRequest() throws Exception {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAccountId("ACC-001");
        request.setAmount(-0.001);
        request.setCurrency("BTC");

        // Act & Assert
        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).getOrCreateWallet(anyString());
    }

    @Test
    @DisplayName("GET /v1/payments/{id} - ExistingPayment - ReturnsPayment")
    void getPayment_ExistingPayment_ReturnsPayment() throws Exception {
        // Arrange
        BitcoinTransaction transaction = new BitcoinTransaction();
        transaction.setTransactionId("txn-123");
        transaction.setWalletId("wallet-123");
        transaction.setTransactionHash("hash-abc");
        transaction.setAmount(0.001);
        transaction.setTransactionType(TransactionType.PAYMENT);
        transaction.setStatus(PaymentStatus.CONFIRMED);
        transaction.setConfirmations(6);
        transaction.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        BitcoinWallet wallet = new BitcoinWallet();
        wallet.setWalletId("wallet-123");
        wallet.setAccountId("ACC-001");
        wallet.setWalletAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");

        when(databaseHelper.getTransactionById("txn-123")).thenReturn(transaction);
        when(databaseHelper.getWalletById("wallet-123")).thenReturn(wallet);

        // Act & Assert
        mockMvc.perform(get("/v1/payments/txn-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId", is("txn-123")))
                .andExpect(jsonPath("$.accountId", is("ACC-001")))
                .andExpect(jsonPath("$.amount", is(0.001)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.confirmations", is(6)));

        verify(databaseHelper, times(1)).getTransactionById("txn-123");
        verify(databaseHelper, times(1)).getWalletById("wallet-123");
    }

    @Test
    @DisplayName("GET /v1/payments/{id} - NonExistentPayment - ReturnsNotFound")
    void getPayment_NonExistentPayment_ReturnsNotFound() throws Exception {
        // Arrange
        when(databaseHelper.getTransactionById("txn-999")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/v1/payments/txn-999"))
                .andExpect(status().isNotFound());

        verify(databaseHelper, times(1)).getTransactionById("txn-999");
        verify(databaseHelper, never()).getWalletById(anyString());
    }

    @Test
    @DisplayName("GET /v1/wallets/{accountId} - ExistingWallet - ReturnsWallet")
    void getWallet_ExistingWallet_ReturnsWallet() throws Exception {
        // Arrange
        BitcoinWallet wallet = new BitcoinWallet();
        wallet.setWalletId("wallet-123");
        wallet.setAccountId("ACC-001");
        wallet.setWalletAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        wallet.setBalance(0.05);
        wallet.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        when(databaseHelper.getWalletByAccountId("ACC-001")).thenReturn(wallet);

        // Act & Assert
        mockMvc.perform(get("/v1/wallets/ACC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId", is("wallet-123")))
                .andExpect(jsonPath("$.accountId", is("ACC-001")))
                .andExpect(jsonPath("$.walletAddress", is("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh")))
                .andExpect(jsonPath("$.balance", is(0.05)));

        verify(databaseHelper, times(1)).getWalletByAccountId("ACC-001");
    }

    @Test
    @DisplayName("GET /v1/wallets/{accountId} - NonExistentWallet - ReturnsNotFound")
    void getWallet_NonExistentWallet_ReturnsNotFound() throws Exception {
        // Arrange
        when(databaseHelper.getWalletByAccountId("ACC-999")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/v1/wallets/ACC-999"))
                .andExpect(status().isNotFound());

        verify(databaseHelper, times(1)).getWalletByAccountId("ACC-999");
    }

    @Test
    @DisplayName("GET /v1/transactions - WithWalletId - ReturnsTransactionsList")
    void getTransactions_WithWalletId_ReturnsTransactionsList() throws Exception {
        // Arrange
        BitcoinTransaction tx1 = new BitcoinTransaction();
        tx1.setTransactionId("txn-1");
        tx1.setWalletId("wallet-123");
        tx1.setAmount(0.001);
        tx1.setStatus(PaymentStatus.PENDING);
        tx1.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        BitcoinTransaction tx2 = new BitcoinTransaction();
        tx2.setTransactionId("txn-2");
        tx2.setWalletId("wallet-123");
        tx2.setAmount(0.002);
        tx2.setStatus(PaymentStatus.CONFIRMED);
        tx2.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        List<BitcoinTransaction> transactions = Arrays.asList(tx1, tx2);

        when(databaseHelper.getTransactionsByWalletId("wallet-123")).thenReturn(transactions);

        // Act & Assert
        mockMvc.perform(get("/v1/transactions")
                .param("walletId", "wallet-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].transactionId", is("txn-1")))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[1].transactionId", is("txn-2")))
                .andExpect(jsonPath("$[1].status", is("CONFIRMED")));

        verify(databaseHelper, times(1)).getTransactionsByWalletId("wallet-123");
    }

    @Test
    @DisplayName("POST /v1/payments - DatabaseError - ReturnsInternalServerError")
    void createPayment_DatabaseError_ReturnsInternalServerError() throws Exception {
        // Arrange
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAccountId("ACC-001");
        request.setAmount(0.001);
        request.setCurrency("BTC");

        when(walletService.getOrCreateWallet("ACC-001"))
                .thenThrow(new SQLException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(post("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(walletService, times(1)).getOrCreateWallet("ACC-001");
        verify(databaseHelper, never()).createTransaction(any(BitcoinTransaction.class));
    }
}
