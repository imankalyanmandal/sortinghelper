package signal_engine.common.management.SignalEngineApplication.Indicators;

import java.util.List;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

/**
 * RSI Indicator — Wilder's Smoothed Moving Average method.
 *
 * Why Wilder's smoothing instead of simple average:
 *   Simple average RSI recomputes from scratch every bar — it is
 *   path-independent and therefore historically inaccurate compared to
 *   how RSI behaves on real charting platforms (TradingView, Zerodha etc.)
 *   Wilder's method carries forward the previous avgGain/avgLoss, giving
 *   a result that matches industry-standard RSI values.
 *
 * Warmup:
 *   Requires (period + period) bars minimum — the first `period` bars seed
 *   the initial simple average; subsequent bars apply the smoothing formula.
 *   Returns null until warmup is complete.
 */
public class RSIIndicator {

    /**
     * Calculate Wilder-smoothed RSI.
     *
     * @param candles full candle list
     * @param index   current bar index
     * @param period  RSI period (typically 14)
     * @return RSI value 0-100, or null if insufficient data
     */
    public static Double calculate(List<Candle> candles, int index, int period) {

        // Need at least (period + 1) bars to compute first change,
        // plus (period - 1) more to seed the initial average = 2*period bars total.
        if (index < period * 2) {
            return null;
        }

        // ── Seed: simple average of first `period` changes ────────────────
        double seedGain = 0;
        double seedLoss = 0;
        int seedStart = index - period * 2 + 1;

        for (int i = seedStart + 1; i <= seedStart + period; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) seedGain += change;
            else            seedLoss += Math.abs(change);
        }

        double avgGain = seedGain / period;
        double avgLoss = seedLoss / period;

        // ── Wilder smoothing for remaining bars up to index ───────────────
        for (int i = seedStart + period + 1; i <= index; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            double gain   = change > 0 ? change : 0;
            double loss   = change < 0 ? Math.abs(change) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}