package signal_engine.common.management.SignalEngineApplication.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Nifty50ScanResult {

    private String symbol;
    private int    rank;

    // Core metrics
    private double returnPercent;
    private double winRate;
    private double sharpeRatio;
    private double profitFactor;
    private double maxDrawdown;
    private double qualityScore;

    // Trade summary
    private int    totalTrades;
    private double totalProfit;
    private double finalCapital;

    private String assessment;

    // Error case (if fetch or backtest failed for this stock)
    private boolean error;
    private String  errorMessage;
}
