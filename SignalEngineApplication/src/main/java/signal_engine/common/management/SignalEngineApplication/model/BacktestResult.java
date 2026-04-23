package signal_engine.common.management.SignalEngineApplication.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class BacktestResult {

    private int    totalTrades;
    private double winRate;          // 0–100
    private double finalCapital;
    private List<Trade> trades;

    private double totalProfit;
    private double returnPercent;
    private double maxDrawdown;      // percentage e.g. 3.5 = 3.5%
    private double avgWinSize;
    private double avgLossSize;
    private double sharpeRatio;      // per-trade annualised

    /** Buy-and-hold return over same period — strategy benchmark */
    private double benchmarkReturn;

    /**
     * Profit factor capped at 99.99.
     * Double.POSITIVE_INFINITY is not valid JSON — cap it here.
     */
    private double profitFactor;

    public void setProfitFactor(double value) {
        this.profitFactor = (Double.isInfinite(value) || Double.isNaN(value))
                ? 99.99
                : Math.min(99.99, value);
    }

    // ── Computed metrics ──────────────────────────────────────────────────

    /** Alpha: how much the strategy outperformed buy-and-hold */
    public double getAlpha() {
        return returnPercent - benchmarkReturn;
    }

    /** Risk:Reward ratio of average win vs average loss */
    public double getRiskRewardRatio() {
        if (avgLossSize == 0) return 0;
        return avgWinSize / Math.abs(avgLossSize);
    }

    /** Expected profit per trade */
    public double getExpectancyPerTrade() {
        double winProb = winRate / 100.0;
        return (winProb * avgWinSize) + ((1 - winProb) * avgLossSize);
    }

    /**
     * Quality score 0–10.
     *
     * Dimensions:
     *   returnPercent  (0–3 pts) — 10% return = 3 pts, capped
     *   sharpeRatio    (0–3 pts) — per-trade Sharpe, capped at 3
     *   profitFactor   (0–2 pts) — PF of 3.0 = 2 pts
     *   alpha vs bench (0–1 pt)  — outperforms buy-and-hold
     *   trade count    penalty   — scaled down if < 10 trades
     */
    public double getQualityScore() {
        if (totalTrades == 0) return 0;

        double score = 0;

        if (returnPercent > 0)
            score += Math.min(3.0, returnPercent * 0.3);

        if (!Double.isNaN(sharpeRatio) && sharpeRatio > 0)
            score += Math.min(3.0, sharpeRatio);

        double pf = Double.isInfinite(profitFactor) || Double.isNaN(profitFactor) ? 0 : profitFactor;
        if (pf > 1.0)
            score += Math.min(2.0, (pf - 1.0) * 0.5);

        if (getAlpha() > 0)
            score += Math.min(1.0, getAlpha() * 0.1);

        // Penalise small sample — fewer than 10 trades is statistically weak
        if (totalTrades < 10)
            score *= (totalTrades / 10.0);

        return Math.min(10.0, Math.max(0, score));
    }

    public String getAssessment() {
        if (totalTrades == 0)  return "NO TRADES — strategy generated no signals";
        if (totalTrades < 5)   return "INSUFFICIENT DATA — too few trades to assess";
        if (returnPercent < 0) return "FAIL — strategy lost money";

        double q = getQualityScore();
        if (q < 2) return "POOR — needs improvement";
        if (q < 4) return "FAIR — acceptable but not exceptional";
        if (q < 6) return "GOOD — solid strategy";
        return              "EXCELLENT — production-ready";
    }
}