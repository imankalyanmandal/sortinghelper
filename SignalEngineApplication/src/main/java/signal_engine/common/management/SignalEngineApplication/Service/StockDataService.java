package signal_engine.common.management.SignalEngineApplication.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

@Service
public class StockDataService {

    private static final Logger log = LoggerFactory.getLogger(StockDataService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yy");

    // Gap warning: if two consecutive trading days are more than this apart,
    // log a warning (could indicate missing data from Yahoo Finance)
    private static final int MAX_GAP_DAYS = 5;

    public List<Candle> loadFromCSV(String fileName) {

        // Security: only allow safe filenames
        if (!fileName.matches("[A-Z0-9_\\-]+\\.csv")) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }

        ClassPathResource resource = new ClassPathResource("data/" + fileName);
        if (!resource.exists()) {
            throw new RuntimeException("File not found in classpath: data/" + fileName);
        }

        List<Candle> candles = new ArrayList<>();
        int skipped = 0;

        log.info("Loading CSV: data/{}", fileName);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream()))) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }

                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = splitCSVLine(line);
                if (parts.length < 6) {
                    log.warn("Skipping short row: {}", line);
                    skipped++;
                    continue;
                }

                try {
                    String date   = parts[0].trim();
                    double open   = parseDouble(parts[1]);
                    double high   = parseDouble(parts[2]);
                    double low    = parseDouble(parts[3]);
                    double close  = parseDouble(parts[4]);
                    double volume = parseDouble(parts[5]);

                    candles.add(new Candle(date, open, high, low, close, volume));

                } catch (Exception ex) {
                    log.warn("Parse error — skipping row: {} | {}", line, ex.getMessage());
                    skipped++;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load CSV: " + fileName, e);
        }

        // Sort ascending by date
        candles.sort(Comparator.comparing(c -> parseDate(c.getDate())));

        // Check for data gaps
        detectGaps(candles, fileName);

        log.info("Loaded {} candles from {} ({} rows skipped)", candles.size(), fileName, skipped);
        return candles;
    }

    /**
     * Proper quoted-CSV parser.
     * Handles values like "1,004.45" which naive split(",") breaks.
     */
    private String[] splitCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private double parseDouble(String value) {
        if (value == null) return 0.0;
        String cleaned = value.replace(",", "").replace("\"", "").trim();
        if (cleaned.isEmpty()) return 0.0;
        return Double.parseDouble(cleaned);
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Cannot parse date: " + date, e);
        }
    }

    /**
     * Detects trading-day gaps in the loaded data.
     * A gap > MAX_GAP_DAYS between consecutive candles likely means
     * missing data which would corrupt indicator calculations.
     */
    private void detectGaps(List<Candle> candles, String fileName) {
        int gapCount = 0;
        for (int i = 1; i < candles.size(); i++) {
            LocalDate prev = parseDate(candles.get(i - 1).getDate());
            LocalDate curr = parseDate(candles.get(i).getDate());
            long gap = java.time.temporal.ChronoUnit.DAYS.between(prev, curr);
            if (gap > MAX_GAP_DAYS) {
                log.warn("[{}] Data gap detected: {} → {} ({} days). Indicators may be inaccurate.",
                         fileName,
                         candles.get(i - 1).getDate(),
                         candles.get(i).getDate(),
                         gap);
                gapCount++;
            }
        }
        if (gapCount == 0) {
            log.debug("[{}] No data gaps detected.", fileName);
        }
    }
}