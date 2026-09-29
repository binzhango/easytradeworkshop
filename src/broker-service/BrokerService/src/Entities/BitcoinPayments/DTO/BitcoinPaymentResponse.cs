namespace EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;

public class BitcoinPaymentResponse
{
    public string? PaymentId { get; set; }
    public string? WalletAddress { get; set; }
    public decimal Amount { get; set; }
    public string? Currency { get; set; }
    public string? Status { get; set; }
    public DateTime? ExpiresAt { get; set; }
    public string? QrCode { get; set; }
    public int Confirmations { get; set; }
    public string? TransactionHash { get; set; }
    public DateTime? CreatedAt { get; set; }
    public DateTime? ConfirmedAt { get; set; }
}
