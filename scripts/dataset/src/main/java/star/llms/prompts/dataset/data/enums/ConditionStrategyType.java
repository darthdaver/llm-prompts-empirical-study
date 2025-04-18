package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConditionStrategyType {
    KEEP("keep"),
    MASK("mask"),
    REMOVE("remove");

    private final String type;

    ConditionStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static ConditionStrategyType fromValue(String value) {
        for (ConditionStrategyType type : ConditionStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}