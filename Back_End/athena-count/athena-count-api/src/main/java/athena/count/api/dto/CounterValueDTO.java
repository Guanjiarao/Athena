package athena.count.api.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CounterValueDTO implements Serializable {

    private String scope;
    private Long targetId;
    private Map<String, Long> counters = new HashMap<>();

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Map<String, Long> getCounters() {
        return counters;
    }

    public void setCounters(Map<String, Long> counters) {
        this.counters = counters;
    }
}
