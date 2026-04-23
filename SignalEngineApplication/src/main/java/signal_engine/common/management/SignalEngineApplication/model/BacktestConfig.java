package signal_engine.common.management.SignalEngineApplication.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BacktestConfig {
	private double stopLossPercent = 2.0;
    private double targetPercent = 4.0;
}
