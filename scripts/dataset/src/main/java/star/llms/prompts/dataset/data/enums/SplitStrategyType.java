package star.llms.prompts.dataset.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SplitStrategyType {
    ASSERTION("assertion"),
    STATEMENT("statement");

    private final String type;

    SplitStrategyType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() { return type; }

    @JsonCreator
    public static SplitStrategyType fromValue(String value) {
        for (SplitStrategyType type : SplitStrategyType.values()) {
            if (type.getType().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}