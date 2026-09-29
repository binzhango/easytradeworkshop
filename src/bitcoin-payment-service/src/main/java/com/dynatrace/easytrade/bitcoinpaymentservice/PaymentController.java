package com.dynatrace.easytrade.bitcoinpaymentservice;

import com.dynatrace.easytrade.bitcoinpaymentservice.models.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(value = "/v1", produces = "application/json")
@CrossOrigin
@Validated
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success", content = 
                @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad request", content =
                @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not found", content =
                @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = 
                @Content(schema = @Schema(implementation = StandardResponse.class))),
})
public class PaymentController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    
    private final DatabaseHelper dbHelper;
    private final WalletService walletService;

    public PaymentController(DatabaseHelper dbHelper, WalletService walletService) {
        this.dbHelper = dbHelper;
        this.walletService = walletService;
    }

    @PostMapping("/payments")
    @Operation(summary = "Create a new Bitcoin payment")
    public ResponseEntity<StandardResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        logger.info("Creating Bitcoin payment for account: {}, amount: {}", 
            request.getAccountId(), request.getAmount());

        if (!"BTC".equalsIgnoreCase(request.getCurrency())) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, 
                "Only BTC currency is supported", request);
        }

        try (Connection conn = dbHelper.getConnection()) {
            // Get or create wallet for account
            Optional<BitcoinWallet> walletOpt = dbHelper.getWalletByAccount(conn, request.getAccountId());
            BitcoinWallet wallet;
            
            if (walletOpt.isEmpty()) {
                // Generate new wallet address
                String walletAddress = walletService.generateWalletAddress(request.getAccountId());
                dbHelper.createWallet(conn, request.getAccountId(), walletAddress);
                wallet = dbHelper.getWalletByAccount(conn, request.getAccountId()).orElseThrow();
            } else {
                wallet = walletOpt.get();
            }

            // Create transaction
            String transactionId = dbHelper.createTransaction(conn, request, wallet.getWalletAddress());
            Optional<BitcoinTransaction> transaction = dbHelper.getTransactionById(conn, transactionId);

            if (transaction.isEmpty()) {
                return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create transaction", null);
            }

            // Generate QR code
            String qrCode = walletService.generateQRCode(wallet.getWalletAddress(), request.getAmount());

            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(transaction.get().getId())
                    .walletAddress(wallet.getWalletAddress())
                    .amount(transaction.get().getAmount())
                    .currency("BTC")
                    .status(transaction.get().getStatus())
                    .expiresAt(transaction.get().getExpiresAt())
                    .qrCode(qrCode)
                    .confirmations(0)
                    .createdAt(transaction.get().getCreatedAt())
                    .build();

            return buildSuccessResponse(HttpStatus.CREATED, "Payment created successfully", response);

        } catch (SQLException e) {
            logger.error("Database error creating payment", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Database error: " + e.getMessage(), null);
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error creating payment: " + e.getMessage(), null);
        }
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<StandardResponse> getPayment(@PathVariable String paymentId) {
        logger.info("Getting payment status for: {}", paymentId);

        try (Connection conn = dbHelper.getConnection()) {
            Optional<BitcoinTransaction> transaction = dbHelper.getTransactionById(conn, paymentId);

            if (transaction.isEmpty()) {
                return buildErrorResponse(HttpStatus.NOT_FOUND, 
                    "Payment not found: " + paymentId, null);
            }

            BitcoinTransaction tx = transaction.get();
            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(tx.getId())
                    .walletAddress(tx.getWalletAddress())
                    .amount(tx.getAmount())
                    .currency("BTC")
                    .status(tx.getStatus())
                    .confirmations(tx.getConfirmations())
                    .transactionHash(tx.getTransactionHash())
                    .createdAt(tx.getCreatedAt())
                    .confirmedAt(tx.getConfirmedAt())
                    .expiresAt(tx.getExpiresAt())
                    .build();

            return buildSuccessResponse(HttpStatus.OK, "Payment found", response);

        } catch (SQLException e) {
            logger.error("Database error getting payment", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Database error: " + e.getMessage(), null);
        }
    }

    @GetMapping("/wallets/{accountId}")
    @Operation(summary = "Get wallet for account")
    @Cacheable(value = "wallets", key = "#accountId")
    public ResponseEntity<StandardResponse> getWallet(@PathVariable Integer accountId) {
        logger.info("Getting wallet for account: {}", accountId);

        try (Connection conn = dbHelper.getConnection()) {
            Optional<BitcoinWallet> wallet = dbHelper.getWalletByAccount(conn, accountId);

            if (wallet.isEmpty()) {
                return buildErrorResponse(HttpStatus.NOT_FOUND, 
                    "Wallet not found for account: " + accountId, null);
            }

            BitcoinWallet w = wallet.get();
            WalletResponse response = WalletResponse.builder()
                    .accountId(w.getAccountId())
                    .walletAddress(w.getWalletAddress())
                    .balance(w.getBalance())
                    .createdAt(w.getCreatedAt())
                    .build();

            return buildSuccessResponse(HttpStatus.OK, "Wallet found", response);

        } catch (SQLException e) {
            logger.error("Database error getting wallet", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Database error: " + e.getMessage(), null);
        }
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history for account")
    public ResponseEntity<StandardResponse> getTransactions(
            @RequestParam Integer accountId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        
        logger.info("Getting transactions for account: {}, offset: {}, limit: {}", 
            accountId, offset, limit);

        if (limit > 100) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, 
                "Limit cannot exceed 100", null);
        }

        try (Connection conn = dbHelper.getConnection()) {
            List<BitcoinTransaction> transactions = dbHelper.getTransactionsByAccount(conn, accountId, offset, limit);
            int total = dbHelper.countTransactionsByAccount(conn, accountId);

            TransactionListResponse response = TransactionListResponse.builder()
                    .transactions(transactions)
                    .total(total)
                    .offset(offset)
                    .limit(limit)
                    .build();

            return buildSuccessResponse(HttpStatus.OK, "Transactions retrieved", response);

        } catch (SQLException e) {
            logger.error("Database error getting transactions", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Database error: " + e.getMessage(), null);
        }
    }

    // Helper methods
    private ResponseEntity<StandardResponse> buildSuccessResponse(HttpStatus status, String message, Object results) {
        logger.info(message);
        StandardResponse response = StandardResponse.builder()
                .status(status.value())
                .message(message)
                .results(results)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<StandardResponse> buildErrorResponse(HttpStatus status, String message, Object data) {
        logger.error(message);
        StandardResponse response = StandardResponse.builder()
                .status(status.value())
                .message(message)
                .data(data)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
