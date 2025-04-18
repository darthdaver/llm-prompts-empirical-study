package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TargetStrategyType {
    ASSERTION("assertion"),
    STATEMENT("statement");

    private final String type;

    TargetStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static TargetStrategyType fromValue(String value) {
        for (TargetStrategyType type : TargetStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
