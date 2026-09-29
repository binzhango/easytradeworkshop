namespace EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;

public class BitcoinWalletResponse
{
    public int AccountId { get; set; }
    public string? WalletAddress { get; set; }
    public decimal Balance { get; set; }
    public DateTime? CreatedAt { get; set; }
}
