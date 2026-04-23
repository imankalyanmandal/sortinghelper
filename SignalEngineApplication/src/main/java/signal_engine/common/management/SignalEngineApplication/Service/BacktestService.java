package signal_engine.common.management.SignalEngineApplication.Service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import signal_engine.common.management.SignalEngineApplication.Indicators.ATRIndicator;
import signal_engine.common.management.SignalEngineApplication.StrategyInterface.Strategy;
import signal_engine.common.management.SignalEngineApplication.StrategyInterfaceImpl.StrategyImplementation;
import signal_engine.common.management.SignalEngineApplication.model.BacktestResult;
import signal_engine.common.management.SignalEngineApplication.model.Candle;
import signal_engine.common.management.SignalEngineApplication.model.Trade;

@Service
@RequiredArgsConstructor
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private final Strategy strategy;

    // ── Configurable via application.properties ───────────────────────────
    @Value("${backtest.initial.capital:100000}")
    private double INITIAL_CAPITAL;

    @Value("${backtest.risk.per.trade:0.02}")
    private double RISK_PER_TRADE;          // 2% of capital risked per trade

    @Value("${backtest.atr.multiplier:2.0}")
    private double ATR_MULTIPLIER;          // stop = entry - (ATR_MULTIPLIER * ATR)

    @Value("${backtest.atr.period:14}")
    private int ATR_PERIOD;

    @Value("${backtest.take.profit.rr:2.0}")
    private double TAKE_PROFIT_RR;          // target = entry + (stop_distance * RR)

    @Value("${backtest.commission.rate:0.001}")
    private double COMMISSION_RATE;         // 0.1% per side

    @Value("${backtest.entry.slippage:0.0025}")
    private double ENTRY_SLIPPAGE;

    @Value("${backtest.exit.slippage:0.0025}")
    private double EXIT_SLIPPAGE;

    @Value("${backtest.cooldown.bars:5}")
    private int COOLDOWN_BARS;

    @Value("${backtest.max.consecutive.losses:2}")
    private int MAX_CONSECUTIVE_LOSSES;

    // Fallback fixed stop if ATR unavailable (3%)
    private static final double FALLBACK_STOP_PCT = 0.03;

    // ─────────────────────────────────────────────────────────────────────

    public BacktestResult run(String stockName, List<Candle> candles) {

        // ── 1. Validate ───────────────────────────────────────────────────
        validateCandles(candles);

        // ── 2. State ──────────────────────────────────────────────────────
        double capital    = INITIAL_CAPITAL;
        double maxCapital = INITIAL_CAPITAL;
        double maxDrawdown = 0;

        boolean positionOpen    = false;
        double  entryPrice      = 0;
        String  entryDate       = null;
        double  stopLossPrice   = 0;
        double  takeProfitPrice = 0;
        double  quantity        = 0;
        double  stopDistance    = 0;

        int lastStopIndex      = -1;
        int consecutiveLosses  = 0;

        List<Trade>  trades      = new ArrayList<>();
        List<Double> tradeReturns = new ArrayList<>();  // per-trade returns for Sharpe
        List<Double> dailyEquity  = new ArrayList<>();

        log.info("Starting backtest: {} | {} candles", stockName, candles.size());

        // ── 3. Main loop ──────────────────────────────────────────────────
        for (int i = 0; i < candles.size(); i++) {

            Candle candle     = candles.get(i);
            double closePrice = candle.getClose();
            double lowPrice   = candle.getLow();
            double highPrice  = candle.getHigh();
            String date       = candle.getDate();

            // Track total portfolio value (cash + mark-to-market open position)
            double portfolioValue = capital + (positionOpen ? quantity * closePrice : 0);
            dailyEquity.add(portfolioValue);

            // Update peak for drawdown
            if (portfolioValue > maxCapital) maxCapital = portfolioValue;
            double drawdown = maxCapital > 0 ? (maxCapital - portfolioValue) / maxCapital : 0;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;

            // ── Priority 1: Check exits ───────────────────────────────────
            if (positionOpen) {

                // Stop-loss hit intraday
                if (lowPrice <= stopLossPrice) {
                    double exitPrice = stopLossPrice * (1 - EXIT_SLIPPAGE);
                    Trade t = buildTrade(stockName, entryPrice, exitPrice,
                                        entryDate, date, quantity, "STOP_LOSS");
                    trades.add(t);
                    tradeReturns.add((exitPrice - entryPrice) / entryPrice);

                    capital      += exitPrice * quantity * (1 - COMMISSION_RATE);
                    positionOpen  = false;
                    lastStopIndex = i;
                    consecutiveLosses = t.getPnl() < 0 ? consecutiveLosses + 1 : 0;

                    log.debug("[{}] STOP_LOSS | exit={} pnl={}", date, exitPrice, t.getPnl());
                    continue;
                }

                // Take-profit hit intraday
                if (highPrice >= takeProfitPrice) {
                    double exitPrice = takeProfitPrice * (1 - EXIT_SLIPPAGE);
                    Trade t = buildTrade(stockName, entryPrice, exitPrice,
                                        entryDate, date, quantity, "TAKE_PROFIT");
                    trades.add(t);
                    tradeReturns.add((exitPrice - entryPrice) / entryPrice);

                    capital      += exitPrice * quantity * (1 - COMMISSION_RATE);
                    positionOpen  = false;
                    consecutiveLosses = t.getPnl() < 0 ? consecutiveLosses + 1 : 0;

                    log.debug("[{}] TAKE_PROFIT | exit={} pnl={}", date, exitPrice, t.getPnl());
                    continue;
                }
            }

            // ── Priority 2: Get signal ────────────────────────────────────
            String signal = strategy.generateSignal(candles, i);

            // ── Priority 3: BUY ───────────────────────────────────────────
            if ("BUY".equals(signal) && !positionOpen) {

                // Cooldown after stop-loss
                if (lastStopIndex >= 0 && (i - lastStopIndex) < COOLDOWN_BARS) continue;

                // Circuit breaker: too many consecutive losses
                if (consecutiveLosses >= MAX_CONSECUTIVE_LOSSES) {
                    if (lastStopIndex >= 0 && (i - lastStopIndex) >= COOLDOWN_BARS) {
                        consecutiveLosses = 0; // reset after cooling off
                    } else {
                        continue;
                    }
                }

                // ── ATR-based dynamic stop-loss ───────────────────────────
                // Read ATR published by strategy (avoids recomputing)
                Double atr = StrategyImplementation.currentATR.get();
                if (atr == null || atr <= 0) {
                    atr = ATRIndicator.calculate(candles, i, ATR_PERIOD);
                }

                double actualEntry = closePrice * (1 + ENTRY_SLIPPAGE);

                if (atr != null && atr > 0) {
                    stopDistance    = ATR_MULTIPLIER * atr;
                    stopLossPrice   = actualEntry - stopDistance;
                } else {
                    // Fallback to fixed % if ATR not yet available
                    stopDistance    = actualEntry * FALLBACK_STOP_PCT;
                    stopLossPrice   = actualEntry - stopDistance;
                }

                takeProfitPrice = actualEntry + (stopDistance * TAKE_PROFIT_RR);

                // ── Position sizing: risk fixed % of capital ──────────────
                double riskAmount = capital * RISK_PER_TRADE;
                quantity = Math.floor(riskAmount / stopDistance);

                if (quantity < 1) continue;

                double investmentCost = actualEntry * quantity * (1 + COMMISSION_RATE);
                if (capital < investmentCost) continue;

                positionOpen = true;
                entryPrice   = actualEntry;
                entryDate    = date;
                capital     -= investmentCost;

                log.debug("[{}] BUY | entry={} stop={} target={} qty={}",
                          date, actualEntry, stopLossPrice, takeProfitPrice, quantity);
            }

            // ── Priority 4: Strategy SELL ─────────────────────────────────
            else if ("SELL".equals(signal) && positionOpen) {

                double exitPrice = closePrice * (1 - EXIT_SLIPPAGE);
                Trade t = buildTrade(stockName, entryPrice, exitPrice,
                                     entryDate, date, quantity, "SELL_SIGNAL");
                trades.add(t);
                tradeReturns.add((exitPrice - entryPrice) / entryPrice);

                capital      += exitPrice * quantity * (1 - COMMISSION_RATE);
                positionOpen  = false;
                consecutiveLosses = t.getPnl() < 0 ? consecutiveLosses + 1 : 0;

                log.debug("[{}] SELL_SIGNAL | exit={} pnl={}", date, exitPrice, t.getPnl());
            }
        }

        // ── 4. Force-close open position at end of data ───────────────────
        if (positionOpen && !candles.isEmpty()) {
            Candle last      = candles.get(candles.size() - 1);
            double exitPrice = last.getClose() * (1 - EXIT_SLIPPAGE);
            Trade t = buildTrade(stockName, entryPrice, exitPrice,
                                 entryDate, last.getDate(), quantity, "END_OF_DATA");
            trades.add(t);
            tradeReturns.add((exitPrice - entryPrice) / entryPrice);
            capital += exitPrice * quantity * (1 - COMMISSION_RATE);
        }

        // ── 5. Metrics ────────────────────────────────────────────────────
        long wins        = trades.stream().filter(Trade::isWin).count();
        double winRate   = trades.isEmpty() ? 0 : (wins * 100.0 / trades.size());
        double totalProfit   = capital - INITIAL_CAPITAL;
        double returnPercent = (totalProfit / INITIAL_CAPITAL) * 100;

        double profitFactor = computeProfitFactor(trades);
        double avgWin       = computeAverageWin(trades);
        double avgLoss      = computeAverageLoss(trades);

        // Per-trade Sharpe (accurate for swing trading — avoids flat-day inflation)
        double sharpe = computePerTradeSharpe(tradeReturns);

        // Benchmark: buy-and-hold return over the same period
        double benchmarkReturn = computeBuyAndHold(candles);

        log.info("Backtest complete: {} | trades={} return={:.2f}% sharpe={:.2f} vs benchmark={:.2f}%",
                 stockName, trades.size(), returnPercent, sharpe, benchmarkReturn);

        BacktestResult result = BacktestResult.builder()
                .totalTrades(trades.size())
                .winRate(winRate)
                .finalCapital(capital)
                .totalProfit(totalProfit)
                .returnPercent(returnPercent)
                .maxDrawdown(maxDrawdown * 100)   // stored as fraction, returned as %
                .avgWinSize(avgWin)
                .avgLossSize(avgLoss)
                .sharpeRatio(sharpe)
                .benchmarkReturn(benchmarkReturn)
                .trades(trades)
                .build();

        result.setProfitFactor(profitFactor);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Trade buildTrade(String stock, double entry, double exit,
                             String entryDate, String exitDate,
                             double qty, String reason) {
        // Commission charged on both entry and exit legs
        double entryComm = entry * qty * COMMISSION_RATE;
        double exitComm  = exit  * qty * COMMISSION_RATE;
        double pnl = (exit - entry) * qty - entryComm - exitComm;

        return Trade.builder()
                .stock(stock)
                .entryDate(entryDate)
                .exitDate(exitDate)
                .entryPrice(entry)
                .exitPrice(exit)
                .quantity(qty)
                .pnl(pnl)
                .win(pnl > 0)
                .exitReason(reason)
                .build();
    }

    /**
     * Per-trade Sharpe Ratio.
     *
     * Why per-trade instead of daily equity:
     *   Swing trading holds positions for days to weeks. On non-trading days
     *   equity doesn't change, compressing the standard deviation and inflating
     *   the daily Sharpe by 3-5x. Per-trade Sharpe uses only the actual
     *   return of each trade, giving a realistic risk-adjusted metric.
     *
     * Annualised assuming 20 trades per year (typical swing frequency).
     */
    private double computePerTradeSharpe(List<Double> tradeReturns) {
        if (tradeReturns.size() < 2) return 0;

        double avg = tradeReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = tradeReturns.stream()
                .mapToDouble(r -> Math.pow(r - avg, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0;

        // Annualise: assume ~20 swing trades per year
        double tradesPerYear = 20.0;
        return (avg * tradesPerYear) / (stdDev * Math.sqrt(tradesPerYear));
    }

    /**
     * Buy-and-hold return: what you'd have made just buying on day 1
     * and selling on the last day. Used as benchmark.
     */
    private double computeBuyAndHold(List<Candle> candles) {
        if (candles.size() < 2) return 0;
        double first = candles.get(0).getClose();
        double last  = candles.get(candles.size() - 1).getClose();
        return ((last - first) / first) * 100;
    }

    private double computeProfitFactor(List<Trade> trades) {
        double wins   = trades.stream().filter(Trade::isWin).mapToDouble(Trade::getPnl).sum();
        double losses = trades.stream().filter(t -> !t.isWin()).mapToDouble(t -> Math.abs(t.getPnl())).sum();
        if (losses == 0) return wins > 0 ? Double.POSITIVE_INFINITY : 1.0;
        return wins / losses;
    }

    private double computeAverageWin(List<Trade> trades) {
        long count = trades.stream().filter(Trade::isWin).count();
        if (count == 0) return 0;
        return trades.stream().filter(Trade::isWin).mapToDouble(Trade::getPnl).sum() / count;
    }

    private double computeAverageLoss(List<Trade> trades) {
        long count = trades.stream().filter(t -> !t.isWin()).count();
        if (count == 0) return 0;
        return trades.stream().filter(t -> !t.isWin()).mapToDouble(Trade::getPnl).sum() / count;
    }

    private void validateCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Candles list cannot be null or empty");
        }
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (c == null || c.getDate() == null)
                throw new IllegalArgumentException("Null candle at index " + i);
            if (c.getClose() <= 0 || c.getOpen() <= 0 || c.getHigh() <= 0 || c.getLow() <= 0)
                throw new IllegalArgumentException("Invalid prices at index " + i + " date=" + c.getDate());
            if (c.getLow() > c.getHigh())
                throw new IllegalArgumentException("Low > High at index " + i + " date=" + c.getDate());
        }
    }
}