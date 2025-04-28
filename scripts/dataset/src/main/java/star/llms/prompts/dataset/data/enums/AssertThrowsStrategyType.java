package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AssertThrowsStrategyType {
    STANDARD("standard"),
    FLAT("flat");

    private final String type;

    AssertThrowsStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static AssertThrowsStrategyType fromValue(String value) {
        for (AssertThrowsStrategyType type : AssertThrowsStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
