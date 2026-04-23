"""
llm_client.py — Unified LLM client with three-tier automatic fallback.

Usage (from any Layer 2 module):
    from llm_client import call_llm

    response_text = call_llm(prompt)
    # Returns the model's text response, or None if all providers fail.

Provider chain (tried in order):
    1. Gemini 2.0 Flash   — Google AI Studio, free cloud
    2. Groq Llama 70B     — Groq console, free cloud fallback
    3. Ollama (local)     — your local Llama install, final fallback

Each provider is tried once. On any error (timeout, 429, bad response)
the next provider is tried immediately.

Ollama is the safety net — it always works as long as `ollama serve` is running,
even with no internet connection.
"""

import requests
import json
import time
from typing import Optional

from claude_config import (
    GEMINI_API_KEY, GEMINI_URL, GEMINI_MODEL,
    GROQ_API_KEY,   GROQ_URL,   GROQ_MODEL,
    OLLAMA_URL,     OLLAMA_MODEL,
    GEMINI_RETRY_DELAY,
    has_gemini, has_groq, has_ollama,
)

TIMEOUT_SECONDS        = 45
OLLAMA_TIMEOUT_SECONDS = 120   # local model is slower, give it more time


def call_llm(prompt: str, max_tokens: int = 1000) -> Optional[str]:
    """
    Send a prompt through the three-tier provider chain.

    Returns the first successful response text, or None if all fail.

    Chain: Gemini → Groq → Ollama (local)
    """
    # Tier 1: Gemini
    if has_gemini():
        result = _call_gemini(prompt, max_tokens)
        if result is not None:
            return result
        print("  [LLM] Gemini failed → trying Groq...")

    # Tier 2: Groq
    if has_groq():
        result = _call_groq(prompt, max_tokens)
        if result is not None:
            return result
        print("  [LLM] Groq failed → trying local Ollama...")

    # Tier 3: Ollama (local — final safety net)
    result = _call_ollama(prompt, max_tokens)
    if result is not None:
        return result

    print("  [LLM] All providers failed (is Ollama running? Run: ollama serve)")
    return None


# ── Gemini provider ───────────────────────────────────────────────────────────

def _call_gemini(prompt: str, max_tokens: int) -> Optional[str]:
    """Call Gemini API. Returns response text or None on any failure."""
    url  = f"{GEMINI_URL}?key={GEMINI_API_KEY}"
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature":     0.2,       # low temp = consistent JSON output
            "maxOutputTokens": max_tokens,
        },
        "safetySettings": [
            # Disable safety filters for financial analysis
            # (they sometimes block market/risk language)
            {"category": "HARM_CATEGORY_HARASSMENT",        "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_HATE_SPEECH",       "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
            {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"},
        ],
    }

    try:
        resp = requests.post(
            url,
            headers={"Content-Type": "application/json"},
            json=body,
            timeout=TIMEOUT_SECONDS,
        )

        if resp.status_code == 429:
            print(f"  [Gemini] Rate limited (429) — switching to Groq")
            return None

        if resp.status_code != 200:
            print(f"  [Gemini] HTTP {resp.status_code}: {resp.text[:150]}")
            return None

        data = resp.json()

        # Check for safety blocks or empty candidates
        candidates = data.get("candidates", [])
        if not candidates:
            reason = data.get("promptFeedback", {}).get("blockReason", "unknown")
            print(f"  [Gemini] No candidates returned — blockReason: {reason}")
            return None

        finish_reason = candidates[0].get("finishReason", "")
        if finish_reason == "SAFETY":
            print(f"  [Gemini] Response blocked by safety filter — switching to Groq")
            return None

        text = candidates[0]["content"]["parts"][0]["text"]
        print(f"  [Gemini] OK — {len(text)} chars")
        # Small delay to stay within free tier rate limit (15 req/min)
        time.sleep(GEMINI_RETRY_DELAY)
        return text

    except requests.Timeout:
        print(f"  [Gemini] Timeout after {TIMEOUT_SECONDS}s — switching to Groq")
        return None
    except (KeyError, IndexError) as e:
        print(f"  [Gemini] Unexpected response structure: {e}")
        return None
    except Exception as e:
        print(f"  [Gemini] Error: {e}")
        return None


# ── Groq provider ─────────────────────────────────────────────────────────────

def _call_groq(prompt: str, max_tokens: int) -> Optional[str]:
    """
    Call Groq API (OpenAI-compatible format).
    Returns response text or None on any failure.
    """
    headers = {
        "Content-Type":  "application/json",
        "Authorization": f"Bearer {GROQ_API_KEY}",
    }
    body = {
        "model":       GROQ_MODEL,
        "messages":    [{"role": "user", "content": prompt}],
        "max_tokens":  max_tokens,
        "temperature": 0.2,
    }

    try:
        resp = requests.post(
            GROQ_URL,
            headers=headers,
            json=body,
            timeout=TIMEOUT_SECONDS,
        )

        if resp.status_code == 429:
            print(f"  [Groq] Rate limited (429)")
            return None

        if resp.status_code != 200:
            print(f"  [Groq] HTTP {resp.status_code}: {resp.text[:150]}")
            return None

        text = resp.json()["choices"][0]["message"]["content"]
        print(f"  [Groq] OK — {len(text)} chars")
        return text

    except requests.Timeout:
        print(f"  [Groq] Timeout after {TIMEOUT_SECONDS}s")
        return None
    except (KeyError, IndexError) as e:
        print(f"  [Groq] Unexpected response structure: {e}")
        return None
    except Exception as e:
        print(f"  [Groq] Error: {e}")
        return None


# ── Ollama local provider ─────────────────────────────────────────────────────

def _call_ollama(prompt: str, max_tokens: int) -> Optional[str]:
    """
    Call local Ollama server (OpenAI-compatible /v1/chat/completions endpoint).

    Ollama must be running: `ollama serve`
    Model must be pulled:   `ollama pull llama3`

    No API key needed — it's local. Slower than cloud but always available.
    """
    url = f"{OLLAMA_URL}/v1/chat/completions"
    body = {
        "model":       OLLAMA_MODEL,
        "messages":    [{"role": "user", "content": prompt}],
        "max_tokens":  max_tokens,
        "temperature": 0.2,
        "stream":      False,
    }

    try:
        resp = requests.post(
            url,
            headers={"Content-Type": "application/json"},
            json=body,
            timeout=OLLAMA_TIMEOUT_SECONDS,
        )

        if resp.status_code == 404:
            # Model not found — give a helpful message
            print(f"  [Ollama] Model '{OLLAMA_MODEL}' not found.")
            print(f"           Run: ollama pull {OLLAMA_MODEL}")
            return None

        if resp.status_code != 200:
            print(f"  [Ollama] HTTP {resp.status_code}: {resp.text[:150]}")
            return None

        text = resp.json()["choices"][0]["message"]["content"]
        print(f"  [Ollama/{OLLAMA_MODEL}] OK — {len(text)} chars")
        return text

    except requests.ConnectionError:
        print(f"  [Ollama] Connection refused — is Ollama running?")
        print(f"           Start it with: ollama serve")
        return None
    except requests.Timeout:
        print(f"  [Ollama] Timeout after {OLLAMA_TIMEOUT_SECONDS}s — model may be too large")
        return None
    except (KeyError, IndexError) as e:
        print(f"  [Ollama] Unexpected response structure: {e}")
        return None
    except Exception as e:
        print(f"  [Ollama] Error: {e}")
        return None


# ── Test utility ──────────────────────────────────────────────────────────────

def test_providers():
    """
    Quick sanity check — ping all three providers.
    Run directly: python llm_client.py
    """
    print("\n=== Testing LLM provider chain ===")
    test_prompt = 'Reply with only this JSON: {"status": "ok", "provider": "your name"}'

    # Gemini
    if has_gemini():
        result = _call_gemini(test_prompt, max_tokens=60)
        print(f"  Gemini:         {'OK — ' + result[:60].strip() if result else 'FAILED'}")
    else:
        print("  Gemini:         not configured (add GEMINI_API_KEY to .env)")

    # Groq
    if has_groq():
        result = _call_groq(test_prompt, max_tokens=60)
        print(f"  Groq:           {'OK — ' + result[:60].strip() if result else 'FAILED'}")
    else:
        print("  Groq:           not configured (add GROQ_API_KEY to .env)")

    # Ollama
    print(f"  Ollama (local): testing {OLLAMA_MODEL}...")
    result = _call_ollama(test_prompt, max_tokens=60)
    print(f"  Ollama:         {'OK — ' + result[:60].strip() if result else 'FAILED — run: ollama serve && ollama pull ' + OLLAMA_MODEL}")

    print("\nFull chain test (should use first available):")
    final = call_llm(test_prompt, max_tokens=60)
    print(f"  Result: {final[:80].strip() if final else 'ALL PROVIDERS FAILED'}\n")


if __name__ == "__main__":
    test_providers()