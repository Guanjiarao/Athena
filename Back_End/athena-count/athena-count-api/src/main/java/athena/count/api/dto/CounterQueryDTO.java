package athena.count.api.dto;

import java.io.Serializable;
import java.util.List;

public class CounterQueryDTO implements Serializable {

    private String scope;
    private List<Long> targetIds;
    private List<String> counterTypes;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<Long> getTargetIds() {
        return targetIds;
    }

    public void setTargetIds(List<Long> targetIds) {
        this.targetIds = targetIds;
    }

    public List<String> getCounterTypes() {
        return counterTypes;
    }

    public void setCounterTypes(List<String> counterTypes) {
        this.counterTypes = counterTypes;
    }
}
