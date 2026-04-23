"""
symbol_provider.py — Live index constituent fetcher

Fetches current Nifty 50 / Next 50 / 100 / 200 constituents from NSE.
Falls back to a hardcoded list if the API is unavailable.

Supported indices (pass as the `index` argument):
    "NIFTY 50"       → 50 stocks
    "NIFTY NEXT 50"  → next 50 (ranks 51-100)
    "NIFTY 100"      → both combined (recommended for swing trading)
    "NIFTY 200"      → top 200

Usage:
    from symbol_provider import get_symbols

    symbols = get_symbols("NIFTY 100")
    # Returns ["HDFCBANK", "RELIANCE", "INFY", ...]

The live fetch is tried first. If NSE is unreachable or returns bad data,
the hardcoded fallback is used and a warning is printed.
Symbols are cached in memory for the session so the API is only hit once per run.
"""

import requests
import time
from typing import Optional

# ── NSE API ───────────────────────────────────────────────────────────────────
NSE_INDEX_API  = "https://www.nseindia.com/api/equity-stockIndices?index={index}"
NSE_HEADERS    = {
    "User-Agent":      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept":          "application/json, text/plain, */*",
    "Referer":         "https://www.nseindia.com/",
    "Accept-Language": "en-US,en;q=0.9",
}

# ── In-memory cache ───────────────────────────────────────────────────────────
_cache: dict = {}


def get_symbols(index: str = "NIFTY 100") -> list[str]:
    """
    Return the current constituent symbols for the given NSE index.

    Args:
        index: One of "NIFTY 50", "NIFTY NEXT 50", "NIFTY 100", "NIFTY 200"

    Returns:
        List of NSE symbols e.g. ["HDFCBANK", "RELIANCE", ...]
    """
    index = index.upper().strip()

    if index in _cache:
        return _cache[index]

    # Special case: NIFTY 100 = NIFTY 50 + NIFTY NEXT 50
    if index == "NIFTY 100":
        n50      = _fetch_or_fallback("NIFTY 50")
        next50   = _fetch_or_fallback("NIFTY NEXT 50")
        combined = _merge_unique(n50, next50)
        _cache[index] = combined
        print(f"[Symbols] NIFTY 100: {len(combined)} stocks "
              f"(Nifty 50: {len(n50)} + Next 50: {len(next50)})")
        return combined

    symbols = _fetch_or_fallback(index)
    _cache[index] = symbols
    return symbols


def _fetch_or_fallback(index: str) -> list[str]:
    """Try live NSE API, fall back to hardcoded list on any failure."""
    live = _fetch_live(index)
    if live:
        print(f"[Symbols] {index}: fetched {len(live)} stocks from NSE (live)")
        return live

    fallback = _get_fallback(index)
    print(f"[Symbols] {index}: NSE API unavailable — using hardcoded fallback "
          f"({len(fallback)} stocks). May be outdated if index rebalanced.")
    return fallback


def _fetch_live(index: str) -> Optional[list[str]]:
    """
    Fetch current constituents from NSE's public equity index API.
    Returns None on any failure so the caller can fall back gracefully.
    """
    try:
        session = requests.Session()
        # NSE requires a session cookie from the main page first
        session.get("https://www.nseindia.com/", headers=NSE_HEADERS, timeout=10)
        time.sleep(1)

        url  = NSE_INDEX_API.format(index=requests.utils.quote(index))
        resp = session.get(url, headers=NSE_HEADERS, timeout=15)

        if resp.status_code != 200:
            print(f"  [Symbols] NSE API returned {resp.status_code} for {index}")
            return None

        data  = resp.json()
        rows  = data.get("data", [])

        # Skip the first row — it's the index itself, not a constituent
        symbols = []
        for row in rows:
            sym = row.get("symbol", "").strip()
            if sym and sym != index.replace(" ", ""):
                symbols.append(sym)

        return symbols if len(symbols) > 10 else None

    except Exception as e:
        print(f"  [Symbols] NSE API error for {index}: {e}")
        return None


def _merge_unique(list_a: list, list_b: list) -> list:
    """Combine two lists preserving order and removing duplicates."""
    seen = set()
    result = []
    for sym in list_a + list_b:
        if sym not in seen:
            seen.add(sym)
            result.append(sym)
    return result


# ── Hardcoded fallbacks ───────────────────────────────────────────────────────
# Updated as of April 2026. Used only when NSE API is unreachable.
# Run `python symbol_provider.py` to check if live fetch is working.

_NIFTY_50_FALLBACK = [
    "ADANIENT", "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT", "AXISBANK",
    "BAJAJ-AUTO", "BAJFINANCE", "BAJAJFINSV", "BPCL", "BHARTIARTL",
    "BRITANNIA", "CIPLA", "COALINDIA", "DIVISLAB", "DRREDDY",
    "EICHERMOT", "GRASIM", "HCLTECH", "HDFCBANK", "HDFCLIFE",
    "HEROMOTOCO", "HINDALCO", "HINDUNILVR", "ICICIBANK", "ITC",
    "INDUSINDBK", "INFY", "JSWSTEEL", "KOTAKBANK", "LT",
    "M&M", "MARUTI", "NESTLEIND", "NTPC", "ONGC",
    "POWERGRID", "RELIANCE", "SBILIFE", "SHRIRAMFIN", "SBIN",
    "SUNPHARMA", "TCS", "TATACONSUM", "TATAMOTORS", "TATASTEEL",
    "TECHM", "TITAN", "ULTRACEMCO", "WIPRO", "ZOMATO",
]

_NIFTY_NEXT_50_FALLBACK = [
    "ABB", "AMBUJACEM", "ATGL", "AUBANK", "BANKBARODA",
    "BERGEPAINT", "BEL", "BOSCHLTD", "CANBK", "CGPOWER",
    "CHOLAFIN", "COLPAL", "DLF", "DMART", "GAIL",
    "GODREJCP", "HAVELLS", "ICICIGI", "ICICIPRULI", "IOC",
    "IRCTC", "JINDALSTEL", "LICI", "LTIM", "LTTS",
    "LUPIN", "MAXHEALTH", "MCDOWELL-N", "MPHASIS", "NHPC",
    "NMDC", "NAUKRI", "OBEROIRLTY", "OFSS", "PIIND",
    "PGHH", "PNB", "PAGEIND", "PERSISTENT", "PIDILITIND",
    "POLYCAB", "RECLTD", "SAIL", "SIEMENS", "TORNTPHARM",
    "TRENT", "TVSMOTOR", "UPL", "VEDL", "ZYDUSLIFE",
]

_NIFTY_200_EXTRA_FALLBACK = [
    # Ranks 101-200 — lower liquidity, use with caution for swing trading
    "ABCAPITAL", "APLAPOLLO", "ASTRAL", "AUROPHARMA", "BANDHANBNK",
    "BHARATFORG", "BIOCON", "CAMS", "CONCOR", "CROMPTON",
    "CUMMINSIND", "DALBHARAT", "DIXON", "FLUOROCHEM", "FORTIS",
    "GLENMARK", "GMRINFRA", "GODREJIND", "GUJGASLTD", "HFCL",
    "IDBI", "IIFL", "IPCALAB", "JUBLFOOD", "KALYANKJIL",
    "KPITTECH", "LAURUSLABS", "MARICO", "MFSL", "MUTHOOTFIN",
    "NATIONALUM", "NYKAA", "PAYTM", "PNBHOUSING", "POLICYBZR",
    "PRINCEPIPE", "PVRINOX", "RAJESHEXPO", "RAMCOCEM", "SBICARD",
    "SNOWMAN", "SOBHA", "SOLARINDS", "STARHEALTH", "SUMICHEM",
    "SUNTV", "SUPREMEIND", "SYNGENE", "TATACOMM", "TIINDIA",
]


def _get_fallback(index: str) -> list[str]:
    if index == "NIFTY 50":
        return _NIFTY_50_FALLBACK[:]
    if index == "NIFTY NEXT 50":
        return _NIFTY_NEXT_50_FALLBACK[:]
    if index == "NIFTY 200":
        return (_NIFTY_50_FALLBACK +
                _NIFTY_NEXT_50_FALLBACK +
                _NIFTY_200_EXTRA_FALLBACK)
    return _NIFTY_50_FALLBACK[:]   # default


# ── Yahoo Finance ticker mapping ──────────────────────────────────────────────
# NSE symbol → Yahoo Finance ticker (most are symbol + .NS)
# Exceptions are listed here; everything else gets .NS appended automatically.
YAHOO_EXCEPTIONS = {
    "M&M":        "MM.NS",
    "BAJAJ-AUTO": "BAJAJ-AUTO.NS",
    "MCDOWELL-N": "MCDOWELL-N.NS",
}

def to_yahoo_ticker(symbol: str) -> str:
    """Convert NSE symbol to Yahoo Finance ticker."""
    return YAHOO_EXCEPTIONS.get(symbol, f"{symbol}.NS")


# ── Test ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("Testing live NSE fetch...\n")

    for idx in ["NIFTY 50", "NIFTY NEXT 50", "NIFTY 100"]:
        syms = get_symbols(idx)
        print(f"{idx}: {len(syms)} stocks")
        print(f"  First 5:  {syms[:5]}")
        print(f"  Last 5:   {syms[-5:]}\n")
