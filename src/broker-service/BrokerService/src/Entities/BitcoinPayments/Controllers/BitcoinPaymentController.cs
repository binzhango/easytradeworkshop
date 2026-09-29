using EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;
using EasyTrade.BrokerService.Entities.BitcoinPayments.Service;
using EasyTrade.BrokerService.ExceptionHandling;
using Microsoft.AspNetCore.Mvc;

namespace EasyTrade.BrokerService.Entities.BitcoinPayments.Controllers;

[ApiController]
[Route("v1/bitcoin")]
[TypeFilter(typeof(BrokerExceptionFilter))]
public class BitcoinPaymentController : ControllerBase
{
    private readonly IBitcoinPaymentService _bitcoinPaymentService;
    private readonly ILogger<BitcoinPaymentController> _logger;

    public BitcoinPaymentController(
        IBitcoinPaymentService bitcoinPaymentService,
        ILogger<BitcoinPaymentController> logger)
    {
        _bitcoinPaymentService = bitcoinPaymentService;
        _logger = logger;
    }

    /// <summary>
    /// Create a Bitcoin payment for stock purchase
    /// </summary>
    /// <param name="accountId">Account ID</param>
    /// <param name="paymentDTO">Payment details</param>
    /// <returns>Payment information including wallet address and QR code</returns>
    [ProducesResponseType(typeof(BitcoinPaymentResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status502BadGateway)]
    [HttpPost("{accountId:int}/payment")]
    public async Task<ActionResult<BitcoinPaymentResponse>> CreatePayment(
        int accountId, 
        CreateBitcoinPaymentDTO paymentDTO)
    {
        _logger.LogInformation("Creating Bitcoin payment for account {AccountId}", accountId);
        
        var payment = await _bitcoinPaymentService.CreatePayment(accountId, paymentDTO);
        
        return Created($"/v1/bitcoin/payment/{payment.PaymentId}", payment);
    }

    /// <summary>
    /// Get Bitcoin payment status
    /// </summary>
    /// <param name="paymentId">Payment ID</param>
    /// <returns>Payment status and details</returns>
    [ProducesResponseType(typeof(BitcoinPaymentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status502BadGateway)]
    [HttpGet("payment/{paymentId}")]
    public async Task<ActionResult<BitcoinPaymentResponse>> GetPaymentStatus(string paymentId)
    {
        _logger.LogInformation("Getting payment status for {PaymentId}", paymentId);
        
        var payment = await _bitcoinPaymentService.GetPaymentStatus(paymentId);
        
        return Ok(payment);
    }

    /// <summary>
    /// Get Bitcoin wallet for account
    /// </summary>
    /// <param name="accountId">Account ID</param>
    /// <returns>Wallet address and balance</returns>
    [ProducesResponseType(typeof(BitcoinWalletResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status502BadGateway)]
    [HttpGet("{accountId:int}/wallet")]
    public async Task<ActionResult<BitcoinWalletResponse>> GetWallet(int accountId)
    {
        _logger.LogInformation("Getting Bitcoin wallet for account {AccountId}", accountId);
        
        var wallet = await _bitcoinPaymentService.GetWallet(accountId);
        
        return Ok(wallet);
    }

    /// <summary>
    /// Check if a Bitcoin payment is confirmed
    /// </summary>
    /// <param name="paymentId">Payment ID</param>
    /// <returns>Boolean indicating if payment is confirmed</returns>
    [ProducesResponseType(typeof(bool), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status404NotFound)]
    [ProducesResponseType(typeof(ErrorResponse), StatusCodes.Status502BadGateway)]
    [HttpGet("payment/{paymentId}/confirmed")]
    public async Task<ActionResult<bool>> IsPaymentConfirmed(string paymentId)
    {
        _logger.LogInformation("Checking if payment {PaymentId} is confirmed", paymentId);
        
        var isConfirmed = await _bitcoinPaymentService.IsPaymentConfirmed(paymentId);
        
        return Ok(isConfirmed);
    }
}
