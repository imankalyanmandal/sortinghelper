package signal_engine.common.management.SignalEngineApplication.Service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import signal_engine.common.management.SignalEngineApplication.model.Layer2Result;

/**
 * Layer2Service — calls the Python microservice for fundamental,
 * sentiment, and concall analysis on a stock or list of stocks.
 */
@Service
@RequiredArgsConstructor
public class Layer2Service {

    private static final Logger log = LoggerFactory.getLogger(Layer2Service.class);

    private final RestTemplate restTemplate;

    @Value("${market.data.service.url:http://localhost:5000}")
    private String dataServiceUrl;

    /**
     * Run full Layer 2 analysis on a single stock.
     *
     * @param symbol NSE stock symbol e.g. TECHM
     * @return Layer2Result with composite score and pass/fail verdict
     */
    public Layer2Result analyse(String symbol) {
        String url = dataServiceUrl + "/layer2/analyse?symbol=" + symbol;
        log.info("[Layer2] Analysing {}", symbol);

        try {
            ResponseEntity<Layer2Result> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, Layer2Result.class);

            Layer2Result result = response.getBody();
            if (result == null) throw new RuntimeException("Empty Layer 2 response for " + symbol);

            log.info("[Layer2] {} → score={} pass={}",
                     symbol, result.getCompositeScore(), result.isLayer2Pass());
            return result;

        } catch (ResourceAccessException e) {
            throw new RuntimeException(
                "Market data service not running. Start with: python market_service.py", e);
        } catch (Exception e) {
            throw new RuntimeException("Layer 2 analysis failed for " + symbol + ": " + e.getMessage(), e);
        }
    }

    /**
     * Run Layer 2 scan across multiple stocks (Layer 1 shortlist).
     *
     * @param symbols list of NSE symbols
     * @return map with "total", "passed", "rejected", "results" keys
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> scan(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols list cannot be empty");
        }

        String symbolParam = String.join(",", symbols);
        String url = dataServiceUrl + "/layer2/scan?symbols=" + symbolParam;

        log.info("[Layer2] Scanning {} stocks: {}", symbols.size(), symbols);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) throw new RuntimeException("Empty Layer 2 scan response");

            int passed   = (int) body.getOrDefault("passed", 0);
            int rejected = (int) body.getOrDefault("rejected", 0);
            log.info("[Layer2] Scan complete — {} passed, {} rejected", passed, rejected);

            return body;

        } catch (ResourceAccessException e) {
            throw new RuntimeException(
                "Market data service not running. Start with: python market_service.py", e);
        } catch (Exception e) {
            throw new RuntimeException("Layer 2 scan failed: " + e.getMessage(), e);
        }
    }
}
