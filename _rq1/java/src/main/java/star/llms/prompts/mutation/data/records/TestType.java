package star.llms.prompts.mutation.data.records;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum TestType {
    INFERENCE("inference"),
    NO_ORACLE("no_oracle"),
    PROTECTED("protected");

    private final String testType;

    TestType(String testType) {
        this.testType = testType;
    }

    public String getTestType() {
        return testType;
    }

    public String getTestTypeTestLabel() {
        if (this == INFERENCE) {
            return "InferenceTest";
        } else {
            return "NoOracleTest";
        }
    }

}
