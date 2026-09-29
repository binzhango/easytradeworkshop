# Bitcoin Payment Integration for Broker Service

This document describes how to integrate Bitcoin payment functionality into the broker-service.

## Overview

The Bitcoin payment integration allows users to purchase stocks using Bitcoin cryptocurrency. The integration communicates with the `bitcoin-payment-service` microservice to handle wallet management, payment creation, and transaction tracking.

## Architecture

```
broker-service → bitcoin-payment-service → Database (BitcoinWallets, BitcoinTransactions)
                                        → Blockchain API (for confirmations)
```

## Files Added

### DTOs (Data Transfer Objects)
- `src/Entities/BitcoinPayments/DTO/CreateBitcoinPaymentDTO.cs` - Request model for creating payments
- `src/Entities/BitcoinPayments/DTO/BitcoinPaymentResponse.cs` - Payment response model
- `src/Entities/BitcoinPayments/DTO/BitcoinWalletResponse.cs` - Wallet response model

### Services
- `src/Entities/BitcoinPayments/Service/IBitcoinPaymentService.cs` - Service interface
- `src/Entities/BitcoinPayments/Service/BitcoinPaymentService.cs` - Service implementation

### Controllers
- `src/Entities/BitcoinPayments/Controllers/BitcoinPaymentController.cs` - API endpoints

## Configuration Required

### 1. Register Services in Program.cs or Startup.cs

Add the following to your service registration:

```csharp
// Add HTTP client factory
builder.Services.AddHttpClient("BitcoinPaymentService")
    .ConfigureHttpClient(client =>
    {
        client.Timeout = TimeSpan.FromSeconds(30);
    })
    .AddTransientHttpErrorPolicy(policy => 
        policy.WaitAndRetryAsync(3, retryAttempt => 
            TimeSpan.FromSeconds(Math.Pow(2, retryAttempt))))
    .AddTransientHttpErrorPolicy(policy =>
        policy.CircuitBreakerAsync(5, TimeSpan.FromSeconds(30)));

// Register Bitcoin payment service
builder.Services.AddScoped<IBitcoinPaymentService, BitcoinPaymentService>();
```

### 2. Add Configuration Settings

Add to `appsettings.json`:

```json
{
  "BitcoinPaymentService": {
    "Url": "http://bitcoin-payment-service:8080"
  }
}
```

### 3. Environment Variables

Set the following environment variable (optional, overrides appsettings.json):

```bash
BITCOIN_PAYMENT_SERVICE_URL=http://bitcoin-payment-service:8080
```

In Kubernetes, this is set in the deployment manifest.

### 4. Add Required NuGet Packages

If not already present, add:

```bash
dotnet add package Microsoft.Extensions.Http.Polly
```

This provides resilience patterns (retry, circuit breaker) for HTTP calls.

## API Endpoints

### Create Bitcoin Payment
**POST** `/v1/bitcoin/{accountId}/payment`

Creates a new Bitcoin payment request for purchasing stocks.

**Request:**
```json
{
  "amount": 0.005,
  "instrumentId": 1,
  "purpose": "stock_purchase"
}
```

**Response (201 Created):**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
  "amount": 0.005,
  "currency": "BTC",
  "status": "PENDING",
  "expiresAt": "2026-09-30T20:00:00Z",
  "qrCode": "data:image/png;base64,...",
  "confirmations": 0,
  "createdAt": "2026-09-29T20:00:00Z"
}
```

### Get Payment Status
**GET** `/v1/bitcoin/payment/{paymentId}`

Retrieves the current status of a Bitcoin payment.

**Response (200 OK):**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
  "amount": 0.005,
  "currency": "BTC",
  "status": "CONFIRMED",
  "confirmations": 3,
  "transactionHash": "a1b2c3d4e5f6...",
  "createdAt": "2026-09-29T20:00:00Z",
  "confirmedAt": "2026-09-29T20:15:00Z"
}
```

### Get Wallet
**GET** `/v1/bitcoin/{accountId}/wallet`

Retrieves the Bitcoin wallet for an account.

**Response (200 OK):**
```json
{
  "accountId": 6,
  "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
  "balance": 0.123,
  "createdAt": "2026-09-29T10:00:00Z"
}
```

### Check Payment Confirmation
**GET** `/v1/bitcoin/payment/{paymentId}/confirmed`

Checks if a payment has been confirmed on the blockchain.

**Response (200 OK):**
```json
true
```

## Error Handling

The service handles the following error scenarios:

### 404 Not Found
- Payment ID doesn't exist
- Wallet doesn't exist for account
- Account doesn't exist

### 400 Bad Request
- Invalid amount (must be positive)
- Invalid currency (must be "BTC")
- Missing required fields

### 502 Bad Gateway
- Bitcoin payment service is unavailable
- Network timeout communicating with service
- Invalid response from service

## Payment Flow

1. **User initiates Bitcoin payment**
   - Frontend calls `POST /v1/bitcoin/{accountId}/payment`
   - Broker service forwards request to bitcoin-payment-service

2. **Payment creation**
   - Bitcoin service generates/retrieves wallet for account
   - Creates transaction record with status "PENDING"
   - Returns wallet address and QR code

3. **User sends Bitcoin**
   - User scans QR code or copies wallet address
   - Sends Bitcoin from their wallet
   - Transaction appears on blockchain

4. **Confirmation** (async)
   - Bitcoin confirmation worker polls blockchain
   - Updates transaction status: PENDING → CONFIRMING → CONFIRMED
   - Typically takes 10-30 minutes for 3 confirmations

5. **Stock purchase**
   - After confirmation, frontend can complete stock purchase
   - Check confirmation via `GET /v1/bitcoin/payment/{paymentId}/confirmed`

## Security Considerations

1. **Service-to-Service Authentication**
   - Currently uses direct HTTP calls
   - In production, add JWT or mTLS between services

2. **Input Validation**
   - All amounts validated as positive
   - Account IDs validated
   - Payment IDs validated as GUIDs

3. **Error Information**
   - Errors logged but sensitive details not exposed to client
   - External service errors mapped to generic messages

## Resilience Patterns

### Retry Policy
- 3 retries with exponential backoff (2^n seconds)
- Handles transient HTTP errors (5xx, timeouts)

### Circuit Breaker
- Opens after 5 consecutive failures
- Stays open for 30 seconds
- Prevents cascading failures

### Timeout
- 30 second timeout per request
- Prevents hanging requests

## Monitoring

### Metrics to Track
- Bitcoin payment creation rate
- Payment confirmation rate
- Average confirmation time
- Bitcoin service availability
- Error rates by type

### Logging
- All payment operations logged with account ID and payment ID
- Errors logged with full exception details
- HTTP requests/responses logged (excluding sensitive data)

### Health Checks
Add to health check endpoint:

```csharp
builder.Services.AddHealthChecks()
    .AddUrlGroup(new Uri($"{bitcoinServiceUrl}/actuator/health"), 
        name: "bitcoin-payment-service");
```

## Testing

### Unit Tests
Test the `BitcoinPaymentService` class:
- Mock `IHttpClientFactory`
- Test successful responses
- Test error handling (404, 502)
- Test retry logic

### Integration Tests
- Test full flow from controller to service
- Use test instance of bitcoin-payment-service
- Verify responses match expected format

### Load Tests
- Test under 100x baseline load (650 req/min)
- Verify circuit breaker behavior under failure
- Check retry backoff under transient errors

## Deployment

### Kubernetes
The bitcoin-payment-service must be deployed first:

```bash
kubectl apply -f kubernetes-manifests/bitcoin-payment-service/
```

Then deploy updated broker-service:

```bash
kubectl apply -f kubernetes-manifests/broker-service/
```

### Docker Compose
Add to `compose.yaml`:

```yaml
bitcoin-payment-service:
  image: easytrade/bitcoin-payment-service:latest
  environment:
    - MSSQL_CONNECTIONSTRING=...
    - REDIS_HOST=redis
    - RABBITMQ_HOST=rabbitmq
  ports:
    - "8083:8080"
  depends_on:
    - db
    - redis
    - rabbitmq

broker-service:
  environment:
    - BITCOIN_PAYMENT_SERVICE_URL=http://bitcoin-payment-service:8080
  depends_on:
    - bitcoin-payment-service
```

## Troubleshooting

### Payment creation fails with 502
**Cause:** Bitcoin payment service is not running or unreachable

**Solution:**
1. Check if service is running: `kubectl get pods | grep bitcoin-payment`
2. Check logs: `kubectl logs deployment/bitcoin-payment-service`
3. Verify network connectivity: `kubectl exec -it broker-service-pod -- curl http://bitcoin-payment-service:8080/actuator/health`

### Payments stuck in PENDING status
**Cause:** Confirmation worker not running or blockchain API issues

**Solution:**
1. Check confirmation worker logs
2. Verify blockchain API credentials
3. Check if transactions are on blockchain (use block explorer)

### High latency on payment operations
**Cause:** Bitcoin service overloaded or database slow

**Solution:**
1. Check bitcoin-payment-service metrics
2. Verify HPA is scaling properly
3. Check database connection pool utilization
4. Verify Redis cache is working

## Migration Path

### Phase 1: Deploy Infrastructure
1. Deploy database schema (BitcoinWallets, BitcoinTransactions tables)
2. Deploy Redis for caching
3. Deploy RabbitMQ for async processing

### Phase 2: Deploy Services
1. Deploy bitcoin-payment-service
2. Update broker-service with Bitcoin integration
3. Verify connectivity between services

### Phase 3: Frontend Integration
1. Add Bitcoin payment UI to frontend
2. Test end-to-end flow
3. Enable feature flag for beta users

### Phase 4: Full Rollout
1. Monitor metrics and error rates
2. Gradually increase traffic via feature flag
3. Enable for all users

## Related Documentation

- Bitcoin Payment Service: `src/bitcoin-payment-service/README.md`
- Database Schema: `src/db/sql-scripts/BITCOIN_SCHEMA.md`
- Architecture: `docs/BITCOIN_ARCHITECTURE.md`
- API Documentation: `http://localhost/broker-service/swagger`

## Support

For issues or questions:
- Check logs: `kubectl logs -f deployment/broker-service | grep Bitcoin`
- Check metrics in Dynatrace
- Review runbook: `docs/BITCOIN_RUNBOOK.md`
