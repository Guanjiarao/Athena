package athena.count.api.dto;

import java.io.Serializable;
import java.util.List;

public class CounterBatchDeltaDTO implements Serializable {

    private List<CounterDeltaDTO> deltas;

    public List<CounterDeltaDTO> getDeltas() {
        return deltas;
    }

    public void setDeltas(List<CounterDeltaDTO> deltas) {
        this.deltas = deltas;
    }
}
