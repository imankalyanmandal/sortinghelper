package signal_engine.common.management.SignalEngineApplication.Indicators;

import java.util.List;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

public class BollingerBandsIndicator {

    /**
     * Calculate Bollinger Bands
     * Returns array: [lowerBand, middleBand (SMA), upperBand]
     */
    public static Double[] calculate(List<Candle> candles, int index, int period, double stdDevMultiplier) {
        
        if (index < period) {
            return null;  // Insufficient data
        }

        // Middle band = SMA
        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).getClose();
        }
        double sma = sum / period;

        // Calculate standard deviation
        double variance = 0;
        for (int i = index - period + 1; i <= index; i++) {
            double diff = candles.get(i).getClose() - sma;
            variance += diff * diff;
        }
        double stdDev = Math.sqrt(variance / period);

        // Bands
        double upperBand = sma + (stdDev * stdDevMultiplier);
        double lowerBand = sma - (stdDev * stdDevMultiplier);

        return new Double[]{lowerBand, sma, upperBand};
    }

    /**
     * Usage example:
     * 
     * Double[] bands = BollingerBandsIndicator.calculate(candles, index, 20, 2.0);
     * if (bands != null) {
     *     double lowerBand = bands[0];
     *     double middleBand = bands[1];
     *     double upperBand = bands[2];
     *     
     *     if (price < lowerBand) {
     *         return "BUY";  // Touched lower band
     *     }
     * }
     */
}

