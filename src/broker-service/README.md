# EasyTradeBrokerService

This service is used to manage accounts' balances and process trades.

## Technologies used

- .NET 8 (ASP.NET Core)
- Docker

## Local build instructions

```bash
docker build -t IMAGE_NAME .
docker run -d --name SERVICE_NAME IMAGE_NAME
```

## Endpoints or logic

### Swagger

---

Swagger endpoint is available at:

```bash
# when deployed with k8s
http://SOMEWHERE/broker-service/swagger
```

### Problem pattern

---

The problem patterns are toggled through [feature flag service](./feature-flag-service.md). The responses from the service are cached for **FEATURE_FLAG_CACHE_DURATION_S** or default value if env var not set.

#### Db not responding

When enabled, no new records will be added to Trade table, as they will fail. Problem pattern can be enabled using the api provided with the feature flag service.

#### High CPU usage

When enabled every request will be delayed by **HIGH_CPU_USAGE_REQUEST_DELAY_MS** or default value if env var not set. During this time Collatz conjecture will be calculated for random numbers on to add a significant load to cpu. It will be run on **HIGH_CPU_USAGE_CONCURRENCY** tasks.

#### Credit card validation

When enabled, the `cardNumber` field from deposit and withdraw request bodies is validated against the mainframe before the operation is processed. If the mainframe deems the card invalid, the request is rejected with a `400` response.

If the mainframe is unreachable, returns an error, or `MAINFRAME_SERVICE_URL` is not configured, the middleware fails open and the request proceeds normally. This means `MAINFRAME_SERVICE_URL` is only required when the flag is enabled.

| Environment variable | Description |
| -------------------- | ----------- |
| `MAINFRAME_SERVICE_URL` | Base URL of the mainframe service (e.g. `https://<mainframe-host>:<port>`). Only required when the flag is enabled. |

### Balance

---

#### `POST` **/v1/balance/{accountId}/deposit** `Deposit money to the account`

##### Parameters

| name         | type     | data type | description | source    |
| ------------ | -------- | --------- | ----------- | --------- |
| `accountId`  | required | int       | Account ID  | Path      |
| `amount`     | required | decimal   | Amount      | Body JSON |
| `name`       | required | string    | Name        | Body JSON |
| `address`    | required | string    | Address     | Body JSON |
| `email`      | required | string    | Email       | Body JSON |
| `cardNumber` | required | string    | Card number | Body JSON |
| `cardType`   | required | string    | Card type   | Body JSON |
| `cvv`        | required | string    | CVV         | Body JSON |

##### Responses

| http code | content-type       | response                                                        |
| --------- | ------------------ | --------------------------------------------------------------- |
| `200`     | `application/json` | `{"accountId": 1, "value": 23.6}`                               |
| `400`     | `application/json` | `{"code":"400","message":"Amount can't be lower that 0"}`       |
| `404`     | `application/json` | `{"code":"404","message":"Account with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "amount": 100,
  "name": "Name",
  "address": "Address",
  "email": "Email",
  "cardNumber": "Card Number",
  "cardType": "Card Type",
  "cvv": "123"
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/balance/1/deposit' \
-H 'accept: text/plain' \
-H 'Content-Type: application/json' \
-d '{
    "amount": 100,
    "name": "Name",
    "address": "Address",
    "email": "Email",
    "cardNumber": "Card Number",
    "cardType": "Card Type",
    "cvv": "123"
}'
```

---

#### `POST` **/v1/balance/{accountId}/withdraw** `Withdraw money to the account`

##### Parameters

| name         | type     | data type | description | source    |
| ------------ | -------- | --------- | ----------- | --------- |
| `accountId`  | required | int       | Account ID  | Path      |
| `amount`     | required | decimal   | Amount      | Body JSON |
| `name`       | required | string    | Name        | Body JSON |
| `address`    | required | string    | Address     | Body JSON |
| `email`      | required | string    | Email       | Body JSON |
| `cardNumber` | required | string    | Card number | Body JSON |
| `cardType`   | required | string    | Card type   | Body JSON |

##### Responses

| http code | content-type       | response                                                        |
| --------- | ------------------ | --------------------------------------------------------------- |
| `200`     | `application/json` | `{"accountId": 1, "value": 23.6}`                               |
| `400`     | `application/json` | `{"code":"400","message":"Amount can't be lower that 0"}`       |
| `404`     | `application/json` | `{"code":"404","message":"Account with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "amount": 10,
  "name": "Name",
  "address": "Address",
  "email": "Email",
  "cardNumber": "Card Number",
  "cardType": "Card Type"
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/balance/1/withdraw' \
-H 'accept: text/plain' \
-H 'Content-Type: application/json' \
-d '{
  "amount": 10,
  "name": "Name",
  "address": "Address",
  "email": "Email",
  "cardNumber": "Card Number",
  "cardType": "Card Type"
}'
```

---

#### `GET` **/v1/balance/{accountId}** `Get current balance of an account`

##### Parameters

| name        | type     | data type | description | source |
| ----------- | -------- | --------- | ----------- | ------ |
| `accountId` | required | int       | Account ID  | Path   |

##### Responses

| http code | content-type       | response                                                        |
| --------- | ------------------ | --------------------------------------------------------------- |
| `200`     | `application/json` | `{"accountId": 1, "value": 23.6}`                               |
| `404`     | `application/json` | `{"code":"404","message":"Account with id {id} doesn't exist"}` |

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/balance/1' \
-H 'accept: text/plain'
```

### Instrument

---

#### `GET` **/v1/instrument** `Get list of all available instruments`

##### Parameters

| name        | type     | data type | description | source |
| ----------- | -------- | --------- | ----------- | ------ |
| `accountId` | optional | int       | Account ID  | Query  |

##### Responses

| http code | content-type       | response  |
| --------- | ------------------ | --------- |
| `200`     | `application/json` | JSON body |

##### Example of response JSON body

```json
{
  "results": [
    {
      "id": 1,
      "code": "ETRAVE",
      "name": "EasyTravel",
      "description": "EasyTravel Incorporated",
      "productId": 1,
      "productName": "Share",
      "price": {
        "timestamp": "2023-07-24T13:44:22+00:00",
        "open": 139.34791667,
        "close": 139.38958333,
        "low": 137.94991929,
        "high": 140.74087808
      },
      "amount": 344
    },
    {
      "id": 2,
      "code": "EPLANE",
      "name": "EasyPlanes",
      "description": "EasyPlanes Worldwide",
      "productId": 2,
      "productName": "ETF",
      "price": {
        "timestamp": "2023-07-24T13:44:22+00:00",
        "open": 96.63777778,
        "close": 96.68222222,
        "low": 96.06399455,
        "high": 97.23283949
      },
      "amount": 966
    }
  ]
}
```

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/instrument?accountId=6' \
-H 'accept: text/plain'
```

### Bitcoin Payments

---

#### `POST` **/v1/bitcoin/{accountId}/payment** `Create Bitcoin payment for stock purchase`

##### Parameters

| name           | type     | data type | description   | source    |
| -------------- | -------- | --------- | ------------- | --------- |
| `accountId`    | required | int       | Account ID    | Path      |
| `amount`       | required | decimal   | Amount in BTC | Body JSON |
| `instrumentId` | required | int       | Instrument ID | Body JSON |
| `purpose`      | optional | string    | Purpose       | Body JSON |

##### Responses

| http code | content-type       | response                                                        |
| --------- | ------------------ | --------------------------------------------------------------- |
| `201`     | `application/json` | Payment details with wallet address and QR code                 |
| `400`     | `application/json` | `{"code":"400","message":"Invalid request"}`                    |
| `404`     | `application/json` | `{"code":"404","message":"Account with id {id} doesn't exist"}` |
| `502`     | `application/json` | `{"code":"502","message":"Bitcoin service unavailable"}`        |

##### Example of request JSON body

```json
{
  "amount": 0.005,
  "instrumentId": 1,
  "purpose": "stock_purchase"
}
```

##### Example of response JSON body

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

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/bitcoin/6/payment' \
-H 'accept: application/json' \
-H 'Content-Type: application/json' \
-d '{
  "amount": 0.005,
  "instrumentId": 1,
  "purpose": "stock_purchase"
}'
```

---

#### `GET` **/v1/bitcoin/payment/{paymentId}** `Get Bitcoin payment status`

##### Parameters

| name        | type     | data type | description | source |
| ----------- | -------- | --------- | ----------- | ------ |
| `paymentId` | required | string    | Payment ID  | Path   |

##### Responses

| http code | content-type       | response                                             |
| --------- | ------------------ | ---------------------------------------------------- |
| `200`     | `application/json` | Payment status and details                           |
| `404`     | `application/json` | `{"code":"404","message":"Payment not found"}`       |
| `502`     | `application/json` | `{"code":"502","message":"Bitcoin service unavailable"}` |

##### Example of response JSON body

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

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/bitcoin/payment/550e8400-e29b-41d4-a716-446655440000' \
-H 'accept: application/json'
```

---

#### `GET` **/v1/bitcoin/{accountId}/wallet** `Get Bitcoin wallet for account`

##### Parameters

| name        | type     | data type | description | source |
| ----------- | -------- | --------- | ----------- | ------ |
| `accountId` | required | int       | Account ID  | Path   |

##### Responses

| http code | content-type       | response                                                        |
| --------- | ------------------ | --------------------------------------------------------------- |
| `200`     | `application/json` | Wallet address and balance                                      |
| `404`     | `application/json` | `{"code":"404","message":"Wallet not found"}`                   |
| `502`     | `application/json` | `{"code":"502","message":"Bitcoin service unavailable"}`        |

##### Example of response JSON body

```json
{
  "accountId": 6,
  "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
  "balance": 0.123,
  "createdAt": "2026-09-29T10:00:00Z"
}
```

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/bitcoin/6/wallet' \
-H 'accept: application/json'
```

---

#### `GET` **/v1/bitcoin/payment/{paymentId}/confirmed** `Check if payment is confirmed`

##### Parameters

| name        | type     | data type | description | source |
| ----------- | -------- | --------- | ----------- | ------ |
| `paymentId` | required | string    | Payment ID  | Path   |

##### Responses

| http code | content-type       | response                                                 |
| --------- | ------------------ | -------------------------------------------------------- |
| `200`     | `application/json` | `true` or `false`                                        |
| `404`     | `application/json` | `{"code":"404","message":"Payment not found"}`           |
| `502`     | `application/json` | `{"code":"502","message":"Bitcoin service unavailable"}` |

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/bitcoin/payment/550e8400-e29b-41d4-a716-446655440000/confirmed' \
-H 'accept: application/json'
```

### Trades

---

#### `POST` **/v1/trade/buy** `Quick buy`

Parameters

| name           | type     | data type | description   | source    |
| -------------- | -------- | --------- | ------------- | --------- |
| `accountId`    | required | int       | Account ID    | Body JSON |
| `instrumentId` | required | int       | Instrument ID | Body JSON |
| `amount`       | required | decimal   | Amount        | Body JSON |

Responses

| http code | content-type       | response                                                                   |
| --------- | ------------------ | -------------------------------------------------------------------------- |
| `200`     | -                  | JSON Body                                                                  |
| `400`     | `application/json` | `{"code":"400","message":"Amount can't be lower that 0"}`                  |
| `404`     | `application/json` | `{"code":"404","message":"Account/Instrument with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 12.5
}
```

##### Example of response JSON body

```json
{
  "instrumentId": 1,
  "direction": "buy",
  "quantity": 12.5,
  "entryPrice": 140.22291667,
  "timestampOpen": "2023-08-30T14:05:37.6132984+00:00",
  "timestampClose": "2023-08-30T14:05:37.6132999+00:00",
  "tradeClosed": true,
  "transactionHappened": true,
  "status": "Instant Buy done."
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/trade/buy' \
-H 'accept: */*' \
-H 'Content-Type: application/json' \
-d '{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 12.5
}'
```

---

#### `POST` **/v1/trade/sell** `Quick sell`

Parameters

| name           | type     | data type | description   | source    |
| -------------- | -------- | --------- | ------------- | --------- |
| `accountId`    | required | int       | Account ID    | Body JSON |
| `instrumentId` | required | int       | Instrument ID | Body JSON |
| `amount`       | required | decimal   | Amount        | Body JSON |

Responses

| http code | content-type       | response                                                                   |
| --------- | ------------------ | -------------------------------------------------------------------------- |
| `200`     | -                  | JSON Body                                                                  |
| `400`     | `application/json` | `{"code":"400","message":"Amount can't be lower that 0"}`                  |
| `404`     | `application/json` | `{"code":"404","message":"Account/Instrument with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 12.5
}
```

##### Example of response JSON body

```json
{
  "instrumentId": 1,
  "direction": "sell",
  "quantity": 12.5,
  "entryPrice": 140.26458333,
  "timestampOpen": "2023-08-30T14:06:23.5028116+00:00",
  "timestampClose": "2023-08-30T14:06:23.5028126+00:00",
  "tradeClosed": true,
  "transactionHappened": true,
  "status": "Instant Sell done."
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/trade/sell' \
-H 'accept: */*' \
-H 'Content-Type: application/json' \
-d '{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 12.5
}'
```

---

#### `POST` **/v1/trade/long/buy** `Long buy`

Parameters

| name           | type     | data type | description       | source    |
| -------------- | -------- | --------- | ----------------- | --------- |
| `accountId`    | required | int       | Account ID        | Body JSON |
| `instrumentId` | required | int       | Instrument ID     | Body JSON |
| `amount`       | required | decimal   | Amount            | Body JSON |
| `duration`     | required | int       | Duration in hours | Body JSON |
| `price`        | required | decimal   | Price             | Body JSON |

Responses

| http code | content-type       | response                                                                   |
| --------- | ------------------ | -------------------------------------------------------------------------- |
| `200`     | -                  | JSON Body                                                                  |
| `400`     | `application/json` | `{"code":"400","message":"Amount/Duration/Price can't be lower that 0"}`   |
| `404`     | `application/json` | `{"code":"404","message":"Account/Instrument with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 5.5,
  "duration": 24,
  "price": 125.5
}
```

##### Example of response JSON body

```json
{
  "instrumentId": 1,
  "direction": "longbuy",
  "quantity": 5.5,
  "entryPrice": 125/5,
  "timestampOpen": "2023-08-30T14:09:25.5985529+00:00",
  "timestampClose": "2023-08-31T14:09:25.5985546+00:00",
  "tradeClosed": false,
  "transactionHappened": false,
  "status": "LongBuy registered."
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/trade/long/buy' \
-H 'accept: */*' \
-H 'Content-Type: application/json' \
-d '{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 5.5,
  "duration": 24,
  "price": 125.5
}'
```

---

#### `POST` **/v1/trade/long/sell** `Long sell`

Parameters

| name           | type     | data type | description       | source    |
| -------------- | -------- | --------- | ----------------- | --------- |
| `accountId`    | required | int       | Account ID        | Body JSON |
| `instrumentId` | required | int       | Instrument ID     | Body JSON |
| `amount`       | required | decimal   | Amount            | Body JSON |
| `duration`     | required | int       | Duration in hours | Body JSON |
| `price`        | required | decimal   | Price             | Body JSON |

Responses

| http code | content-type       | response                                                                   |
| --------- | ------------------ | -------------------------------------------------------------------------- |
| `200`     | -                  | JSON Body                                                                  |
| `400`     | `application/json` | `{"code":"400","message":"Amount/Duration/Price can't be lower that 0"}`   |
| `404`     | `application/json` | `{"code":"404","message":"Account/Instrument with id {id} doesn't exist"}` |

##### Example of request JSON body

```json
{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 5.5,
  "duration": 24,
  "price": 125.5
}
```

##### Example of response JSON body

```json
{
  "instrumentId": 1,
  "direction": "longsell",
  "quantity": 5.5,
  "entryPrice": 125.5,
  "timestampOpen": "2023-08-30T14:09:25.5985529+00:00",
  "timestampClose": "2023-08-31T14:09:25.5985546+00:00",
  "tradeClosed": false,
  "transactionHappened": false,
  "status": "LongSell registered."
}
```

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/trade/long/sell' \
-H 'accept: */*' \
-H 'Content-Type: application/json' \
-d '{
  "accountId": 6,
  "instrumentId": 1,
  "amount": 5.5,
  "duration": 24,
  "price": 125.5
}'
```

---

#### `POST` **/v1/trade/long/process** `Process all the long running transactions`

| http code | content-type | response       |
| --------- | ------------ | -------------- |
| `200`     | -            | Empty response |

##### Example cURL

```bash
curl -X 'POST' \
'http://localhost/broker-service/v1/trade/long/process' \
-H 'accept: */*' \
-d ''
```

---

#### `GET` **/v1/trade/{accountId}** `Get all trades for account`

##### Parameters

| name        | type     | data type | description                                     | source |
| ----------- | -------- | --------- | ----------------------------------------------- | ------ |
| `accountId` | required | int       | Account ID                                      | Path   |
| `count`     | optional | int       | Number of last trades (default value = 10)      | Query  |
| `page`      | optional | int       | Page (default value = 0)                        | Query  |
| `onlyOpen`  | optional | int       | Filter only open trades (default value = false) | Query  |
| `onlyLong`  | optional | int       | Filter only long trades (default value = false) | Query  |

##### Responses

| http code | content-type       | response  |
| --------- | ------------------ | --------- |
| `200`     | `application/json` | JSON body |

##### Example of response JSON body

```json
{
  "results": [
    {
      "instrumentId": 1,
      "direction": "longbuy",
      "quantity": 5.5,
      "entryPrice": 125.5,
      "timestampOpen": "2023-07-24T14:00:12+00:00",
      "timestampClose": "2023-07-25T14:00:12+00:00",
      "tradeClosed": false,
      "transactionHappened": false,
      "status": "LongBuy registered."
    },
    {
      "instrumentId": 1,
      "direction": "sell",
      "quantity": 12.5,
      "entryPrice": 139.80625,
      "timestampOpen": "2023-07-24T13:56:01+00:00",
      "timestampClose": "2023-07-24T13:56:01+00:00",
      "tradeClosed": true,
      "transactionHappened": true,
      "status": "Instant Sell done."
    }
  ]
}
```

##### Example cURL

```bash
curl -X 'GET' \
'http://localhost/broker-service/v1/trade/6?count=10&page=0&onlyOpen=false&onlyLong=false' \
-H 'accept: text/plain'
```
