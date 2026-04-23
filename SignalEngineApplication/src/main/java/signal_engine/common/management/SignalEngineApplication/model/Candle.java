package signal_engine.common.management.SignalEngineApplication.model;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Candle {
    private String date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
