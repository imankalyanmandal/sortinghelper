# Signal Engine

A comprehensive financial signal analysis and backtesting platform built with Java Spring Boot backend and Python services for AI-powered market analysis.

## Architecture

### Java Service (`java-service/`)
- **Framework**: Spring Boot application
- **Purpose**: Core signal processing, backtesting, and REST API endpoints
- **Key Components**:
  - Controllers: BacktestController, Layer3Controller, LiveScanController, TradeController
  - Services: Backtesting logic, market data processing
  - Indicators: Technical analysis (ATR, Bollinger Bands, RSI, SMA)
  - Models: BacktestConfig, BacktestResult, trading data structures

### Python Service (`python-service/`)
- **Purpose**: Data fetching, sentiment analysis, and AI/ML processing
- **Key Modules**:
  - `fundamentals_fetcher.py`: Financial fundamentals data
  - `market_service.py`: Market data services
  - `sentiment_analyser.py`: Market sentiment analysis
  - `concall_analyser.py`: Conference call analysis
  - `composite_scorer.py`: Scoring algorithms
  - `llm_client.py`: Large Language Model integration
  - `claude_config.py`: LLM provider configuration

## Development Setup

1. **Prerequisites**:
   - Java 17+ (for Spring Boot)
   - Python 3.8+ (for data services)
   - Docker & Docker Compose (for containerized deployment)

2. **Local Development**:
   - Java service: `./mvnw spring-boot:run`
   - Python services: `pip install -r requirements.txt`
   - Full stack: `docker-compose up`

## Key Features

- **Backtesting Engine**: Historical data analysis with customizable strategies
- **Live Scanning**: Real-time market signal detection
- **Technical Indicators**: Standard TA indicators implementation
- **Sentiment Analysis**: AI-powered market sentiment from news and calls
- **Composite Scoring**: Multi-factor signal scoring system

## API Endpoints

- `POST /api/backtest`: Run backtesting simulations
- `GET /api/live-scan`: Get live market signals
- `POST /api/trade`: Execute trading operations

## Configuration

Environment variables for LLM providers (Claude, Gemini, Groq, etc.) configured in `claude_config.py` with fallback support.