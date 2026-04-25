package signal_engine.common.management.SignalEngineApplication.model;

/**
 * Strictness level for Layer 1 live scan gate thresholds.
 *
 * Only affects Layer 1 (indicator gates + scoring).
 * Layer 2 (LLM analysis) and Layer 3 (S/R refinement) are identical across all levels.
 *
 * STRICT   — few signals, high precision. For experienced traders.
 * MODERATE — balanced. Recommended for most users.
 * RELAXED  — more signals, lower precision. Good for learning / paper trading.
 */
public enum ScanStrictness {

    STRICT(
        // Description
        "High precision — 1 to 3 signals per scan",
        // SMA50 slope required as hard gate
        true,
        // ATR bounds
        1.0, 6.0,
        // HH+HL structure required as hard gate
        true,
        // Max stop risk %
        4.0,
        // Pullback band (% distance from SMA20)
        0.02,
        // Near resistance threshold
        5.0,
        // Pullback RSI window
        35, 50,
        // Breakout RSI window
        55, 65,
        // Volume minimums
        1.5, 2.0,
        // Minimum setup score
        50
    ),

    MODERATE(
        "Balanced — 3 to 6 signals per scan",
        // SMA50 slope — soft bonus, not a hard gate
        false,
        // ATR bounds — slightly relaxed
        0.8, 7.0,
        // HH+HL — scoring bonus only, not gate
        false,
        // Max stop risk %
        5.0,
        // Pullback band
        0.03,
        // Near resistance
        5.0,
        // Pullback RSI — wider window
        30, 55,
        // Breakout RSI
        50, 70,
        // Volume — relaxed
        1.2, 1.5,
        // Min score
        40
    ),

    RELAXED(
        "More signals — 5 to 10 per scan. Recommended for paper trading only.",
        // SMA50 slope — not a gate
        false,
        // ATR bounds — wide
        0.5, 8.0,
        // HH+HL — not a gate
        false,
        // Max stop risk %
        7.0,
        // Pullback band — generous
        0.05,
        // Near resistance
        5.0,
        // Pullback RSI — very wide
        25, 60,
        // Breakout RSI
        45, 75,
        // Volume — almost no requirement
        0.9, 1.2,
        // Min score
        30
    );

    // ── Fields ────────────────────────────────────────────────────────────────
    public final String  description;
    public final boolean sma50SlopeGate;
    public final double  atrPctMin;
    public final double  atrPctMax;
    public final boolean hhHlGate;
    public final double  stopMaxPct;
    public final double  pullbackBand;
    public final double  resistancePct;
    public final double  pullbackRsiLow;
    public final double  pullbackRsiHigh;
    public final double  breakoutRsiLow;
    public final double  breakoutRsiHigh;
    public final double  volPullbackMin;
    public final double  volBreakoutMin;
    public final int     minSetupScore;

    ScanStrictness(
            String description,
            boolean sma50SlopeGate,
            double atrPctMin, double atrPctMax,
            boolean hhHlGate,
            double stopMaxPct,
            double pullbackBand,
            double resistancePct,
            double pullbackRsiLow, double pullbackRsiHigh,
            double breakoutRsiLow, double breakoutRsiHigh,
            double volPullbackMin, double volBreakoutMin,
            int minSetupScore
    ) {
        this.description     = description;
        this.sma50SlopeGate  = sma50SlopeGate;
        this.atrPctMin       = atrPctMin;
        this.atrPctMax       = atrPctMax;
        this.hhHlGate        = hhHlGate;
        this.stopMaxPct      = stopMaxPct;
        this.pullbackBand    = pullbackBand;
        this.resistancePct   = resistancePct;
        this.pullbackRsiLow  = pullbackRsiLow;
        this.pullbackRsiHigh = pullbackRsiHigh;
        this.breakoutRsiLow  = breakoutRsiLow;
        this.breakoutRsiHigh = breakoutRsiHigh;
        this.volPullbackMin  = volPullbackMin;
        this.volBreakoutMin  = volBreakoutMin;
        this.minSetupScore   = minSetupScore;
    }

    /** Parse from string safely, defaulting to MODERATE. */
    public static ScanStrictness fromString(String s) {
        if (s == null) return MODERATE;
        return switch (s.toUpperCase().trim()) {
            case "STRICT"  -> STRICT;
            case "RELAXED" -> RELAXED;
            default        -> MODERATE;
        };
    }
}
