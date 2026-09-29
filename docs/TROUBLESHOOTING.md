# EasyTrade Troubleshooting Guide

## Common Issues

### Credit Card Orders Failing with 500 Error

**Symptoms:**
- Users receive `500 Internal Server Error` when checking credit card order status
- Error message: `java.lang.ArithmeticException: / by zero`
- Endpoint: `GET /v1/orders/{accountId}/status/latest`

**Cause:**
The `credit_card_meltdown` feature flag is enabled. This is a chaos engineering pattern that intentionally causes a division-by-zero error for testing observability and incident response.

**Quick Fix:**
```bash
# Run the provided script
./scripts/disable-credit-card-meltdown.sh

# Or manually via API
curl -X PUT "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

**Verification:**
```bash
# Run verification script
./scripts/verify-credit-card-fix.sh

# Or manually check
curl -s "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" | jq '.enabled'
# Should return: false
```

**Prevention:**
- The default configuration now explicitly sets `ENABLE_CREDIT_CARD_MELTDOWN=false`
- Only enable problem patterns in non-production environments for testing
- Monitor feature flag changes in production
- Set up alerts for problem pattern flags being enabled

**Related Files:**
- Fix documentation: [ISSUE_1_FIX.md](../ISSUE_1_FIX.md)
- GitHub Issue: [#1](https://github.com/binzhango/easytradeworkshop/issues/1)

---

## Problem Patterns

EasyTrade includes several "problem patterns" - chaos engineering features designed to simulate production issues:

1. **db_not_responding** - Simulates database errors, preventing new trades
2. **ergo_aggregator_slowdown** - Causes slow responses from offer services
3. **factory_crisis** - Blocks credit card production and processing
4. **credit_card_meltdown** - Causes division-by-zero errors in order status checks
5. **high_cpu_usage** - Increases CPU usage and causes slowdowns

### Managing Problem Patterns

**View all problem pattern flags:**
```bash
curl -s "http://localhost/feature-flag-service/v1/flags?tag=problem_pattern" | jq '.'
```

**Enable a problem pattern (for testing only):**
```bash
curl -X PUT "http://localhost/feature-flag-service/v1/flags/{FLAG_ID}" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

**Disable a problem pattern:**
```bash
curl -X PUT "http://localhost/feature-flag-service/v1/flags/{FLAG_ID}" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'
```

**⚠️ Warning:** Problem patterns should **never** be enabled in production environments unless you are intentionally conducting chaos engineering exercises.

---

## Service Health Checks

### Check if all services are running

**Docker Compose:**
```bash
docker compose ps
```

**Kubernetes:**
```bash
kubectl get pods -n easytrade
```

### Check service logs

**Docker Compose:**
```bash
# Specific service
docker compose logs credit-card-order-service

# Follow logs
docker compose logs -f credit-card-order-service
```

**Kubernetes:**
```bash
# Specific pod
kubectl logs -n easytrade <pod-name>

# Follow logs
kubectl logs -n easytrade <pod-name> -f
```

---

## Database Issues

### Database not responding
If you see errors about database connections:

1. Check if the database is running:
   ```bash
   docker compose ps db
   # or
   kubectl get pods -n easytrade | grep db
   ```

2. Check database logs:
   ```bash
   docker compose logs db
   # or
   kubectl logs -n easytrade <db-pod-name>
   ```

3. Verify connection string in service environment variables

---

## Feature Flag Service Issues

### Cannot connect to feature flag service

1. Verify the service is running:
   ```bash
   curl -s "http://localhost/feature-flag-service/v1/flags" | jq '.'
   ```

2. Check service logs for errors

3. Verify proxy/ingress configuration is correct

### Feature flag changes not taking effect

Feature flags are evaluated at request time. If changes aren't taking effect:

1. Verify the flag was actually updated:
   ```bash
   curl -s "http://localhost/feature-flag-service/v1/flags/{FLAG_ID}" | jq '.'
   ```

2. Check if services are caching flag values (most services don't cache)

3. Restart the affected service if necessary

---

## Getting Help

For more detailed information:
- Check service-specific README files in `src/<service-name>/`
- Review the main [README.md](../README.md)
- Check GitHub issues for known problems
- Review Bluebox investigations for production issues

For problem pattern documentation, see:
- [README.md - Problem Patterns section](../README.md#problem-patterns)
- [Feature Flag Service README](../src/feature-flag-service/README.md)
