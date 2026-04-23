package signal_engine.common.management.SignalEngineApplication.Provider;

import java.util.List;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

public interface MarketDataProvider {
	List<Candle> fetch(String symbol);
}
