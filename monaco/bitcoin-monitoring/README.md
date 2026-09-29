# Bitcoin Payment Service - Monitoring Configuration

This Monaco project provides comprehensive monitoring and alerting for the Bitcoin Payment Service using Dynatrace Configuration as Code.

## Overview

The monitoring configuration includes:
- **3 SLOs** - Availability, Latency, Confirmation Time
- **4 Alerting Profiles** - Error Rate, Service Down, Slow Confirmations, Circuit Breaker
- **1 Dashboard** - Bitcoin Payments Overview
- **1 Metric Event** - Payment Volume Spike Detection

## SLOs (Service Level Objectives)

### 1. Bitcoin Payment Service - Availability
- **Target:** 99.5% success rate
- **Warning:** 99.7%
- **Timeframe:** 7 days (rolling)
- **Error Budget:** 0.5% = 421 failed requests/week at 100x scale

**Calculation:**
```
(Success Count / Total Request Count) × 100 >= 99.5%
```

**Why This Matters:**
- Ensures payment creation endpoint is highly available
- Error budget allows for ~60 failed requests per day at 100x scale
- Weekly window smooths out daily variations

### 2. Bitcoin Payment Service - Latency
- **Target:** p95 < 200ms
- **Warning:** p95 < 150ms
- **Timeframe:** 24 hours (rolling)

**Calculation:**
```
95th percentile of service response time < 200ms
```

**Why This Matters:**
- Fast payment creation improves user experience
- 200ms allows for DB queries, Redis cache, and API processing
- Catches performance degradation before it affects users

### 3. Bitcoin Payment - Confirmation Latency
- **Target:** p95 < 15 minutes (900,000ms)
- **Warning:** p95 < 10 minutes (600,000ms)
- **Timeframe:** 24 hours (rolling)

**Calculation:**
```
95th percentile of confirmation duration < 15 minutes
```

**Why This Matters:**
- Typical Bitcoin confirmations take 10-30 minutes
- Target aligned with blockchain network performance
- Alerts on unusually long confirmation times

## Alerting Profiles

### 1. High Error Rate Alert
- **Severity:** Performance
- **Trigger:** Error rate > threshold for 5 minutes
- **Scope:** bitcoin-payment-service tagged pods

**When It Fires:**
- Multiple failed payment creations
- Database connection errors
- Validation failures

**Response:**
1. Check service logs for error patterns
2. Verify database connectivity
3. Check Redis and RabbitMQ health
4. Review recent deployments

### 2. Service Down Alert
- **Severity:** Availability
- **Trigger:** Service unavailable for 2 minutes
- **Scope:** bitcoin-payment-service tagged pods

**When It Fires:**
- All pods down or unhealthy
- Load balancer cannot reach service
- Container crashes or OOMKilled

**Response:**
1. Check pod status: `kubectl get pods -l app=bitcoin-payment-service`
2. View pod logs: `kubectl logs -l app=bitcoin-payment-service --tail=100`
3. Check HPA status
4. Verify resource limits not exceeded

### 3. Slow Confirmations Alert
- **Severity:** Performance
- **Trigger:** Confirmation time > 15 minutes for 15+ minutes
- **Scope:** bitcoin-confirmation-worker

**When It Fires:**
- Blockchain network congestion
- Blockchain API rate limiting or errors
- Worker not processing queue

**Response:**
1. Check blockchain network status (external)
2. Verify RabbitMQ queue depth
3. Check worker logs for errors
4. Verify blockchain API credentials

### 4. Circuit Breaker Open Alert
- **Severity:** Resource
- **Trigger:** Circuit breaker state changes to OPEN
- **Scope:** bitcoin-payment-service

**When It Fires:**
- Blockchain API failures exceed threshold
- 5 consecutive failures in sliding window

**Response:**
1. Check blockchain API status
2. Verify API credentials
3. Check network connectivity
4. Wait for auto-recovery (30 seconds) or manual intervention

## Dashboard

### Bitcoin Payments Overview

**Top Row:**
1. **Payment Request Rate** - Requests per minute graph
2. **Error Rate** - Error percentage with color-coded thresholds
3. **Response Time** - p50, p95, p99 latency lines

**Middle Row:**
4. **Payments Created** - Total count single value
5. **Payments Confirmed** - Total count single value
6. **Active DB Connections** - HikariCP pool usage

**Bottom Row:**
7. **Circuit Breaker State** - 0=Closed (healthy), 1=Open (failing)
8. **Cache Hit Rate** - Redis cache effectiveness

**Color Coding:**
- 🟢 Green: Healthy/Normal
- 🟡 Yellow: Warning threshold
- 🔴 Red: Critical threshold

## Metric Events

### Payment Volume Spike Detection
- **Metric:** bitcoin.payments.created
- **Threshold:** > 100 requests/minute
- **Trigger:** 3 out of 5 samples exceed threshold

**When It Fires:**
- Abnormal payment creation rate
- Potential attack or abuse
- Viral marketing campaign
- Load testing

**Response:**
1. Verify legitimate traffic increase
2. Check HPA scaling appropriately
3. Monitor resource usage
4. Investigate suspicious patterns

## Deployment

### Prerequisites

1. **Dynatrace Tenant**
   - SaaS or Managed environment
   - API token with required permissions

2. **Monaco CLI**
   ```bash
   # Install Monaco v2
   curl -L https://github.com/dynatrace/dynatrace-configuration-as-code/releases/latest/download/monaco-linux-amd64 -o monaco
   chmod +x monaco
   sudo mv monaco /usr/local/bin/
   ```

3. **Environment Variables**
   ```bash
   export TENANT_URL="https://your-tenant.live.dynatrace.com"
   export TENANT_TOKEN="dt0c01.YOUR_API_TOKEN"
   # OR for OAuth
   export CLIENT_ID="your-client-id"
   export CLIENT_SECRET="your-client-secret"
   ```

### Deploy Configuration

```bash
# From repository root
cd monaco

# Dry run (validate without deploying)
monaco deploy --dry-run --manifest manifest.yaml --environment development

# Deploy to Dynatrace
monaco deploy --manifest manifest.yaml --environment development

# Deploy only Bitcoin monitoring
monaco deploy --manifest manifest.yaml --environment development --project bitcoin-monitoring
```

### Verify Deployment

```bash
# Check SLOs in Dynatrace UI
# Navigate to: Services & Transactions > SLOs

# Check Alerting Profiles
# Navigate to: Settings > Alerting > Alerting profiles

# Check Dashboard
# Navigate to: Dashboards > Bitcoin Payments Overview

# Check Metric Events
# Navigate to: Settings > Anomaly detection > Metric events
```

## Customization

### Adjusting SLO Targets

Edit `slo/*.json` files:

```json
{
  "target": 99.5,    // Change to your target (e.g., 99.9)
  "warning": 99.7,   // Warning threshold
  "timeframe": "-1w" // Time window (-1d, -1w, -1M)
}
```

### Modifying Alert Thresholds

Edit `alerting/*.json` files:

```json
{
  "delayInMinutes": 5  // Time before alert fires
}
```

### Adding Dashboard Tiles

Edit `dashboard/bitcoin-overview.json`:

1. Add new tile to `tiles` array
2. Set `bounds` for position and size
3. Define `queries` for metrics
4. Configure `visualConfig` for appearance

### Custom Metrics

To track custom metrics, ensure your application exports them:

**Spring Boot Actuator (bitcoin-payment-service):**
```java
@Component
public class BitcoinMetrics {
    private final MeterRegistry meterRegistry;

    public BitcoinMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordPaymentCreated() {
        meterRegistry.counter("bitcoin.payments.created").increment();
    }

    public void recordConfirmation(Duration duration) {
        meterRegistry.timer("bitcoin.confirmation.duration")
            .record(duration);
    }
}
```

## Monitoring Best Practices

### SLO Management

1. **Review Monthly:** Check SLO burn rate and error budget
2. **Adjust Targets:** Based on actual performance and business needs
3. **Prioritize Work:** Use error budget to justify reliability improvements

### Alert Fatigue Prevention

1. **Tune Thresholds:** Reduce false positives
2. **Add Delays:** Allow transient issues to self-resolve
3. **Group Alerts:** Combine related alerts
4. **Escalation Policy:** Route to appropriate teams

### Dashboard Usage

1. **Daily Checks:** Review dashboard during standup
2. **Incident Response:** First place to check during outages
3. **Capacity Planning:** Monitor trends for scaling decisions
4. **Performance Optimization:** Identify slow endpoints

## Troubleshooting

### SLO Not Showing Data

**Cause:** Service entity not found or metric not available

**Solution:**
1. Replace `SERVICE-BITCOIN_PAYMENT` with actual service entity ID
2. Find entity ID in Dynatrace:
   ```
   Services & Transactions > Search "bitcoin" > Copy entity ID
   ```
3. Update all SLO JSON files with correct ID

### Alerts Not Firing

**Cause:** Tag filters not matching

**Solution:**
1. Verify tags on service:
   ```bash
   kubectl get deployment bitcoin-payment-service -o yaml | grep -A 5 labels
   ```
2. Ensure tags match in alerting profiles:
   ```json
   "tagFilters": [{
     "key": "app",
     "value": "bitcoin-payment-service"
   }]
   ```

### Dashboard Empty

**Cause:** Metrics not being exported

**Solution:**
1. Check actuator endpoint:
   ```bash
   curl http://bitcoin-payment-service:8080/actuator/prometheus
   ```
2. Verify ServiceMonitor created:
   ```bash
   kubectl get servicemonitor bitcoin-payment-service
   ```
3. Check Prometheus targets in Dynatrace

### Monaco Deployment Fails

**Common Errors:**

1. **Authentication Error**
   ```
   Solution: Verify TENANT_TOKEN has required permissions
   Required: API v2 scopes (settings.read, settings.write)
   ```

2. **Invalid JSON**
   ```
   Solution: Validate JSON syntax
   Command: jq . slo/availability-slo.json
   ```

3. **Duplicate Names**
   ```
   Solution: Ensure unique names across all configurations
   Or use overrideExisting flag
   ```

## Metrics Reference

| Metric | Type | Description | Unit |
|--------|------|-------------|------|
| `bitcoin.payments.created` | Counter | Total payments created | Count |
| `bitcoin.payments.confirmed` | Counter | Total payments confirmed | Count |
| `bitcoin.payments.failed` | Counter | Total payments failed | Count |
| `bitcoin.confirmation.duration` | Timer | Time to confirm payment | Milliseconds |
| `bitcoin.payment.requests.duration` | Histogram | API request latency | Milliseconds |
| `bitcoin.db.connection.pool.active` | Gauge | Active DB connections | Count |
| `bitcoin.cache.hit.rate` | Gauge | Cache hit percentage | Percent |
| `bitcoin.circuit.breaker.state` | Gauge | CB state (0=closed, 1=open) | State |
| `bitcoin.queue.depth` | Gauge | RabbitMQ queue size | Count |

## Related Documentation

- Architecture: `docs/BITCOIN_ARCHITECTURE.md`
- Service README: `src/bitcoin-payment-service/README.md`
- Kubernetes Manifests: `kubernetes-manifests/bitcoin-payment-service/`
- Dynatrace Monaco: https://github.com/dynatrace/dynatrace-configuration-as-code

## Support

For monitoring issues:
- Check Dynatrace logs in Settings > Log Monitoring
- Review Monaco deployment logs
- Verify API token permissions
- Contact Dynatrace support for platform issues
