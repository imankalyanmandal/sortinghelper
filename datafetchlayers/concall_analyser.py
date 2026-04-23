"""
Layer 2 — Concall Analyser (BSE version)

NSE blocks automated requests with dynamic tokens.
BSE has a simpler, more scraper-friendly public API.

Sources tried in order:
  1. BSE corporate announcements API (investor presentations / concall notices)
  2. NSE as fallback (still attempted with better headers)
  3. Graceful neutral result if both fail (doesn't break the pipeline)
"""

import requests
import json
import re
from typing import Optional
from datetime import datetime, timedelta

from claude_config  import is_mock
from llm_client     import call_llm
from mock_responses import mock_concall_response

# BSE scrip codes for Nifty 50 stocks
BSE_CODES = {
    "TECHM":      "532755", "POWERGRID":  "532898", "HDFCBANK":   "500180",
    "DIVISLAB":   "532488", "TITAN":      "500114", "COALINDIA":  "533278",
    "SBIN":       "500112", "HCLTECH":    "532281", "HINDALCO":   "500440",
    "INFY":       "500209", "AXISBANK":   "532215", "ICICIBANK":  "532174",
    "RELIANCE":   "500325", "TCS":        "532540", "WIPRO":      "507685",
    "BAJFINANCE": "500034", "KOTAKBANK":  "500247", "ADANIPORTS": "532921",
    "TATAMOTORS": "500570", "MARUTI":     "532500", "SUNPHARMA":  "524715",
    "DRREDDY":    "500124", "CIPLA":      "500087", "HEROMOTOCO": "500182",
    "BAJAJFINSV": "532978", "NESTLEIND":  "500790", "BRITANNIA":  "500825",
    "JSWSTEEL":   "500228", "TATASTEEL":  "500470", "NTPC":       "532555",
    "ONGC":       "500312", "BPCL":       "500547", "LT":         "500510",
    "EICHERMOT":  "505200", "ASIANPAINT": "500820", "ULTRACEMCO": "532538",
    "GRASIM":     "500300", "APOLLOHOSP": "508869", "INDUSINDBK": "532187",
    "ITC":        "500875", "BHARTIARTL": "532454", "HDFCLIFE":   "540777",
    "SBILIFE":    "540719", "SHRIRAMFIN": "511218", "BAJAJ-AUTO": "532977",
    "ADANIENT":   "512599", "TATACONSUM": "500800", "HINDUNILVR": "500696",
    "ZOMATO":     "543320", "MM":         "500520",
}

BSE_ANNOUNCEMENT_API = (
    "https://api.bseindia.com/BseIndiaAPI/api/AnnSubCategoryGetData/w"
    "?pageno=1&category=Corp.+Action&subcategory=-1&scrip_cd={scrip_cd}&strCat=E"
)

BSE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept":     "application/json",
    "Referer":    "https://www.bseindia.com/",
    "Origin":     "https://www.bseindia.com",
}

NSE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept":     "application/json, text/plain, */*",
    "Referer":    "https://www.nseindia.com/",
    "Accept-Language": "en-US,en;q=0.9",
}

MAX_TRANSCRIPT_CHARS = 6000


def fetch_concall_analysis(symbol: str, company_name: str = "") -> dict:
    name = company_name if company_name else symbol

    if is_mock():
        result = mock_concall_response(symbol)
        return {"symbol": symbol, "source": "mock", "error": False,
                "filing_date": None, **result}

    # Try BSE first, then NSE
    pdf_url, filing_date = _find_via_bse(symbol)

    if not pdf_url:
        pdf_url, filing_date = _find_via_nse(symbol)

    if not pdf_url:
        # Don't fail — return neutral, let LLM work with fundamentals + sentiment
        return _neutral_result(symbol, "No recent concall transcript found (BSE/NSE)")

    transcript = _extract_pdf_text(pdf_url)
    if not transcript or len(transcript) < 150:
        return _neutral_result(symbol, "Could not extract readable text from transcript PDF")

    return _analyse_with_llm(symbol, name, transcript[:MAX_TRANSCRIPT_CHARS], filing_date)


def _find_via_bse(symbol: str):
    """Search BSE corporate announcements for concall/investor call documents."""
    scrip_cd = BSE_CODES.get(symbol)
    if not scrip_cd:
        return None, None

    try:
        url  = BSE_ANNOUNCEMENT_API.format(scrip_cd=scrip_cd)
        resp = requests.get(url, headers=BSE_HEADERS, timeout=15)

        if resp.status_code != 200:
            return None, None

        data    = resp.json()
        items   = data.get("Table", []) or data.get("data", [])
        cutoff  = datetime.now() - timedelta(days=180)

        for item in items:
            headline = (item.get("HEADLINE", "") + " " + item.get("ATTACHMENT", "")).lower()
            is_concall = any(kw in headline for kw in
                            ["concall", "earnings call", "investor call",
                             "analyst call", "investor presentation", "quarterly results"])
            if not is_concall:
                continue

            pdf_link = item.get("ATTACHMENTNAME", "") or item.get("ATTACHMENT", "")
            date_str = item.get("NEWS_DT", "") or item.get("DT_TM", "")

            # Parse date
            filing_date = None
            for fmt in ("%Y-%m-%dT%H:%M:%S", "%d/%m/%Y", "%d-%m-%Y"):
                try:
                    filing_date = datetime.strptime(date_str[:10], fmt[:len(date_str[:10])])
                    break
                except ValueError:
                    pass

            if filing_date and filing_date < cutoff:
                break

            if pdf_link and pdf_link.lower().endswith(".pdf"):
                full_url = (f"https://www.bseindia.com{pdf_link}"
                           if pdf_link.startswith("/") else pdf_link)
                return full_url, date_str

    except Exception as e:
        print(f"  [Concall] BSE lookup error for {symbol}: {e}")

    return None, None


def _find_via_nse(symbol: str):
    """Try NSE as fallback with improved session handling."""
    try:
        session = requests.Session()
        session.get("https://www.nseindia.com/", headers=NSE_HEADERS, timeout=10)

        url  = (f"https://www.nseindia.com/api/corp-info"
                f"?symbol={symbol}&corpType=transcript&market=equities")
        resp = session.get(url, headers=NSE_HEADERS, timeout=15)

        if resp.status_code != 200:
            return None, None

        cutoff = datetime.now() - timedelta(days=180)
        for filing in resp.json().get("data", []):
            attach = filing.get("attachmentName", "")
            date   = filing.get("date", "")
            if not attach or not attach.lower().endswith(".pdf"):
                continue
            try:
                if datetime.strptime(date, "%d-%b-%Y") < cutoff:
                    break
            except ValueError:
                pass
            full_url = (f"https://www.nseindia.com{attach}"
                       if attach.startswith("/") else attach)
            return full_url, date

    except Exception as e:
        print(f"  [Concall] NSE lookup error for {symbol}: {e}")

    return None, None


def _extract_pdf_text(pdf_url: str) -> Optional[str]:
    try:
        resp = requests.get(pdf_url, headers=BSE_HEADERS, timeout=30, stream=True)
        if resp.status_code != 200:
            return None

        pdf_bytes = resp.content

        # Try pdfminer first
        try:
            from pdfminer.high_level import extract_text_to_fp
            from pdfminer.layout import LAParams
            import io
            output = io.StringIO()
            extract_text_to_fp(io.BytesIO(pdf_bytes), output,
                               laparams=LAParams(), output_type="text", codec="utf-8")
            text = output.getvalue()
            if text and len(text) > 150:
                return _clean_text(text)
        except ImportError:
            pass

        # Fallback: raw ASCII
        text = re.sub(r'[^\x20-\x7E\n\r]', ' ',
                      pdf_bytes.decode("latin-1", errors="ignore"))
        return _clean_text(text) if len(text) > 150 else None

    except Exception as e:
        print(f"  [Concall] PDF extraction error: {e}")
        return None


def _clean_text(text: str) -> str:
    text = re.sub(r'\(cid:\d+\)', '', text)
    text = re.sub(r'[\x00-\x1F]+', ' ', text)
    text = re.sub(r' {3,}', '  ', text)
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


def _analyse_with_llm(symbol, company_name, transcript, filing_date) -> dict:
    prompt = f"""You are a swing trading analyst reviewing an earnings call transcript for {company_name} ({symbol}).
Filing date: {filing_date or 'recent quarter'}

Transcript excerpt:
\"\"\"
{transcript}
\"\"\"

Analyse management tone and guidance for a 5-20 day swing trade.
Respond with ONLY a valid JSON object:

{{
  "concall_score": <integer 0-100>,
  "tone": "<CONFIDENT|CAUTIOUS|CONCERNED>",
  "guidance_change": "<RAISED|MAINTAINED|LOWERED|NOT_GIVEN>",
  "key_positives": ["<up to 3 bullish management statements>"],
  "key_negatives": ["<up to 3 bearish management statements>"],
  "summary": "<2 sentences on what management said and near-term price implication>",
  "swing_signal": "<BUY_BIAS|HOLD_BIAS|SELL_BIAS|NEUTRAL>",
  "confidence": "<HIGH|MEDIUM|LOW>"
}}"""

    raw = call_llm(prompt, max_tokens=500)

    if raw is None:
        return _neutral_result(symbol, "LLM unavailable")

    try:
        cleaned = raw.replace("```json", "").replace("```", "").strip()
        start   = cleaned.find("{")
        end     = cleaned.rfind("}") + 1
        parsed  = json.loads(cleaned[start:end])
        return {
            "symbol":          symbol,
            "source":          "bse_transcript + llm",
            "error":           False,
            "filing_date":     filing_date,
            "concall_score":   parsed.get("concall_score",   50),
            "tone":            parsed.get("tone",            "CAUTIOUS"),
            "guidance_change": parsed.get("guidance_change", "NOT_GIVEN"),
            "key_positives":   parsed.get("key_positives",   []),
            "key_negatives":   parsed.get("key_negatives",   []),
            "summary":         parsed.get("summary",         ""),
            "swing_signal":    parsed.get("swing_signal",    "NEUTRAL"),
            "confidence":      parsed.get("confidence",      "LOW"),
        }

    except (json.JSONDecodeError, ValueError) as e:
        print(f"  [{symbol}] Concall JSON parse error: {e}")
        return _neutral_result(symbol, "LLM returned invalid JSON")


def _neutral_result(symbol: str, reason: str) -> dict:
    """Neutral fallback — does NOT fail the pipeline."""
    print(f"  [Concall] {symbol}: {reason}")
    return {
        "symbol":          symbol,
        "source":          "bse_transcript + llm",
        "error":           False,
        "filing_date":     None,
        "concall_score":   50,    # neutral, not 0
        "tone":            "CAUTIOUS",
        "guidance_change": "NOT_GIVEN",
        "key_positives":   [],
        "key_negatives":   [],
        "summary":         reason,
        "swing_signal":    "NEUTRAL",
        "confidence":      "LOW",
    }