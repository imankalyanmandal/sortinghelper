package signal_engine.common.management.SignalEngineApplication.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Layer 2 result from the LLM-powered composite scorer.
 * Claude reasons holistically across fundamentals, sentiment, and concall.
 */
@Data
public class Layer2Result {

    private String  symbol;
    private String  companyName;

    // ── Gate result ───────────────────────────────────────────────────────
    private boolean layer2Pass;
    private double  compositeScore;
    private double  passThreshold;

    // ── Claude's verdict ──────────────────────────────────────────────────
    /** STRONG_BUY | BUY | HOLD | AVOID | STRONG_AVOID */
    private String swingVerdict;

    /** HIGH | MEDIUM | LOW — how confident Claude is in the verdict */
    private String conviction;

    /** ALIGNED | MIXED | CONFLICTED — do all signals agree? */
    private String signalAlignment;

    /** Plain-English rationale mentioning specific numbers and any conflicts */
    private String rationale;

    /** Up to 3 specific reasons to enter the trade */
    private List<String> keyPositives;

    /** Up to 3 specific risks that could stop out the trade */
    private List<String> keyRisks;

    /** NOW | WAIT_FOR_PULLBACK | WAIT_FOR_CATALYST | NOT_NOW */
    private String optimalEntryTiming;

    /** One sentence on how/when to enter if verdict is BUY or STRONG_BUY */
    private String entryNote;

    /** Hard stop reasons — empty if none */
    private List<String> redFlags;

    /**
     * How Claude weighted each signal for this specific stock.
     * Keys: fundamentals, sentiment, concall (sum to 100)
     * These vary per stock — near earnings concall weight rises, etc.
     */
    private Map<String, Integer> weightsUsed;

    // ── Raw sub-results for transparency ─────────────────────────────────
    private FundamentalDetail fundamentals;
    private SentimentDetail   sentiment;
    private ConcallDetail     concall;

    /** True if Claude API call failed and a fallback neutral result was used */
    private boolean claudeFallback;

    // ── Nested detail classes ─────────────────────────────────────────────

    @Data
    public static class FundamentalDetail {
        private Double       roe;
        private Double       debtEquity;
        private Double       promoterHolding;
        private Double       revenueGrowth3y;
        private Double       profitGrowth3y;
        private List<String> flags;
    }

    @Data
    public static class SentimentDetail {
        private String       direction;
        private String       confidence;
        private String       narrative;
        private List<String> topHeadlines;
    }

    @Data
    public static class ConcallDetail {
        private String tone;
        private String guidanceChange;
        private String swingSignal;
        private String summary;
        private String filingDate;
    }
}