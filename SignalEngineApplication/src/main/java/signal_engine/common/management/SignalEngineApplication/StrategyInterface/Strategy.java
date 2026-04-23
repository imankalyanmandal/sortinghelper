package signal_engine.common.management.SignalEngineApplication.StrategyInterface;

import java.util.List;

import org.springframework.stereotype.Service;

import signal_engine.common.management.SignalEngineApplication.model.Candle;

@Service
public interface Strategy {
	String generateSignal(List<Candle> candles, int index);
}
