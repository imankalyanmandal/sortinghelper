package signal_engine.common.management.SignalEngineApplication.Indicators;

import java.util.List;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

/**
 * Average True Range (ATR) Indicator.
 *
 * ATR measures market volatility — the average daily price range
 * adjusted for gaps between sessions.
 *
 * Used for:
 *   - Dynamic stop-loss: stopLoss = entryPrice - (ATR_MULTIPLIER * atr)
 *   - Position sizing: wider stops on volatile stocks = smaller position
 *
 * Higher ATR = more volatile stock = wider stop needed
 * Lower ATR  = calmer stock        = tighter stop is fine
 */
public class ATRIndicator {

    /**
     * Calculate ATR using simple average of true ranges.
     *
     * True Range = max of:
     *   (1) High - Low              (today's range)
     *   (2) |High - prevClose|      (gap-up day)
     *   (3) |Low  - prevClose|      (gap-down day)
     *
     * @param candles full candle list
     * @param index   current bar index
     * @param period  ATR period (typically 14)
     * @return ATR in price units (e.g. ₹25.50), or null if insufficient data
     */
    public static Double calculate(List<Candle> candles, int index, int period) {
        // Need previous candle for true range, so minimum is period + 1 bars
        if (index < period) return null;

        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double tr1 = curr.getHigh() - curr.getLow();
            double tr2 = Math.abs(curr.getHigh() - prev.getClose());
            double tr3 = Math.abs(curr.getLow()  - prev.getClose());

            sum += Math.max(tr1, Math.max(tr2, tr3));
        }

        return sum / period;
    }

    /**
     * Returns ATR as a percentage of current price.
     * Useful for comparing volatility across stocks at different price levels.
     *
     * e.g. ATR = ₹25 on a ₹500 stock → atrPercent = 5.0%
     *
     * @return ATR % of price, or null if insufficient data
     */
    public static Double calculatePercent(List<Candle> candles, int index, int period) {
        Double atr = calculate(candles, index, period);
        if (atr == null) return null;

        double price = candles.get(index).getClose();
        if (price <= 0) return null;

        return (atr / price) * 100.0;
    }
}