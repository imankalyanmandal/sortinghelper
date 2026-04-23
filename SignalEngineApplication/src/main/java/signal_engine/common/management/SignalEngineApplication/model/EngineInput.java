package signal_engine.common.management.SignalEngineApplication.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EngineInput {
	
	private String index;
    private List<String> stocks;
    private int topN = 4;

    private double minROE = 15;
    private double maxDebtEquity = 0.5;


}
