package signal_engine.common.management.SignalEngineApplication.StrategyInterfaceImpl;

import java.util.List;

import org.springframework.stereotype.Component;

import signal_engine.common.management.SignalEngineApplication.Indicators.ATRIndicator;
import signal_engine.common.management.SignalEngineApplication.Indicators.RSIIndicator;
import signal_engine.common.management.SignalEngineApplication.Indicators.SMAIndicator;
import signal_engine.common.management.SignalEngineApplication.StrategyInterface.Strategy;
import signal_engine.common.management.SignalEngineApplication.model.Candle;

/**
 * Multi-mode swing trading strategy.
 *
 * Three BUY modes cover different market regimes so the strategy
 * generates meaningful signals in both uptrending and downtrending markets.
 *
 * All modes share two gatekeeping filters:
 *   1. Volume confirmation — current volume must be above 20-day average.
 *      Low-volume signals are far more likely to be false.
 *   2. Not making fresh lows — price must not be breaking below the
 *      prior 3 candles' lows. Avoids catching a falling knife.
 *
 * ATR is calculated and passed to BacktestService via a thread-local
 * so the service can set dynamic stop-losses without coupling to strategy internals.
 */
@Component
public class StrategyImplementation implements Strategy {

    // Periods
    private static final int RSI_PERIOD    = 14;
    private static final int SMA_FAST      = 20;
    private static final int SMA_SLOW      = 50;
    private static final int ATR_PERIOD    = 14;
    private static final int VOLUME_PERIOD = 20;

    // Volume filter: signal only when volume >= this multiple of average
    private static final double VOLUME_THRESHOLD = 1.0; // at or above average

    // ATR: exposed so BacktestService can use it for dynamic stop calculation
    public static final ThreadLocal<Double> currentATR = new ThreadLocal<>();

    @Override
    public String generateSignal(List<Candle> candles, int index) {

        // ── 1. Compute all indicators ─────────────────────────────────────
        Double rsi   = RSIIndicator.calculate(candles, index, RSI_PERIOD);
        Double sma20 = SMAIndicator.calculate(candles, index, SMA_FAST);
        Double sma50 = SMAIndicator.calculate(candles, index, SMA_SLOW);
        Double atr   = ATRIndicator.calculate(candles, index, ATR_PERIOD);
        Double volSma = SMAIndicator.calculateVolumeSMA(candles, index, VOLUME_PERIOD);

        // Publish ATR so BacktestService can read it for dynamic stop-loss
        currentATR.set(atr);

        // ── 2. Warmup guard ───────────────────────────────────────────────
        if (rsi == null || sma20 == null || sma50 == null) return "HOLD";

        double price  = candles.get(index).getClose();
        double volume = candles.get(index).getVolume();

        // ── 3. Shared entry gates (both modes must pass these) ────────────

        // Gate A: Volume confirmation — skip low-volume signals
        boolean volumeOk = volSma == null || volume >= volSma * VOLUME_THRESHOLD;

        // Gate B: Not making fresh lows (avoid catching falling knife)
        boolean notFreshLow = true;
        if (index >= 3) {
            double low3 = Math.min(candles.get(index - 1).getLow(),
                         Math.min(candles.get(index - 2).getLow(),
                                  candles.get(index - 3).getLow()));
            notFreshLow = price >= low3;
        }

        // ── 4. Trend regime ───────────────────────────────────────────────
        boolean uptrend   = sma20 > sma50;
        boolean downtrend = sma20 < sma50;

        // SMA20 slope: compare today vs 5 bars ago
        boolean sma20Rising  = false;
        boolean sma20Falling = false;
        if (index >= 5) {
            Double sma20prev = SMAIndicator.calculate(candles, index - 5, SMA_FAST);
            if (sma20prev != null) {
                sma20Rising  = sma20 > sma20prev;
                sma20Falling = sma20 < sma20prev;
            }
        }

        // ── 5. BUY logic ──────────────────────────────────────────────────

        // Mode 1: Uptrend pullback
        //   Stock is in uptrend (sma20 > sma50, sma20 rising),
        //   price has dipped below sma20 (pullback), RSI is oversold.
        //   Classic "buy the dip in an uptrend" setup.
        boolean uptrendPullback = uptrend
                && sma20Rising
                && rsi < 40
                && price < sma20
                && volumeOk
                && notFreshLow;

        // Mode 2: Extreme mean reversion in downtrend
        //   Stock is oversold to an extreme degree (RSI < 28),
        //   SMA20 has stopped falling (stabilising), not making new lows.
        //   Short counter-trend bounce trades only — strict conditions.
        boolean meanReversion = downtrend
                && !sma20Falling
                && rsi < 28
                && volumeOk
                && notFreshLow;

        // Mode 3: SMA20 crossover resumption
        //   Price was below SMA20 yesterday, is crossing back above today.
        //   Signals trend resumption after a brief dip. RSI must be neutral
        //   (40-60) — not overbought entry.
        boolean crossAbove = uptrend
                && price > sma20
                && index >= 1
                && candles.get(index - 1).getClose() < sma20
                && rsi > 40 && rsi < 60
                && volumeOk;

        if (uptrendPullback || meanReversion || crossAbove) {
            return "BUY";
        }

        // ── 6. SELL logic ─────────────────────────────────────────────────

        // Take profit: overbought
        if (rsi > 70) return "SELL";

        // Trend exit: below SMA20 + weakening momentum + SMA20 falling
        if (price < sma20 && rsi < 50 && sma20Falling) return "SELL";

        // Downtrend confirmed: both MAs aligned down, price below both
        if (downtrend && price < sma20 && rsi < 45) return "SELL";

        return "HOLD";
    }
}