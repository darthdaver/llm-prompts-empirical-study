package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AssertionStrategyType {
    KEEP("keep"),
    MASK("mask"),
    REMOVE("remove");

    private final String type;

    AssertionStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static AssertionStrategyType fromValue(String value) {
        for (AssertionStrategyType type : AssertionStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
