# Bitcoin Payment Database Schema

This document describes the database schema for Bitcoin payment functionality in EasyTrade.

## Overview

The Bitcoin payment schema consists of three main tables:
1. **BitcoinWallets** - Stores user Bitcoin wallet information
2. **BitcoinTransactions** - Records all Bitcoin transactions
3. **BitcoinPaymentStatus** - Tracks status history for transactions

## Tables

### BitcoinWallets

Stores one wallet per account for receiving Bitcoin payments.

| Column | Type | Description |
|--------|------|-------------|
| Id | nvarchar(36) | Primary key (GUID) |
| AccountId | INT | Foreign key to Accounts table (UNIQUE) |
| WalletAddress | nvarchar(100) | Bitcoin address (UNIQUE) |
| Balance | DECIMAL(18, 8) | Current balance in BTC |
| CreatedAt | datetimeoffset(0) | Wallet creation timestamp |
| UpdatedAt | datetimeoffset(0) | Last update timestamp |

**Indexes:**
- IX_BitcoinWallets_AccountId (for fast account lookups)
- IX_BitcoinWallets_WalletAddress (for incoming transaction matching)

**Constraints:**
- One wallet per account (UNIQUE on AccountId)
- One account per wallet address (UNIQUE on WalletAddress)

### BitcoinTransactions

Records all Bitcoin payment transactions, including pending, confirmed, and failed payments.

| Column | Type | Description |
|--------|------|-------------|
| Id | nvarchar(36) | Primary key (GUID) |
| AccountId | INT | Foreign key to Accounts table |
| WalletAddress | nvarchar(100) | Bitcoin address involved |
| TransactionHash | nvarchar(100) | Blockchain transaction hash (UNIQUE) |
| Amount | DECIMAL(18, 8) | Transaction amount in BTC |
| Type | VARCHAR(20) | PAYMENT, REFUND, WITHDRAWAL, DEPOSIT |
| Status | VARCHAR(20) | PENDING, CONFIRMING, CONFIRMED, FAILED, EXPIRED |
| Confirmations | INT | Number of blockchain confirmations (default 0) |
| Purpose | VARCHAR(50) | Transaction purpose (e.g., stock_purchase) |
| Metadata | nvarchar(MAX) | JSON metadata for additional context |
| CreatedAt | datetimeoffset(0) | Transaction creation timestamp |
| UpdatedAt | datetimeoffset(0) | Last update timestamp |
| ConfirmedAt | datetimeoffset(0) | Confirmation timestamp (NULL until confirmed) |
| ExpiresAt | datetimeoffset(0) | Payment expiration timestamp |

**Indexes:**
- IX_BitcoinTransactions_AccountId (for user transaction history)
- IX_BitcoinTransactions_WalletAddress (for wallet-based queries)
- IX_BitcoinTransactions_TransactionHash (for blockchain sync)
- IX_BitcoinTransactions_Status (for pending transaction queries)
- IX_BitcoinTransactions_CreatedAt (for time-based queries)
- IX_BitcoinTransactions_Type_Status (composite for filtered queries)

**Transaction Types:**
- **PAYMENT**: User paying for something (e.g., stock purchase)
- **DEPOSIT**: User depositing BTC to their wallet
- **REFUND**: Refund of a previous payment
- **WITHDRAWAL**: User withdrawing BTC from their wallet

**Transaction Status:**
- **PENDING**: Transaction created, awaiting blockchain confirmation
- **CONFIRMING**: Transaction seen on blockchain, accumulating confirmations
- **CONFIRMED**: Transaction has required confirmations (typically 3)
- **FAILED**: Transaction failed or rejected
- **EXPIRED**: Payment expired before being completed

### BitcoinPaymentStatus

Tracks the status history of each transaction, similar to CreditCardOrderStatus.

| Column | Type | Description |
|--------|------|-------------|
| Id | INT IDENTITY | Primary key (auto-increment) |
| TransactionId | nvarchar(36) | Foreign key to BitcoinTransactions |
| Status | VARCHAR(20) | PENDING, CONFIRMING, CONFIRMED, FAILED, EXPIRED |
| Timestamp | datetimeoffset(0) | When status change occurred |
| Details | nvarchar(500) | Additional details about status change |

**Indexes:**
- IX_BitcoinPaymentStatus_TransactionId (for transaction status history)
- IX_BitcoinPaymentStatus_Timestamp (for time-based queries)
- IX_BitcoinPaymentStatus_Status (for status analysis)

## Relationships

```
Accounts (existing)
    ├── BitcoinWallets (1:1)
    │       └── references AccountId
    │
    └── BitcoinTransactions (1:many)
            ├── references AccountId
            └── BitcoinPaymentStatus (1:many)
                    └── references TransactionId
```

## Query Patterns

### Get wallet for account
```sql
SELECT * FROM BitcoinWallets 
WHERE AccountId = @accountId;
```

### Get pending transactions requiring confirmation
```sql
SELECT * FROM BitcoinTransactions 
WHERE Status IN ('PENDING', 'CONFIRMING')
  AND ExpiresAt > SYSDATETIMEOFFSET()
ORDER BY CreatedAt ASC;
```

### Get transaction history for account
```sql
SELECT * FROM BitcoinTransactions 
WHERE AccountId = @accountId
ORDER BY CreatedAt DESC
OFFSET @offset ROWS
FETCH NEXT @limit ROWS ONLY;
```

### Get transaction status history
```sql
SELECT s.* FROM BitcoinPaymentStatus s
WHERE s.TransactionId = @transactionId
ORDER BY s.Timestamp DESC;
```

### Update transaction status
```sql
BEGIN TRANSACTION;

UPDATE BitcoinTransactions 
SET Status = @newStatus,
    Confirmations = @confirmations,
    UpdatedAt = SYSDATETIMEOFFSET(),
    ConfirmedAt = CASE WHEN @newStatus = 'CONFIRMED' THEN SYSDATETIMEOFFSET() ELSE ConfirmedAt END
WHERE Id = @transactionId;

INSERT INTO BitcoinPaymentStatus (TransactionId, Status, Timestamp, Details)
VALUES (@transactionId, @newStatus, SYSDATETIMEOFFSET(), @details);

COMMIT TRANSACTION;
```

## Performance Considerations

### Indexing Strategy
- All frequently queried columns have indexes
- Composite index on (Type, Status) supports common filtered queries
- Transaction history queries use CreatedAt index for pagination

### Partitioning (Future Enhancement)
For high-volume scenarios, consider partitioning BitcoinTransactions by CreatedAt:
- Monthly or quarterly partitions
- Archive old confirmed transactions
- Improves query performance on recent data

### Caching Recommendations
Based on architecture document:
- Wallet addresses: Cache for 5 minutes (Redis key: `btc:wallet:{accountId}`)
- Transaction status: Cache for 1 minute for CONFIRMED, no cache for PENDING
- Balance queries: Cache for 5 minutes, invalidate on new transaction

## Migration from Existing System

Since this is a new feature, no migration is needed. However, coordinate deployment:

1. Deploy database schema (these SQL scripts)
2. Deploy bitcoin-payment-service with migration checks
3. Service should handle missing tables gracefully during rollout

## Security Considerations

### Data Protection
- **Wallet addresses**: Not PII but should be treated as sensitive
- **Transaction hashes**: Public blockchain data, but linking to accounts is sensitive
- **Metadata**: May contain PII, encrypt at application layer if needed

### Access Control
- Only bitcoin-payment-service should have direct write access
- Read access can be granted to reporting/analytics services
- Implement row-level security if multiple services read data

## Monitoring Queries

### Check for stuck transactions
```sql
SELECT COUNT(*) as StuckCount
FROM BitcoinTransactions
WHERE Status = 'CONFIRMING'
  AND UpdatedAt < DATEADD(HOUR, -1, SYSDATETIMEOFFSET());
```

### Daily transaction volume
```sql
SELECT 
    Type,
    Status,
    COUNT(*) as Count,
    SUM(Amount) as TotalBTC
FROM BitcoinTransactions
WHERE CreatedAt >= DATEADD(DAY, -1, SYSDATETIMEOFFSET())
GROUP BY Type, Status;
```

### Average confirmation time
```sql
SELECT 
    AVG(DATEDIFF(SECOND, CreatedAt, ConfirmedAt)) as AvgConfirmationSeconds
FROM BitcoinTransactions
WHERE Status = 'CONFIRMED'
  AND CreatedAt >= DATEADD(DAY, -7, SYSDATETIMEOFFSET());
```

## Testing

### Test Data Creation
```sql
-- Create test wallet
INSERT INTO BitcoinWallets (Id, AccountId, WalletAddress, Balance)
VALUES (NEWID(), 1, 'bc1qtest123456789', 0.00000000);

-- Create test transaction
INSERT INTO BitcoinTransactions (Id, AccountId, WalletAddress, TransactionHash, Amount, Type, Status, Purpose)
VALUES (NEWID(), 1, 'bc1qtest123456789', '0000000000000000000000000000000000000000000000000000000000000000', 0.005, 'PAYMENT', 'PENDING', 'stock_purchase');
```

### Cleanup Test Data
```sql
DELETE FROM BitcoinPaymentStatus WHERE TransactionId IN (
    SELECT Id FROM BitcoinTransactions WHERE WalletAddress LIKE '%test%'
);
DELETE FROM BitcoinTransactions WHERE WalletAddress LIKE '%test%';
DELETE FROM BitcoinWallets WHERE WalletAddress LIKE '%test%';
```

## References

- Architecture Document: `docs/BITCOIN_ARCHITECTURE.md`
- EasyTrade Database ERD: `img/database.jpg`
- SQL Server Documentation: https://docs.microsoft.com/en-us/sql/
