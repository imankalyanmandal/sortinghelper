# Signal Engine - Local Development Configuration

## Environment Variables

Set these in your local `.env` file or environment:

### LLM Provider API Keys (at least one required)
```
GEMINI_API_KEY=your_gemini_api_key_here
GROQ_API_KEY=your_groq_api_key_here
CEREBRAS_API_KEY=your_cerebras_api_key_here
OPENROUTER_API_KEY=your_openrouter_api_key_here
GITHUB_TOKEN=your_github_token_here
```

### Optional Configuration
```
GEMINI_RETRY_DELAY=4.0
MOCK_MODE=false
```

## Local Development Setup

### Prerequisites
- Java 17 or higher
- Python 3.8 or higher
- Maven (or use included `mvnw`)
- Docker & Docker Compose

### Running Locally

#### Java Service
```bash
cd java-service
./mvnw spring-boot:run
```
- API available at: http://localhost:8080
- Database: `trades.db` (SQLite, created automatically)

#### Python Services
```bash
cd python-service
pip install -r requirements.txt
python -m <module_name>  # Run specific modules as needed
```

#### Full Stack with Docker
```bash
docker-compose up --build
```

### Database
- Uses SQLite (`trades.db`)
- Auto-creates tables on startup
- Located in `java-service/` directory

### Web Interface
- Open `trade_tracker.html` in browser
- Connects to Java service API

### Testing
```bash
cd java-service
./mvnw test
```

## Key Files
- `application.properties`: Spring Boot configuration
- `claude_config.py`: LLM provider configuration
- `docker-compose.yml`: Container orchestration
- `Makefile`: Common commands

## Ports
- Java Service: 8080
- Nginx (if using): 80

## Common Issues
- Ensure API keys are set for LLM functionality
- Check Java version: `java -version`
- Clear database: Delete `trades.db` and restart