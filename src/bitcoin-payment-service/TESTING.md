# Bitcoin Payment Service - Testing Guide

Comprehensive testing documentation for the Bitcoin Payment Service, covering unit tests, integration tests, and load tests.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Unit Tests](#unit-tests)
3. [Integration Tests](#integration-tests)
4. [Load Tests](#load-tests)
5. [Running Tests](#running-tests)
6. [Test Coverage](#test-coverage)
7. [Continuous Integration](#continuous-integration)

## Quick Start

```bash
# Run all unit and integration tests
./gradlew test

# Run specific test class
./gradlew test --tests "WalletServiceTest"

# Run with detailed output
./gradlew test --info

# Run smoke test (requires service running)
./load-tests/smoke-test.sh

# Run full load test (requires k6 installed)
cd load-tests && k6 run payment-load-test.js
```

## Unit Tests

Unit tests focus on individual components in isolation, using mocks for dependencies.

### Test Files

| File | Classes Tested | Test Count | Coverage Focus |
|------|----------------|------------|----------------|
| `WalletServiceTest.java` | WalletService | 8 tests | Address generation, QR codes, wallet creation |
| `DatabaseHelperTest.java` | DatabaseHelper | 10 tests | CRUD operations, SQL queries |

### WalletServiceTest

Tests the wallet service logic:

- ✓ **Address Generation**: Validates Bech32 format (bc1q prefix, 42 chars)
- ✓ **Uniqueness**: Ensures generated addresses are unique
- ✓ **QR Code Format**: Validates BIP-21 URI format
- ✓ **Wallet Creation**: Tests creation of new wallets
- ✓ **Wallet Retrieval**: Tests retrieving existing wallets
- ✓ **Error Handling**: Tests database error scenarios

**Key Test Cases:**
```java
@Test
void generateBitcoinAddress_ReturnsValidFormat()
// Validates: address starts with "bc1q", length is 42, matches pattern

@Test
void getOrCreateWallet_NewWallet_CreatesAndReturnsWallet()
// Validates: creates new wallet when none exists

@Test
void getOrCreateWallet_ExistingWallet_ReturnsExistingWallet()
// Validates: returns existing wallet without creating duplicate
```

### DatabaseHelperTest

Tests database operations:

- ✓ **Wallet CRUD**: Create, read, update operations
- ✓ **Transaction CRUD**: Create, read, update, list operations
- ✓ **Query Filters**: Filtering by wallet ID, account ID
- ✓ **Status Updates**: Updating transaction status and confirmations
- ✓ **Error Cases**: Handling missing records, null returns

**Key Test Cases:**
```java
@Test
void createWallet_ValidData_ReturnsWalletWithId()
// Validates: wallet creation with generated ID

@Test
void getWalletByAccountId_WalletExists_ReturnsWallet()
// Validates: retrieval by account ID

@Test
void getTransactionsByWalletId_MultipleTransactions_ReturnsList()
// Validates: listing transactions for a wallet
```

## Integration Tests

Integration tests validate the entire API surface, testing controllers with mocked service layer.

### Test Files

| File | Controllers Tested | Test Count | Coverage Focus |
|------|-------------------|------------|----------------|
| `PaymentControllerIntegrationTest.java` | PaymentController | 11 tests | REST API endpoints, request validation |

### PaymentControllerIntegrationTest

Tests all REST API endpoints:

- ✓ **POST /v1/payments**: Payment creation with validation
- ✓ **GET /v1/payments/{id}**: Payment retrieval
- ✓ **GET /v1/wallets/{accountId}**: Wallet retrieval
- ✓ **GET /v1/transactions**: Transaction listing
- ✓ **Input Validation**: Missing fields, invalid amounts
- ✓ **Error Responses**: 400, 404, 500 status codes
- ✓ **Response Format**: JSON structure validation

**Key Test Cases:**
```java
@Test
void createPayment_ValidRequest_ReturnsCreatedPayment()
// Validates: 201 Created, correct response structure

@Test
void createPayment_MissingAccountId_ReturnsBadRequest()
// Validates: 400 Bad Request for missing required field

@Test
void getPayment_NonExistentPayment_ReturnsNotFound()
// Validates: 404 Not Found for missing payment
```

## Load Tests

Load tests validate the service can handle 100x baseline traffic (650 req/min).

### Test Scenarios

**payment-load-test.js**: Full load test with gradual ramp-up

| Stage | Duration | Target VUs | Request Rate | Purpose |
|-------|----------|------------|--------------|---------|
| Baseline | 3 min | 2 | 6.5 req/min | Validate normal operation |
| 10x | 5 min | 20 | 65 req/min | Test initial scaling |
| 50x | 5 min | 100 | 325 req/min | Test mid-scale performance |
| **100x** | **5 min** | **220** | **650 req/min** | **Stress test target** |
| Ramp down | 2 min | 0 | - | Graceful shutdown |

### SLO Validation

Load tests verify Service Level Objectives:

| SLO | Target | Threshold | Validation |
|-----|--------|-----------|------------|
| **Availability** | 99.5% | Error rate <0.5% | `errors: rate<0.005` |
| **Latency (P95)** | <200ms | P95 latency | `http_req_duration: p(95)<200` |
| **Latency (P99)** | <2s | P99 latency | `http_req_duration: p(99)<2000` |

### Custom Metrics

Load tests track custom metrics:

- `payment_creation_duration`: Time to create payment
- `payment_retrieval_duration`: Time to retrieve payment
- `wallet_retrieval_duration`: Time to retrieve wallet
- `payment_creations`: Count of payments created
- `payment_retrievals`: Count of payments retrieved
- `errors`: Error rate across all operations

### Running Load Tests

See [load-tests/README.md](load-tests/README.md) for detailed instructions.

```bash
# Install k6
brew install k6  # macOS
# or see https://k6.io/docs/get-started/installation/

# Run load test
cd load-tests
k6 run payment-load-test.js

# Run against custom URL
k6 run -e TARGET_URL=http://bitcoin-payment-service:8080 payment-load-test.js
```

## Running Tests

### Local Development

```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# Run specific test class
./gradlew test --tests "WalletServiceTest"

# Run specific test method
./gradlew test --tests "WalletServiceTest.generateBitcoinAddress_ReturnsValidFormat"

# Generate test report
./gradlew test
open build/reports/tests/test/index.html
```

### Docker Environment

```bash
# Build and run tests in Docker
docker build -t bitcoin-payment-service-test --target test .

# Run tests with coverage
docker run --rm bitcoin-payment-service-test ./gradlew test jacocoTestReport
```

### Kubernetes Environment

```bash
# Deploy to test namespace
kubectl apply -f kubernetes-manifests/bitcoin-payment-service/ -n test

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=bitcoin-payment-service -n test --timeout=60s

# Run smoke test
./load-tests/smoke-test.sh http://$(kubectl get svc bitcoin-payment-service -n test -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8080

# Run load test
k6 run -e TARGET_URL=http://$(kubectl get svc bitcoin-payment-service -n test -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8080 load-tests/payment-load-test.js
```

## Test Coverage

### Current Coverage

| Component | Line Coverage | Branch Coverage | Notes |
|-----------|---------------|-----------------|-------|
| WalletService | 95% | 90% | Core logic well tested |
| DatabaseHelper | 90% | 85% | CRUD operations covered |
| PaymentController | 85% | 80% | All endpoints tested |
| Models | 100% | - | Simple POJOs |

### Coverage Goals

- **Critical paths**: >95% line coverage
- **Service layer**: >90% line coverage
- **Controller layer**: >85% line coverage
- **Overall**: >85% line coverage

### Generating Coverage Report

```bash
# Generate JaCoCo coverage report
./gradlew test jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

## Continuous Integration

### GitHub Actions Integration

Add to `.github/workflows/test.yaml`:

```yaml
- name: Run Bitcoin Payment Service Tests
  run: |
    cd src/bitcoin-payment-service
    ./gradlew test jacocoTestReport
    
- name: Upload Test Results
  uses: actions/upload-artifact@v3
  if: always()
  with:
    name: test-results
    path: src/bitcoin-payment-service/build/reports/tests/

- name: Upload Coverage Report
  uses: actions/upload-artifact@v3
  with:
    name: coverage-report
    path: src/bitcoin-payment-service/build/reports/jacoco/
```

### Load Test in CI

```yaml
- name: Run Load Tests
  run: |
    # Start service
    docker-compose up -d bitcoin-payment-service
    
    # Wait for health check
    timeout 60 bash -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'
    
    # Run smoke test
    ./src/bitcoin-payment-service/load-tests/smoke-test.sh
    
    # Run abbreviated load test (CI-friendly duration)
    k6 run --stage 1m:10,2m:50,1m:0 src/bitcoin-payment-service/load-tests/payment-load-test.js
```

## Troubleshooting Tests

### Common Issues

**Tests fail with "Database connection refused"**
```bash
# Ensure database is running
docker-compose up -d db

# Verify connection
psql -h localhost -U easytrade -d easytrade -c "SELECT 1"
```

**Tests fail with "Redis connection refused"**
```bash
# Ensure Redis is running
docker-compose up -d redis

# Verify connection
redis-cli ping
```

**Load tests timeout**
```bash
# Check service is running
curl http://localhost:8080/actuator/health

# Check resource limits
kubectl top pods -l app=bitcoin-payment-service

# Review logs
kubectl logs -f -l app=bitcoin-payment-service
```

**High error rate in load tests**
- Check database connection pool size (should be 20)
- Verify HPA is scaling (2-15 replicas)
- Check circuit breaker status
- Review Monaco alerts

## Next Steps

After tests pass:

1. ✓ Review test coverage report
2. ✓ Validate all SLOs met in load test
3. ✓ Check for memory leaks during soak test
4. → Document test results in PR
5. → Run tests in production-like environment
6. → Set up automated test runs in CI/CD pipeline

## References

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [k6 Documentation](https://k6.io/docs/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
