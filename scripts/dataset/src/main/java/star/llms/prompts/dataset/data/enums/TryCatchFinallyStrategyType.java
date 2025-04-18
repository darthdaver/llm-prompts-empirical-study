package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TryCatchFinallyStrategyType {
    STANDARD("standard"),
    FLAT("flat");

    private final String type;

    TryCatchFinallyStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static TryCatchFinallyStrategyType fromValue(String value) {
        for (TryCatchFinallyStrategyType type : TryCatchFinallyStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
