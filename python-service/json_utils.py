"""
Shared JSON extraction utility for Signal Engine LLM responses.

Gemini 2.5 Flash (and other LLMs) sometimes:
  - Wraps JSON in ```json ... ``` markdown fences
  - Adds preamble text before the JSON
  - Adds explanation text after the JSON
  - Returns unterminated JSON if output is cut off at max_tokens

This module provides a single robust extractor used by all analysers.
"""

import re
import json


def extract_json(raw: str) -> dict:
    """
    Robustly extract a JSON object from an LLM response string.

    Handles:
      - Markdown code fences (```json ... ```)
      - Preamble text before the JSON object
      - Trailing text after the JSON object
      - Unterminated strings (tries to recover partial JSON)

    Raises ValueError if no JSON object can be found or parsed.
    """
    if not raw or not raw.strip():
        raise ValueError("Empty LLM response")

    # Remove markdown code fences
    cleaned = re.sub(r'```(?:json)?\s*', '', raw).strip()
    cleaned = cleaned.replace('```', '').strip()

    # Find the start of the outermost JSON object
    start = cleaned.find('{')
    if start == -1:
        raise ValueError(f"No JSON object found in response: {cleaned[:100]}")

    # Walk forward to find the matching closing brace
    depth     = 0
    end       = -1
    in_string = False
    escape    = False

    for i, ch in enumerate(cleaned[start:], start):
        if escape:
            escape = False
            continue
        if ch == '\\' and in_string:
            escape = True
            continue
        if ch == '"' and not escape:
            in_string = not in_string
            continue
        if in_string:
            continue
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break

    if end != -1:
        # Normal case — well-formed JSON
        return json.loads(cleaned[start:end])

    # Unterminated JSON — try to recover by closing the object
    fragment = cleaned[start:]
    # Strip any trailing incomplete string value e.g. "rationale": "Stock is stron
    fragment = re.sub(r',?\s*"[^"]*$', '', fragment)
    # Strip trailing comma
    fragment = fragment.rstrip(',').strip()
    # Close the object
    fragment = fragment + '}'

    try:
        return json.loads(fragment)
    except json.JSONDecodeError:
        raise ValueError(
            f"Could not parse JSON even after recovery. "
            f"Raw response start: {raw[:200]}"
        )


def safe_json_loads(raw: str, fallback: dict) -> dict:
    """
    Like extract_json but never raises — returns fallback dict on any failure.
    Use this when you want silent degradation.
    """
    try:
        return extract_json(raw)
    except Exception as e:
        print(f"  [JSON] Parse failed: {e}")
        return fallback
