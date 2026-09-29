# Bitcoin Payment Service Load Tests

This directory contains load and performance tests to validate the service can handle 100x baseline traffic (650 req/min).

## Prerequisites

Install k6 (load testing tool):

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows
choco install k6
```

See [k6 installation guide](https://k6.io/docs/get-started/installation/) for other platforms.

## Load Tests

### payment-load-test.js

Full load test that gradually ramps up to 100x baseline traffic (650 req/min).

**Test Stages:**
1. **Baseline (6.5 req/min)** - 3 minutes
2. **10x (65 req/min)** - 5 minutes  
3. **50x (325 req/min)** - 5 minutes
4. **100x (650 req/min)** - 5 minutes (stress test)
5. **Ramp down** - 2 minutes

**SLO Validation:**
- ✓ Availability: >99.5% (error rate <0.5%)
- ✓ Latency: P95 <200ms
- ✓ No request >2 seconds

**Run the test:**

```bash
# Against local service
k6 run payment-load-test.js

# Against deployed service
k6 run -e TARGET_URL=http://bitcoin-payment-service:8080 payment-load-test.js

# With custom duration
k6 run -e TARGET_URL=http://localhost:8080 payment-load-test.js
```

**Expected Output:**
```
✓ All SLOs met! Service is ready for 100x scale.
```

## Smoke Tests

Quick validation that the service is responding correctly.

```bash
# Run smoke test
./smoke-test.sh

# Against custom URL
./smoke-test.sh http://bitcoin-payment-service:8080
```

## Interpreting Results

### Success Criteria

| Metric | Baseline | 10x | 50x | 100x | Status |
|--------|----------|-----|-----|------|--------|
| Request Rate | 6.5 req/min | 65 req/min | 325 req/min | 650 req/min | Target |
| Error Rate | <0.5% | <0.5% | <0.5% | <0.5% | SLO |
| P95 Latency | <200ms | <200ms | <200ms | <200ms | SLO |
| P99 Latency | <500ms | <500ms | <500ms | <2000ms | Target |

### Common Issues

**High Error Rate (>0.5%)**
- Check database connection pool size (should be 20)
- Verify HPA is scaling pods (should scale 2-15 replicas)
- Check Redis cache is working (5min TTL)
- Review circuit breaker status

**High Latency (P95 >200ms)**
- Check database query performance (see indexes in BITCOIN_SCHEMA.md)
- Verify Redis cache hit rate (should be >80%)
- Check for rate limiting or throttling
- Review pod resource limits (CPU/memory)

**Circuit Breaker Opening**
- Check blockchain API health (external dependency)
- Verify Resilience4j configuration:
  - Failure rate threshold: 50%
  - Wait duration in open state: 30s
  - Sliding window size: 100 requests

## Monitoring During Tests

### Key Metrics to Watch

**From Dynatrace/Monaco Dashboard:**
1. **Request rate** - Should match load test stages
2. **Error rate** - Should stay <0.5%
3. **Response time** (P50/P95/P99) - P95 should be <200ms
4. **Database connections** - Should not exceed pool size (20)
5. **Cache hit rate** - Should be >80%
6. **Circuit breaker state** - Should stay CLOSED
7. **HPA scaling events** - Pods should scale 2→15 as load increases

**From Kubernetes:**
```bash
# Watch pod scaling
kubectl get hpa bitcoin-payment-service -w

# Check pod metrics
kubectl top pods -l app=bitcoin-payment-service

# View logs
kubectl logs -f -l app=bitcoin-payment-service --tail=100
```

## Load Test Scenarios

### Scenario 1: Gradual Ramp (Default)
Tests normal traffic growth over time. Good for validating HPA behavior.

```bash
k6 run payment-load-test.js
```

### Scenario 2: Spike Test
Tests sudden traffic spike. Good for validating circuit breaker and rate limiting.

```bash
k6 run -e TARGET_URL=http://localhost:8080 --stage 10s:0,1m:220,5m:220 payment-load-test.js
```

### Scenario 3: Soak Test
Tests sustained high load over extended period. Good for finding memory leaks.

```bash
k6 run -e TARGET_URL=http://localhost:8080 --stage 5m:220,60m:220,5m:0 payment-load-test.js
```

## Troubleshooting

### Test fails to connect
- Verify service is running: `curl http://localhost:8080/actuator/health`
- Check network connectivity
- Verify TARGET_URL is correct

### Test times out
- Check database is accessible
- Verify Redis is running
- Check RabbitMQ connection
- Review service logs for errors

### High error rate during test
- Check database capacity (connection pool)
- Verify HPA is enabled and scaling
- Check for resource limits (CPU/memory throttling)
- Review circuit breaker configuration

### Results don't match expectations
- Ensure database has proper indexes (see BITCOIN_SCHEMA.md)
- Verify Redis cache is enabled and working
- Check HPA configuration (scaling thresholds)
- Review resource requests/limits in deployment

## Next Steps

After successful load tests:
1. ✓ Validate SLO compliance (99.5% availability, P95 <200ms)
2. ✓ Verify HPA scaled correctly (2-15 replicas)
3. ✓ Check Monaco alerts were not triggered
4. ✓ Review Dynatrace dashboard for anomalies
5. → Document results in PR description
6. → Schedule follow-up load test in production-like environment
