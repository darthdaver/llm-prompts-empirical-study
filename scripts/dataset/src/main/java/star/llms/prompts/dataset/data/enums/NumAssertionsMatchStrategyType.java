package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NumAssertionsMatchStrategyType {
    STRICT("strict"),
    LOOSE("loose");

    private final String type;

    NumAssertionsMatchStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }

    @JsonCreator
    public static NumAssertionsMatchStrategyType fromValue(String value) {
        for (NumAssertionsMatchStrategyType type : NumAssertionsMatchStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
