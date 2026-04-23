package signal_engine.common.management.SignalEngineApplication.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import signal_engine.common.management.SignalEngineApplication.Service.BacktestService;
import signal_engine.common.management.SignalEngineApplication.Service.Layer2Service;
import signal_engine.common.management.SignalEngineApplication.Service.MarketDataClient;
import signal_engine.common.management.SignalEngineApplication.Service.Nifty50ScannerService;
import signal_engine.common.management.SignalEngineApplication.Service.StockDataService;
import signal_engine.common.management.SignalEngineApplication.model.BacktestResult;
import signal_engine.common.management.SignalEngineApplication.model.Candle;
import signal_engine.common.management.SignalEngineApplication.model.Layer2Result;
import signal_engine.common.management.SignalEngineApplication.model.Nifty50ScanResult;

@RestController
@RequestMapping("/api/v1/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService        backtestService;
    private final StockDataService       stockDataService;
    private final MarketDataClient       marketDataClient;
    private final Nifty50ScannerService  nifty50ScannerService;
    private final Layer2Service          layer2Service;

    // ─────────────────────────────────────────────────────────────────────
    // Layer 1 — single stock backtest (live data)
    // GET /api/v1/backtest/run?stock=TECHM&period=5y
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/run")
    public ResponseEntity<?> runBacktest(
            @RequestParam String stock,
            @RequestParam(defaultValue = "1y")  String period,
            @RequestParam(defaultValue = "NS")  String exchange
    ) {
        if (!isValidSymbol(stock))      return badRequest("Invalid symbol");
        if (!isValidPeriod(period))     return badRequest("Invalid period. Use: 1mo, 3mo, 6mo, 1y, 2y, 5y");
        if (!isValidExchange(exchange)) return badRequest("Invalid exchange. Use NS or BO");

        try {
            List<Candle> candles = marketDataClient.fetchCandles(stock, period, exchange);
            return ResponseEntity.ok(backtestService.run(stock, candles));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layer 1 — single stock backtest (CSV fallback)
    // GET /api/v1/backtest/run-csv?stock=INFY&fileName=INFY.csv
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/run-csv")
    public ResponseEntity<?> runBacktestFromCSV(
            @RequestParam String stock,
            @RequestParam String fileName
    ) {
        if (!fileName.matches("[A-Z0-9_]+\\.csv"))
            return badRequest("Invalid file name");

        try {
            List<Candle> candles = stockDataService.loadFromCSV(fileName);
            return ResponseEntity.ok(backtestService.run(stock, candles));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layer 1 — Nifty 50 full scan
    // GET /api/v1/backtest/nifty50/scan?period=5y
    // GET /api/v1/backtest/nifty50/scan?period=5y&topN=10
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/nifty50/scan")
    public ResponseEntity<?> scanNifty50(
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "NS") String exchange,
            @RequestParam(defaultValue = "0")  int topN
    ) {
        if (!isValidPeriod(period))     return badRequest("Invalid period");
        if (!isValidExchange(exchange)) return badRequest("Invalid exchange");
        if (topN < 0 || topN > 50)     return badRequest("topN must be 0-50");

        try {
            return ResponseEntity.ok(nifty50ScannerService.scanAll(period, exchange, topN));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layer 1 — top N performers shorthand
    // GET /api/v1/backtest/nifty50/top?topN=10&period=5y
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/nifty50/top")
    public ResponseEntity<?> topNifty50(
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "NS") String exchange,
            @RequestParam(defaultValue = "10") int topN
    ) {
        if (!isValidPeriod(period))     return badRequest("Invalid period");
        if (!isValidExchange(exchange)) return badRequest("Invalid exchange");
        if (topN < 1 || topN > 50)     return badRequest("topN must be 1-50");

        try {
            return ResponseEntity.ok(nifty50ScannerService.scanAll(period, exchange, topN));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layer 2 — single stock full analysis
    // GET /api/v1/backtest/layer2/analyse?stock=TECHM
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/layer2/analyse")
    public ResponseEntity<?> layer2Analyse(
            @RequestParam String stock
    ) {
        if (!isValidSymbol(stock)) return badRequest("Invalid symbol");

        try {
            Layer2Result result = layer2Service.analyse(stock);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layer 2 — scan a comma-separated list of stocks
    // GET /api/v1/backtest/layer2/scan?stocks=TECHM,POWERGRID,HDFCBANK
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/layer2/scan")
    public ResponseEntity<?> layer2Scan(
            @RequestParam String stocks
    ) {
        List<String> symbols = Arrays.stream(stocks.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (symbols.isEmpty())    return badRequest("No valid symbols provided");
        if (symbols.size() > 20) return badRequest("Maximum 20 symbols per scan");

        for (String s : symbols) {
            if (!isValidSymbol(s)) return badRequest("Invalid symbol: " + s);
        }

        try {
            Map<String, Object> result = layer2Service.scan(symbols);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Combined pipeline: Layer 1 top-N → auto-feed into Layer 2
    // GET /api/v1/backtest/pipeline/run?period=5y&topN=8
    //
    // This is the main swing trading workflow:
    //   1. Run Nifty 50 backtest scan (Layer 1)
    //   2. Take top N by quality score (default 8)
    //   3. Run Layer 2 on those stocks
    //   4. Return Layer 2 results sorted by composite score
    // ─────────────────────────────────────────────────────────────────────
    @GetMapping("/pipeline/run")
    public ResponseEntity<?> runPipeline(
            @RequestParam(defaultValue = "5y") String period,
            @RequestParam(defaultValue = "NS") String exchange,
            @RequestParam(defaultValue = "8")  int topN
    ) {
        if (!isValidPeriod(period))     return badRequest("Invalid period");
        if (!isValidExchange(exchange)) return badRequest("Invalid exchange");
        if (topN < 1 || topN > 20)     return badRequest("topN must be 1-20");

        try {
            // Step 1: Layer 1 scan → get shortlist
            List<Nifty50ScanResult> layer1Results =
                    nifty50ScannerService.scanAll(period, exchange, topN);

            List<String> shortlist = layer1Results.stream()
                    .filter(r -> !r.isError())
                    .map(Nifty50ScanResult::getSymbol)
                    .collect(Collectors.toList());

            if (shortlist.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "message", "Layer 1 produced no qualifying stocks",
                    "layer1_results", layer1Results
                ));
            }

            // Step 2: Layer 2 scan on shortlist
            Map<String, Object> layer2Results = layer2Service.scan(shortlist);

            return ResponseEntity.ok(Map.of(
                "pipeline", "layer1 → layer2",
                "period",   period,
                "layer1_shortlist", shortlist,
                "layer2",   layer2Results
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private boolean isValidSymbol(String s) {
        return s != null && s.matches("[A-Z][A-Z0-9\\-&]{0,19}");
    }
    private boolean isValidPeriod(String p) {
        return p != null && p.matches("1mo|3mo|6mo|1y|2y|5y");
    }
    private boolean isValidExchange(String e) {
        return "NS".equals(e) || "BO".equals(e);
    }
    private ResponseEntity<String> badRequest(String msg) {
        return ResponseEntity.badRequest().body(msg);
    }
}