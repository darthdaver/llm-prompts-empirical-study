package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.data.enums.JUnitAssertionType;
import star.llms.prompts.dataset.preprocessing.components.TestStats;
import java.util.HashMap;

/**
 * Builder for the {@link star.llms.prompts.dataset.preprocessing.components.TestStats} class.
 * <p>
 * This class is used to build a {@link star.llms.prompts.dataset.preprocessing.components.TestStats} object, which
 * represents the statistics of a test case.
 * It provides methods to set the values of the statistics.
 */
public class TestStatsBuilder {
    private String identifier;
    private String signature;
    private String classIdentifier;
    private String filePath;
    private  int testLength;
    private  int numberOfAssertions;
    private  int numberOfMethodCalls;
    private  int numberOfVariables;
    private HashMap<String, Integer> assertionsDistribution;

    /**
     * Builds a {@link star.llms.prompts.dataset.preprocessing.components.TestStats} object with the values set in this builder.
     *
     * @return a {@link star.llms.prompts.dataset.preprocessing.components.TestStats} object
     */
    public TestStats build() {
        return new TestStats(
                this.identifier,
                this.signature,
                this.classIdentifier,
                this.filePath,
                this.testLength,
                this.numberOfAssertions,
                this.numberOfMethodCalls,
                this.numberOfVariables,
                this.assertionsDistribution
        );
    }

    /**
     * Sets the identifier of the test case.
     *
     * @param identifier the identifier of the test case
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Sets the signature of the test case.
     *
     * @param signature the signature of the test case
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Sets the identifier of the test class.
     *
     * @param classIdentifier the identifier of the test class
     */
    public void setClassIdentifier(String classIdentifier) {
        this.classIdentifier = classIdentifier;
    }

    /**
     * Sets the file path of the test case.
     *
     * @param filePath the file path of the test case
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Sets the number of assertions in the test case.
     *
     * @param numberOfAssertions the number of assertions
     */
    public void setNumberOfAssertions(int numberOfAssertions) {
        this.numberOfAssertions = numberOfAssertions;
    }

    /**
     * Sets the length of the test case.
     *
     * @param testLength the length of the test case
     */
    public void setTestLength(int testLength) {
        this.testLength = testLength;
    }

    /**
     * Sets the number of variables in the test case.
     *
     * @param numberOfVariables the number of variables
     */
    public void setNumberOfVariables(int numberOfVariables) {
        this.numberOfVariables = numberOfVariables;
    }

    /**
     * Sets the number of method calls in the test case.
     *
     * @param numberOfMethodCalls the number of method calls
     */
    public void setNumberOfMethodCalls(int numberOfMethodCalls) {
        this.numberOfMethodCalls = numberOfMethodCalls;
    }

    /**
     * Increment the counter of the assertion type detected within the test case.
     *
     * @param assertionsDistribution the assertion to increment
     */
    public void setAssertionsDistribution(HashMap<String,Integer> assertionsDistribution) {
        this.assertionsDistribution = assertionsDistribution;
    }

}
