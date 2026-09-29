using System.ComponentModel.DataAnnotations;

namespace EasyTrade.BrokerService.Entities.BitcoinPayments.DTO;

public class CreateBitcoinPaymentDTO
{
    [Required]
    [Range(0.00000001, double.MaxValue, ErrorMessage = "Amount must be positive")]
    public decimal Amount { get; set; }

    [Required]
    public int InstrumentId { get; set; }

    public string? Purpose { get; set; } = "stock_purchase";
}
