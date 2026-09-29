# Fix for Issue #1: Credit Card Orders Failing - Division by Zero Error

## Summary
This document describes the fix for the credit card order failure caused by the `credit_card_meltdown` feature flag being enabled.

## Root Cause
The `credit_card_meltdown` feature flag is currently enabled, triggering an intentional division-by-zero error in the `OrderController.CountArythmeticSequenceTotal()` method when users check credit card order status.

## Immediate Fix

### Option 1: Via API (Recommended for immediate production fix)

If the service is running, disable the feature flag via the feature-flag-service API:

```bash
# Disable the feature flag
curl -X PUT "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"enabled": false}'
```

Verify the flag is disabled:
```bash
curl -s "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" | jq '.enabled'
# Should return: false
```

### Option 2: Via Docker Compose Environment Variable

Add the following environment variable to the `feature-flag-service` in `compose.yaml`:

```yaml
feature-flag-service:
  <<: *default-service
  image: ${REGISTRY}/feature-flag-service:${TAG}
  environment:
    PROXY_PREFIX: feature-flag-service
    ENABLE_CREDIT_CARD_MELTDOWN: "false"  # Explicitly disable the meltdown flag
```

Then restart the service:
```bash
docker compose restart feature-flag-service
```

### Option 3: Via Kubernetes Helm Values

For Kubernetes deployments, ensure the Helm values explicitly set the flag to false by adding to `helm/easytrade/values.yaml`:

```yaml
feature-flag-service:
  enabled: true
  env:
    FEATURE_FLAG_SERVICE_PROTOCOL: http
    FEATURE_FLAG_SERVICE_BASE_URL: "{{ .Release.Name }}-feature-flag-service"
    FEATURE_FLAG_SERVICE_PORT: "8080"
    ENABLE_CREDIT_CARD_MELTDOWN: "false"  # Explicitly disable
```

Then upgrade the Helm release:
```bash
helm upgrade easytrade oci://europe-docker.pkg.dev/dynatrace-demoability/helm/easytrade \
  --namespace easytrade \
  -f helm/easytrade/values.yaml
```

## Verification

After applying the fix, verify the service is working:

1. Check the feature flag status:
```bash
curl -s "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" | jq '.'
```

Expected output:
```json
{
  "id": "credit_card_meltdown",
  "enabled": false,
  "description": "When enabled, checking the latest credit card order status results in a division by zero error.",
  "tag": "problem_pattern"
}
```

2. Test the credit card order status endpoint:
```bash
# This should now return a successful response (or 404 if no orders exist)
curl -X GET "http://localhost/credit-card-order-service/v1/orders/1/status/latest" \
  -H "Accept: application/json"
```

## Long-term Recommendations

### 1. Feature Flag Management
- **Audit Logging**: Implement logging for all feature flag changes
- **Access Control**: Restrict production feature flag changes to authorized personnel only
- **Alerting**: Add alerts when problem pattern flags are enabled in production
- **Default to Safe**: Ensure `ENABLE_CREDIT_CARD_MELTDOWN` defaults to `false` in all production configurations

### 2. Monitoring & Alerting
- Add error rate alerts on credit card endpoints (threshold: >1% error rate)
- Monitor feature flag state changes in production environments
- Set up SLOs for credit card order success rate (target: 99.9%)
- Create a dashboard showing problem pattern flag states

### 3. Documentation
- Document all problem patterns and their purposes
- Create runbooks for disabling problem patterns in emergencies
- Add warnings in the feature flag service UI about production impacts

## Files Involved

- **Service Code**: `src/credit-card-order-service/src/main/java/com/dynatrace/easytrade/creditcardorderservice/OrderController.java` (lines 134-147, 323-330)
- **Feature Flag Service**: `src/feature-flag-service/src/main/resources/application.properties` (line 6)
- **Docker Compose**: `compose.yaml`
- **Helm Values**: `helm/easytrade/values.yaml`

## Testing

To test this is a chaos engineering feature (not a bug), you can:

1. Enable the flag:
```bash
curl -X PUT "http://localhost/feature-flag-service/v1/flags/credit_card_meltdown" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

2. Try to get order status (should fail with 500):
```bash
curl -X GET "http://localhost/credit-card-order-service/v1/orders/1/status/latest"
```

3. Disable the flag again to restore service.

## Notes

- This is a **chaos engineering feature** designed to test observability and incident response
- The code intentionally causes a division-by-zero error when enabled
- This is not a code defect; it's an operational issue (misconfigured feature flag)
- The fix is to disable the feature flag, not to remove or modify the code

## Related

- Bluebox Investigation: task_20eibKfzUFzNfu6ugDrZSC
- GitHub Issue: https://github.com/binzhango/easytradeworkshop/issues/1
