package signal_engine.common.management.SignalEngineApplication.Indicators;

import java.util.List;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

/**
 * Moving Average Indicators.
 *
 * Provides SMA on price and on volume (needed for volume confirmation filter).
 * EMA uses iterative calculation — no recursion, no stack overflow risk.
 */
public class SMAIndicator {

    /**
     * Simple Moving Average of closing price.
     *
     * @return SMA value, or null if index < period - 1
     */
    public static Double calculate(List<Candle> candles, int index, int period) {
        if (index < period - 1) return null;

        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).getClose();
        }
        return sum / period;
    }

    /**
     * Simple Moving Average of volume.
     * Used to confirm that a BUY signal has above-average participation.
     *
     * @return volume SMA, or null if insufficient data
     */
    public static Double calculateVolumeSMA(List<Candle> candles, int index, int period) {
        if (index < period - 1) return null;

        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).getVolume();
        }
        return sum / period;
    }

    /**
     * Exponential Moving Average — iterative (no recursion).
     *
     * Seeds from SMA at index == period - 1, then applies
     * EMA = price * k + prevEMA * (1 - k)  where k = 2 / (period + 1).
     *
     * @return EMA value, or null if insufficient data
     */
    public static Double calculateEMA(List<Candle> candles, int index, int period) {
        if (index < period - 1) return null;

        double k = 2.0 / (period + 1);

        // Seed EMA with SMA of first `period` closes
        double ema = 0;
        for (int i = 0; i < period; i++) {
            ema += candles.get(i).getClose();
        }
        ema /= period;

        // Iterate forward from period to index
        for (int i = period; i <= index; i++) {
            ema = candles.get(i).getClose() * k + ema * (1 - k);
        }

        return ema;
    }
}