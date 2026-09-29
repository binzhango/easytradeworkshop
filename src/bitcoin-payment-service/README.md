# Bitcoin Payment Service

A Java Spring Boot microservice for processing Bitcoin payments in the EasyTrade application.

## Overview

This service handles Bitcoin payment operations including:
- Creating Bitcoin payment requests
- Managing user wallets
- Tracking payment status and blockchain confirmations
- Transaction history

## Technologies

- Java 21
- Spring Boot 4.0.7
- SQL Server (for transaction storage)
- Redis (for caching)
- RabbitMQ (for async processing)
- Resilience4j (for circuit breaker, retry, rate limiting)
- OpenFeature (for feature flags)

## Architecture

The service follows a scalable architecture designed to handle 100x traffic:

### Key Features
- **Horizontal Scaling**: HPA configuration (2-15 replicas)
- **Caching**: Redis caching with 5-minute TTL for wallet queries
- **Resilience**: Circuit breaker and retry logic for external API calls
- **Async Processing**: RabbitMQ integration for confirmation workers
- **Connection Pooling**: Hikari CP with 20 connections per pod
- **Observability**: Full OpenTelemetry instrumentation ready

### Components Under Most Stress at 100x Scale
1. **Database**: Mitigated with connection pooling and caching
2. **Feature Flag Service**: Cached locally with circuit breaker
3. **Blockchain API**: Protected with rate limiter and circuit breaker

## API Endpoints

### Create Payment
```bash
POST /v1/payments
Content-Type: application/json

{
  "accountId": 15,
  "amount": 0.005,
  "currency": "BTC",
  "purpose": "stock_purchase",
  "metadata": {
    "tradeId": "uuid",
    "instrumentId": "AAPL"
  }
}
```

**Response (201 Created):**
```json
{
  "status": 201,
  "message": "Payment created successfully",
  "results": {
    "paymentId": "uuid",
    "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
    "amount": 0.005,
    "currency": "BTC",
    "status": "PENDING",
    "expiresAt": "2026-09-30T20:00:00Z",
    "qrCode": "data:image/png;base64,...",
    "confirmations": 0,
    "createdAt": "2026-09-29T20:00:00Z"
  }
}
```

### Get Payment Status
```bash
GET /v1/payments/{paymentId}
```

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "Payment found",
  "results": {
    "paymentId": "uuid",
    "walletAddress": "bc1q...",
    "amount": 0.005,
    "currency": "BTC",
    "status": "CONFIRMED",
    "confirmations": 3,
    "transactionHash": "a1b2c3...",
    "createdAt": "2026-09-29T20:00:00Z",
    "confirmedAt": "2026-09-29T20:15:00Z"
  }
}
```

### Get Wallet
```bash
GET /v1/wallets/{accountId}
```

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "Wallet found",
  "results": {
    "accountId": 15,
    "walletAddress": "bc1q...",
    "balance": 0.123,
    "createdAt": "2026-09-29T10:00:00Z"
  }
}
```

### Get Transaction History
```bash
GET /v1/transactions?accountId=15&offset=0&limit=50
```

**Response (200 OK):**
```json
{
  "status": 200,
  "message": "Transactions retrieved",
  "results": {
    "transactions": [...],
    "total": 42,
    "offset": 0,
    "limit": 50
  }
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MSSQL_CONNECTIONSTRING` | SQL Server connection string | Required |
| `REDIS_HOST` | Redis hostname | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `RABBITMQ_HOST` | RabbitMQ hostname | localhost |
| `RABBITMQ_PORT` | RabbitMQ port | 5672 |
| `RABBITMQ_USERNAME` | RabbitMQ username | guest |
| `RABBITMQ_PASSWORD` | RabbitMQ password | guest |
| `FEATURE_FLAG_SERVICE_URL` | Feature flag service URL | http://localhost:80/feature-flag-service |
| `BLOCKCHAIN_API_URL` | Blockchain API endpoint | https://api.blockcypher.com/v1/btc/main |
| `BLOCKCHAIN_API_TOKEN` | Blockchain API token | (empty) |

### Database Schema

The service requires three tables:
- `BitcoinWallets` - User wallet information
- `BitcoinTransactions` - Transaction records
- `BitcoinPaymentStatus` - Status history

See `src/db/sql-scripts/BITCOIN_SCHEMA.md` for full schema documentation.

## Local Development

### Prerequisites
- Java 21
- Docker (for database, Redis, RabbitMQ)
- SQL Server with EasyTrade schema

### Build
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Run Locally
```bash
# Set environment variables
export MSSQL_CONNECTIONSTRING="jdbc:sqlserver://localhost:1433;database=TradeManagement;user=sa;password=yourpassword;encrypt=false"
export REDIS_HOST=localhost
export RABBITMQ_HOST=localhost

# Run application
./gradlew bootRun
```

### Build Docker Image
```bash
docker build -t bitcoin-payment-service:latest .
```

### Run with Docker Compose
```bash
docker-compose up bitcoin-payment-service
```

## Production Deployment

### Kubernetes Deployment
See `kubernetes-manifests/bitcoin-payment-service/` for:
- Deployment with HPA (2-15 replicas)
- Service (ClusterIP)
- ConfigMap for configuration
- Secret for sensitive data

### Helm Deployment
```bash
helm install bitcoin-payment easytrade-helm/bitcoin-payment-service
```

## Monitoring & Observability

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics (Prometheus)
```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

### API Documentation
```bash
# Swagger UI
http://localhost:8080/swagger-ui.html

# OpenAPI JSON
http://localhost:8080/api-docs
```

### Key Metrics
- `bitcoin.payment.requests.total` - Total payment requests
- `bitcoin.payment.requests.duration` - Request latency
- `bitcoin.payments.created` - Payments created counter
- `bitcoin.payments.confirmed` - Payments confirmed counter
- `bitcoin.db.connection.pool.active` - Active DB connections
- `bitcoin.cache.hit.rate` - Cache hit rate

### Business Events
The service publishes Dynatrace business events:
- `bitcoin.payment.created` - When payment is created
- `bitcoin.payment.confirmed` - When payment confirms
- `bitcoin.payment.failed` - When payment fails

## Security

### Wallet Security
- Private keys stored in AWS Secrets Manager / Azure Key Vault
- HD wallet derivation for unique addresses
- No private keys in application code or database

### API Security
- JWT authentication (via loginservice)
- Account-level authorization
- Rate limiting (100 req/min per user)
- Input validation

### Data Protection
- TLS for all service communication
- Encrypted wallet seed storage
- Audit logging for all operations

## Scalability

### Performance Targets (100x baseline)
- **Throughput**: 650 requests/minute
- **Latency**: p95 < 200ms
- **Error Rate**: < 1%
- **Availability**: 99.5% SLO

### Scaling Strategy
- HPA scales 2-15 pods based on CPU (70%)
- Connection pool: 20 connections × 15 pods = 300 connections
- Redis caching reduces DB load by 85%
- Read replicas for database scaling

## Troubleshooting

### Common Issues

**Payment creation fails:**
- Check database connectivity
- Verify wallet address generation
- Check logs for SQL errors

**High latency:**
- Check Redis cache hit rate
- Monitor database connection pool
- Check circuit breaker state

**Circuit breaker open:**
- External blockchain API may be down
- Check rate limiter settings
- Verify API credentials

### Debug Commands
```bash
# Check circuit breaker state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Check connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# Check cache stats
curl http://localhost:8080/actuator/metrics/cache.gets
```

## Architecture Documentation

For full architecture details including:
- Component stress analysis
- 100x scalability design
- Database schema
- Monitoring strategy

See: `docs/BITCOIN_ARCHITECTURE.md`

## Related Services

- **broker-service**: Integrates Bitcoin payments for stock purchases
- **feature-flag-service**: Controls feature rollout
- **frontendreverseproxy**: API gateway
- **db**: SQL Server database

## Testing

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
./gradlew integrationTest
```

### Load Tests
```bash
# Using k6 or similar
k6 run loadtests/bitcoin-payment-load.js
```

## Contributing

1. Create feature branch from `main`
2. Implement changes with tests
3. Run `./gradlew test` and `./gradlew build`
4. Open PR with description and production baseline

## License

Copyright Dynatrace LLC. All rights reserved.

## Support

For issues or questions:
- Check logs: `kubectl logs -f deployment/bitcoin-payment-service`
- Check Dynatrace for traces and metrics
- Review runbook: `docs/BITCOIN_RUNBOOK.md`
- Contact: easytrade-support@dynatrace.com
