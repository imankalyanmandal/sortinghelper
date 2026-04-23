"""
claude_config.py — Multi-provider LLM configuration

Provider chain (tried in order, automatic fallback):
  1. Gemini 2.0 Flash  — Google AI Studio, free, no credit card
  2. Groq Llama 70B    — Groq console, free, no credit card
  3. Ollama (local)    — your local Llama install, always available offline

Set cloud keys in your .env file (same folder as market_service.py):
    GEMINI_API_KEY=AIzaSy...
    GROQ_API_KEY=gsk_...

Ollama needs no key. Make sure it is running and your model is pulled:
    ollama list                     <- see what you have installed
    ollama pull llama3.2            <- pull a model if needed

Then set OLLAMA_MODEL in .env to match the name from `ollama list`.

Get cloud keys (both free, no credit card):
    Gemini -> https://aistudio.google.com  -> Get API key
    Groq   -> https://console.groq.com     -> API Keys -> Create API Key
"""

import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

# ── API keys ──────────────────────────────────────────────────────────────────
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GROQ_API_KEY   = os.environ.get("GROQ_API_KEY",   "")

# ── Mock mode ─────────────────────────────────────────────────────────────────
MOCK_MODE = False

# ── Cloud provider endpoints ──────────────────────────────────────────────────
GEMINI_URL   = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
GEMINI_MODEL = "gemini-2.0-flash"

GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "llama-3.3-70b-versatile"   # llama3-70b-8192 was decommissioned May 2025

# ── Ollama local config ───────────────────────────────────────────────────────
# Run `ollama list` to see your exact installed model name.
# Common names: llama3.2, llama3.1, llama3.1:8b, llama2, mistral, phi3
# Set OLLAMA_MODEL in .env to match your installed model exactly.
OLLAMA_URL   = os.environ.get("OLLAMA_URL",   "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "llama3.2")  # <-- update to match `ollama list`

# ── Gemini rate limit buffer ──────────────────────────────────────────────────
# Free tier: 15 req/min. Each stock makes 3 LLM calls.
# 4s delay between calls keeps you safely under the limit.
# Reduce to 2 if you have a paid Gemini key. Increase to 5 if still hitting 429.
GEMINI_RETRY_DELAY = float(os.environ.get("GEMINI_RETRY_DELAY", "4"))

# ── Startup summary ───────────────────────────────────────────────────────────
if MOCK_MODE:
    print("[INFO] MOCK_MODE is ON — no API calls, no charges")
else:
    chain = []
    if GEMINI_API_KEY: chain.append("Gemini")
    if GROQ_API_KEY:   chain.append("Groq")
    chain.append(f"Ollama/{OLLAMA_MODEL} (local)")
    print(f"[INFO] LLM chain: {' -> '.join(chain)}")
    if not GEMINI_API_KEY:
        print("       Add GEMINI_API_KEY to .env to enable Gemini (free)")
    if not GROQ_API_KEY:
        print("       Add GROQ_API_KEY to .env to enable Groq (free)")
    print(f"       Ollama model expected: {OLLAMA_MODEL}  (run `ollama list` to confirm)")


def is_mock() -> bool:
    return MOCK_MODE

def has_gemini() -> bool:
    return bool(GEMINI_API_KEY)

def has_groq() -> bool:
    return bool(GROQ_API_KEY)

def has_ollama() -> bool:
    return True  # always in the chain — works offline