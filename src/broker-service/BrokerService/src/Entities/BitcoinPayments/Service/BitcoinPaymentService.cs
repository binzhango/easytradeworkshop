using EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;
using EasyTrade.BrokerService.ExceptionHandling.Exceptions;
using System.Text;
using System.Text.Json;

namespace EasyTrade.BrokerService.Entities.BitcoinPayments.Service;

public class BitcoinPaymentService : IBitcoinPaymentService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<BitcoinPaymentService> _logger;
    private readonly string _bitcoinServiceUrl;

    public BitcoinPaymentService(
        IHttpClientFactory httpClientFactory,
        ILogger<BitcoinPaymentService> logger,
        IConfiguration configuration)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _bitcoinServiceUrl = configuration["BitcoinPaymentService:Url"] 
            ?? Environment.GetEnvironmentVariable("BITCOIN_PAYMENT_SERVICE_URL")
            ?? "http://bitcoin-payment-service:8080";
    }

    public async Task<BitcoinPaymentResponse> CreatePayment(int accountId, CreateBitcoinPaymentDTO paymentDTO)
    {
        _logger.LogInformation("Creating Bitcoin payment for account {AccountId}, amount: {Amount}", 
            accountId, paymentDTO.Amount);

        var client = _httpClientFactory.CreateClient("BitcoinPaymentService");
        client.BaseAddress = new Uri(_bitcoinServiceUrl);

        var requestBody = new
        {
            accountId,
            amount = paymentDTO.Amount,
            currency = "BTC",
            purpose = paymentDTO.Purpose ?? "stock_purchase",
            metadata = new
            {
                instrumentId = paymentDTO.InstrumentId,
                timestamp = DateTime.UtcNow
            }
        };

        var jsonContent = JsonSerializer.Serialize(requestBody);
        var content = new StringContent(jsonContent, Encoding.UTF8, "application/json");

        try
        {
            var response = await client.PostAsync("/v1/payments", content);

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogError("Bitcoin payment service error: {StatusCode} - {Error}", 
                    response.StatusCode, errorContent);
                throw new ExternalServiceException("Failed to create Bitcoin payment");
            }

            var responseContent = await response.Content.ReadAsStringAsync();
            var apiResponse = JsonSerializer.Deserialize<StandardResponse<BitcoinPaymentResponse>>(responseContent, 
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

            if (apiResponse?.Results == null)
            {
                throw new ExternalServiceException("Invalid response from Bitcoin payment service");
            }

            return apiResponse.Results;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "HTTP error calling Bitcoin payment service");
            throw new ExternalServiceException("Bitcoin payment service unavailable", ex);
        }
    }

    public async Task<BitcoinPaymentResponse> GetPaymentStatus(string paymentId)
    {
        _logger.LogInformation("Getting Bitcoin payment status for {PaymentId}", paymentId);

        var client = _httpClientFactory.CreateClient("BitcoinPaymentService");
        client.BaseAddress = new Uri(_bitcoinServiceUrl);

        try
        {
            var response = await client.GetAsync($"/v1/payments/{paymentId}");

            if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                throw new NotFoundException($"Payment {paymentId} not found");
            }

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogError("Bitcoin payment service error: {StatusCode} - {Error}", 
                    response.StatusCode, errorContent);
                throw new ExternalServiceException("Failed to get payment status");
            }

            var responseContent = await response.Content.ReadAsStringAsync();
            var apiResponse = JsonSerializer.Deserialize<StandardResponse<BitcoinPaymentResponse>>(responseContent, 
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

            if (apiResponse?.Results == null)
            {
                throw new ExternalServiceException("Invalid response from Bitcoin payment service");
            }

            return apiResponse.Results;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "HTTP error calling Bitcoin payment service");
            throw new ExternalServiceException("Bitcoin payment service unavailable", ex);
        }
    }

    public async Task<BitcoinWalletResponse> GetWallet(int accountId)
    {
        _logger.LogInformation("Getting Bitcoin wallet for account {AccountId}", accountId);

        var client = _httpClientFactory.CreateClient("BitcoinPaymentService");
        client.BaseAddress = new Uri(_bitcoinServiceUrl);

        try
        {
            var response = await client.GetAsync($"/v1/wallets/{accountId}");

            if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                throw new NotFoundException($"Wallet for account {accountId} not found");
            }

            if (!response.IsSuccessStatusCode)
            {
                var errorContent = await response.Content.ReadAsStringAsync();
                _logger.LogError("Bitcoin payment service error: {StatusCode} - {Error}", 
                    response.StatusCode, errorContent);
                throw new ExternalServiceException("Failed to get wallet");
            }

            var responseContent = await response.Content.ReadAsStringAsync();
            var apiResponse = JsonSerializer.Deserialize<StandardResponse<BitcoinWalletResponse>>(responseContent, 
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true });

            if (apiResponse?.Results == null)
            {
                throw new ExternalServiceException("Invalid response from Bitcoin payment service");
            }

            return apiResponse.Results;
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "HTTP error calling Bitcoin payment service");
            throw new ExternalServiceException("Bitcoin payment service unavailable", ex);
        }
    }

    public async Task<bool> IsPaymentConfirmed(string paymentId)
    {
        var payment = await GetPaymentStatus(paymentId);
        return payment.Status?.ToUpper() == "CONFIRMED";
    }

    private class StandardResponse<T>
    {
        public int Status { get; set; }
        public string? Message { get; set; }
        public T? Results { get; set; }
    }
}
