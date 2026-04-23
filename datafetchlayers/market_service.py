"""
PATCH for market_service.py
Apply these 3 changes — everything else in the file stays identical.
"""

# ── CHANGE 1: Add this import at the top of market_service.py ────────────────

from symbol_provider import get_symbols, to_yahoo_ticker

# ── CHANGE 2: Remove the hardcoded NIFTY_50 dict entirely ────────────────────
# Delete the whole block that starts with:
#   NIFTY_50 = { "ADANIENT": "ADANIENT", ... }


# ── CHANGE 3: Replace the /nifty50/symbols endpoint ──────────────────────────

@app.route("/nifty50/symbols")
def get_nifty50_symbols():
    # Now supports any index — pass ?index=NIFTY+100 etc.
    index   = request.args.get("index", "NIFTY 50")
    symbols = get_symbols(index)
    return jsonify({
        "index":   index,
        "count":   len(symbols),
        "symbols": symbols,
    })


# ── CHANGE 4: Update the /candles endpoint ticker lookup ─────────────────────
# Find this line in the /candles endpoint:
#   ticker = f"{NIFTY_50.get(symbol, symbol)}.{exchange}"
# Replace with:
#   ticker = to_yahoo_ticker(symbol) if exchange == "NS" else f"{symbol}.BO"


# ── CHANGE 5: Update Nifty50ScannerService call in /pipeline/run ─────────────
# The Java side calls /nifty50/symbols to get the list.
# No Java changes needed — the endpoint still works, just returns live data now.


# ── NEW ENDPOINT: scan any index ──────────────────────────────────────────────
# Add this alongside your existing endpoints:

@app.route("/index/symbols")
def get_index_symbols():
    """
    GET /index/symbols?index=NIFTY+100
    GET /index/symbols?index=NIFTY+50
    GET /index/symbols?index=NIFTY+NEXT+50
    GET /index/symbols?index=NIFTY+200

    Returns live constituents from NSE, falls back to hardcoded list.
    """
    index   = request.args.get("index", "NIFTY 100")
    symbols = get_symbols(index)
    return jsonify({
        "index":   index,
        "count":   len(symbols),
        "symbols": symbols,
    })