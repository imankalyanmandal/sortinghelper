package signal_engine.common.management.SignalEngineApplication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Data
@Builder
public class Trade {

    private String stock;
    private String entryDate;
    private String exitDate;

    private double entryPrice;
    private double exitPrice;
    
    private double quantity;

    private double pnl;
    private boolean win;
    
    private String exitReason;

    // getters & setters
}