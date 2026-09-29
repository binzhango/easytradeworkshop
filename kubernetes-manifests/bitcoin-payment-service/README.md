# Bitcoin Payment Service - Kubernetes Manifests

This directory contains Kubernetes manifests for deploying the Bitcoin Payment Service with production-grade configuration designed to handle 100x baseline traffic.

## Overview

The Bitcoin Payment Service is deployed as a horizontally scalable microservice with:
- Horizontal Pod Autoscaling (2-15 replicas)
- Pod Disruption Budget for high availability
- Health checks and graceful shutdown
- Prometheus metrics integration
- Resource limits and requests
- Security context (non-root user)

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Ingress / Gateway                             │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              bitcoin-payment-service (Service)                   │
│                    Type: ClusterIP, Port: 8080                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              bitcoin-payment-service (Deployment)                │
│                    Replicas: 2-15 (HPA)                          │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Pod 1      │  │   Pod 2      │  │   Pod N      │          │
│  │ CPU: 200m-1  │  │ CPU: 200m-1  │  │ CPU: 200m-1  │          │
│  │ Mem: 256-512 │  │ Mem: 256-512 │  │ Mem: 256-512 │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
          ▼                 ▼                 ▼
    ┌─────────┐      ┌──────────┐      ┌──────────┐
    │  Redis  │      │   MSSQL  │      │ RabbitMQ │
    │ (Cache) │      │   (DB)   │      │ (Queue)  │
    └─────────┘      └──────────┘      └──────────┘
```

## Files

| File | Purpose |
|------|---------|
| `deployment.yaml` | Main deployment with container spec, resources, health checks |
| `service.yaml` | ClusterIP service exposing port 8080 |
| `hpa.yaml` | Horizontal Pod Autoscaler (2-15 replicas, 70% CPU, 80% memory) |
| `configmap.yaml` | Application configuration (optional, can use env vars) |
| `pdb.yaml` | Pod Disruption Budget (minimum 1 pod available) |
| `servicemonitor.yaml` | Prometheus ServiceMonitor for metrics scraping |

## Prerequisites

1. **Kubernetes Cluster**
   - Version 1.23+ recommended
   - Metrics Server installed (for HPA)
   - Prometheus Operator (optional, for ServiceMonitor)

2. **Dependencies**
   - SQL Server database with EasyTrade schema
   - Redis for caching
   - RabbitMQ for async processing
   - feature-flag-service deployed

3. **Secrets**
   ```bash
   kubectl create secret generic easytrade-db-credentials \
     --from-literal=JAVA_CONNECTION_STRING="jdbc:sqlserver://..."
   
   kubectl create secret generic bitcoin-payment-secrets \
     --from-literal=blockchain-api-token="your-token-here"
   ```

## Deployment

### Option 1: Deploy All Manifests

```bash
# Create namespace
kubectl create namespace easytrade

# Deploy all manifests
kubectl apply -f kubernetes-manifests/bitcoin-payment-service/ -n easytrade

# Verify deployment
kubectl get pods -n easytrade -l app=bitcoin-payment-service
kubectl get hpa -n easytrade bitcoin-payment-service
```

### Option 2: Deploy Individually

```bash
# Deploy in order
kubectl apply -f configmap.yaml -n easytrade
kubectl apply -f deployment.yaml -n easytrade
kubectl apply -f service.yaml -n easytrade
kubectl apply -f hpa.yaml -n easytrade
kubectl apply -f pdb.yaml -n easytrade
kubectl apply -f servicemonitor.yaml -n easytrade
```

### Option 3: Using Helm

```bash
# From repository root
helm install easytrade helm/easytrade \
  --namespace easytrade \
  --create-namespace \
  --set bitcoin-payment-service.enabled=true
```

## Configuration

### Environment Variables

Edit `deployment.yaml` to customize:

**Required:**
- `MSSQL_CONNECTIONSTRING` - Database connection string (from secret)

**Optional:**
- `REDIS_HOST` - Redis hostname (default: easytrade-redis)
- `REDIS_PORT` - Redis port (default: 6379)
- `RABBITMQ_HOST` - RabbitMQ hostname (default: easytrade-rabbitmq)
- `RABBITMQ_PORT` - RabbitMQ port (default: 5672)
- `FEATURE_FLAG_SERVICE_URL` - Feature flag service URL
- `BLOCKCHAIN_API_URL` - Blockchain API endpoint
- `BLOCKCHAIN_API_TOKEN` - Blockchain API authentication token

### Resource Limits

Adjust in `deployment.yaml`:

```yaml
resources:
  requests:
    cpu: 200m       # Per pod request
    memory: 256Mi
  limits:
    cpu: 1000m      # Per pod limit
    memory: 512Mi
```

**Capacity Calculation:**
- 15 pods × 200m CPU = 3 CPU cores minimum
- 15 pods × 256Mi = 3.75 GB memory minimum
- 15 pods × 1000m = 15 CPU cores maximum
- 15 pods × 512Mi = 7.5 GB memory maximum

### HPA Configuration

Modify `hpa.yaml` for different scaling:

```yaml
spec:
  minReplicas: 2        # Minimum pods
  maxReplicas: 15       # Maximum pods
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70    # Scale at 70% CPU
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80    # Scale at 80% memory
```

**Scale-up behavior:**
- Can add 50% more pods every 60 seconds
- Or add 2 pods every 60 seconds
- Whichever is higher

**Scale-down behavior:**
- Can remove 10% of pods every 60 seconds
- Or remove 1 pod every 120 seconds
- Whichever is lower
- Stabilization window: 300 seconds

## Scaling Strategy

### Traffic Projections

| Scenario | RPS | Pods Required | CPU | Memory |
|----------|-----|---------------|-----|--------|
| Baseline | 0.1 | 2 (min) | 400m | 512Mi |
| 10x | 1 | 3 | 600m | 768Mi |
| 50x | 5 | 8 | 1600m | 2Gi |
| 100x | 10 | 15 (max) | 3000m | 3.75Gi |

### Verification

```bash
# Check current replicas
kubectl get hpa bitcoin-payment-service -n easytrade

# Check pod CPU/Memory usage
kubectl top pods -n easytrade -l app=bitcoin-payment-service

# Check HPA metrics
kubectl describe hpa bitcoin-payment-service -n easytrade

# Watch scaling events
kubectl get events -n easytrade --field-selector involvedObject.name=bitcoin-payment-service --watch
```

## Health Checks

### Liveness Probe
- **Endpoint:** `/actuator/health`
- **Initial Delay:** 60 seconds
- **Period:** 10 seconds
- **Timeout:** 3 seconds
- **Failure Threshold:** 3

### Readiness Probe
- **Endpoint:** `/actuator/health`
- **Initial Delay:** 30 seconds
- **Period:** 5 seconds
- **Timeout:** 3 seconds
- **Failure Threshold:** 3

### Manual Health Check

```bash
# From inside cluster
kubectl run curl --image=curlimages/curl -i --rm --restart=Never -- \
  curl http://bitcoin-payment-service:8080/actuator/health

# Port forward and check locally
kubectl port-forward svc/bitcoin-payment-service 8080:8080 -n easytrade
curl http://localhost:8080/actuator/health
```

## Monitoring

### Prometheus Metrics

The service exposes metrics at `/actuator/prometheus`:

**Key Metrics:**
- `bitcoin_payment_requests_total` - Total payment requests
- `bitcoin_payment_requests_duration_seconds` - Request latency
- `bitcoin_payments_created` - Payments created counter
- `bitcoin_payments_confirmed` - Payments confirmed counter
- `hikaricp_connections_active` - Active DB connections
- `cache_gets` - Cache operations

### Viewing Metrics

```bash
# Port forward
kubectl port-forward svc/bitcoin-payment-service 8080:8080 -n easytrade

# View metrics
curl http://localhost:8080/actuator/prometheus

# Filter specific metrics
curl http://localhost:8080/actuator/prometheus | grep bitcoin_payment
```

### ServiceMonitor

If using Prometheus Operator:

```bash
# Verify ServiceMonitor created
kubectl get servicemonitor bitcoin-payment-service -n easytrade

# Check Prometheus targets
# Navigate to Prometheus UI → Status → Targets
# Look for: easytrade/bitcoin-payment-service/0
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n easytrade -l app=bitcoin-payment-service

# View pod logs
kubectl logs -n easytrade -l app=bitcoin-payment-service --tail=100

# Describe pod for events
kubectl describe pod -n easytrade -l app=bitcoin-payment-service
```

**Common Issues:**
- Database connection failure → Check `MSSQL_CONNECTIONSTRING` secret
- ImagePullBackOff → Build and push Docker image first
- CrashLoopBackOff → Check application logs for errors

### HPA Not Scaling

```bash
# Check HPA status
kubectl describe hpa bitcoin-payment-service -n easytrade

# Check metrics-server
kubectl get deployment metrics-server -n kube-system

# View current metrics
kubectl get hpa bitcoin-payment-service -n easytrade -o yaml
```

**Common Issues:**
- Metrics server not installed
- Resource requests not set
- Metrics not available yet (wait 2-3 minutes)

### High Latency

```bash
# Check pod resource usage
kubectl top pods -n easytrade -l app=bitcoin-payment-service

# Check if hitting resource limits
kubectl describe pod -n easytrade -l app=bitcoin-payment-service | grep -A 5 "Limits"

# Check HPA for scaling events
kubectl describe hpa bitcoin-payment-service -n easytrade
```

**Solutions:**
- Increase CPU/memory limits if hitting caps
- Lower HPA target utilization to scale earlier
- Check database performance
- Verify Redis cache is working

### Database Connection Issues

```bash
# Test database connectivity
kubectl run -it --rm debug --image=mcr.microsoft.com/mssql-tools --restart=Never -- \
  sqlcmd -S <db-host> -U <user> -P <password>

# Check connection pool metrics
kubectl port-forward svc/bitcoin-payment-service 8080:8080 -n easytrade
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

## Security

### Pod Security Context

Runs as non-root user (UID: 3369):

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 3369
  fsGroup: 3369
```

### Network Policies

Recommended network policy (not included):

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: bitcoin-payment-service
spec:
  podSelector:
    matchLabels:
      app: bitcoin-payment-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: broker-service
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: db
    ports:
    - protocol: TCP
      port: 1433
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
```

## Maintenance

### Rolling Update

```bash
# Update image
kubectl set image deployment/bitcoin-payment-service \
  bitcoin-payment-service=easytrade/bitcoin-payment-service:v1.1.0 \
  -n easytrade

# Watch rollout
kubectl rollout status deployment/bitcoin-payment-service -n easytrade

# Rollback if needed
kubectl rollout undo deployment/bitcoin-payment-service -n easytrade
```

### Scaling Manually

```bash
# Scale to specific replica count
kubectl scale deployment bitcoin-payment-service --replicas=5 -n easytrade

# Disable HPA for manual scaling
kubectl delete hpa bitcoin-payment-service -n easytrade
```

### Restart Pods

```bash
# Rolling restart
kubectl rollout restart deployment/bitcoin-payment-service -n easytrade

# Force delete pod (not recommended)
kubectl delete pod -n easytrade -l app=bitcoin-payment-service --force
```

## Cleanup

```bash
# Delete all resources
kubectl delete -f kubernetes-manifests/bitcoin-payment-service/ -n easytrade

# Or delete by label
kubectl delete all -n easytrade -l app=bitcoin-payment-service
```

## Related Documentation

- Service README: `src/bitcoin-payment-service/README.md`
- Architecture: `docs/BITCOIN_ARCHITECTURE.md`
- Database Schema: `src/db/sql-scripts/BITCOIN_SCHEMA.md`
- Helm Charts: `helm/easytrade/`
- Broker Integration: `src/broker-service/BITCOIN_INTEGRATION.md`

## Support

For issues or questions:
- Check pod logs: `kubectl logs -n easytrade -l app=bitcoin-payment-service`
- Check events: `kubectl get events -n easytrade`
- Review metrics in Prometheus/Grafana
- Check Dynatrace for distributed tracing
