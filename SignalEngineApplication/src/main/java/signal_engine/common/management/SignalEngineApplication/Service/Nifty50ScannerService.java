package signal_engine.common.management.SignalEngineApplication.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import signal_engine.common.management.SignalEngineApplication.model.BacktestResult;
import signal_engine.common.management.SignalEngineApplication.model.Candle;
import signal_engine.common.management.SignalEngineApplication.model.Nifty50ScanResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class Nifty50ScannerService {

    private final MarketDataClient marketDataClient;
    private final BacktestService  backtestService;

    /**
     * Runs the backtest strategy across all Nifty 50 stocks
     * and returns results ranked by returnPercent (highest first).
     *
     * @param period   Data period: 1mo | 3mo | 6mo | 1y | 2y | 5y
     * @param exchange NS or BO
     * @param topN     If > 0, returns only the top N results. 0 = return all 50.
     */
    public List<Nifty50ScanResult> scanAll(String period, String exchange, int topN) {

        // 1. Get symbol list from Python service
        List<String> symbols = marketDataClient.fetchNifty50Symbols();
        log.info("Starting Nifty 50 scan: {} symbols, period={}", symbols.size(), period);

        List<Nifty50ScanResult> results = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);

        // 2. Loop every symbol — sequential to respect Yahoo Finance rate limits
        for (String symbol : symbols) {
            int current = done.incrementAndGet();
            log.info("[{}/{}] Processing {}", current, symbols.size(), symbol);

            Nifty50ScanResult result = runSingleStock(symbol, period, exchange);
            results.add(result);

            // Small delay to avoid hammering Yahoo Finance
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        // 3. Sort: profitable stocks first (by returnPercent desc),
        //    then failed stocks at the bottom (by errorMessage)
        results.sort(Comparator
                .<Nifty50ScanResult>comparingInt(r -> r.isError() ? 1 : 0)
                .thenComparingDouble(r -> -r.getReturnPercent()));

        // 4. Assign ranks (errors get rank 0)
        int rank = 1;
        for (Nifty50ScanResult r : results) {
            r.setRank(r.isError() ? 0 : rank++);
        }

        log.info("Scan complete. {} succeeded, {} failed.",
                results.stream().filter(r -> !r.isError()).count(),
                results.stream().filter(Nifty50ScanResult::isError).count());

        // 5. Return top N if requested
        if (topN > 0) {
            return results.stream()
                    .filter(r -> !r.isError())
                    .limit(topN)
                    .collect(java.util.stream.Collectors.toList());
        }

        return results;
    }

    private Nifty50ScanResult runSingleStock(String symbol, String period, String exchange) {
        try {
            List<Candle> candles = marketDataClient.fetchCandles(symbol, period, exchange);
            BacktestResult backtest = backtestService.run(symbol, candles);

            return Nifty50ScanResult.builder()
                    .symbol(symbol)
                    .returnPercent(backtest.getReturnPercent())
                    .winRate(backtest.getWinRate())
                    .sharpeRatio(backtest.getSharpeRatio())
                    .profitFactor(backtest.getProfitFactor())
                    .maxDrawdown(backtest.getMaxDrawdown())
                    .qualityScore(backtest.getQualityScore())
                    .totalTrades(backtest.getTotalTrades())
                    .totalProfit(backtest.getTotalProfit())
                    .finalCapital(backtest.getFinalCapital())
                    .assessment(backtest.getAssessment())
                    .error(false)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to process {}: {}", symbol, e.getMessage());
            return Nifty50ScanResult.builder()
                    .symbol(symbol)
                    .error(true)
                    .errorMessage(e.getMessage())
                    .assessment("ERROR")
                    .build();
        }
    }
}
