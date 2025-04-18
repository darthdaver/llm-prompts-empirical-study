package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.*;

public class OracleDatapointBuilder {

    private Callable testPrefix;
    private String target;

    public OracleDatapoint build() {
        return new OracleDatapoint(
            this.testPrefix,
            this.target
        );
    }

    /**
     * Set the test prefix of the OracleDatapoint.
     *
     * @param testPrefix the test prefix
     */
    public void setTestClass(Callable testPrefix) {
        this.testPrefix = testPrefix;
    }

    /**
     * Set the target of the OracleDatapoint.
     *
     * @param target the target
     */
    public void setTarget(String target) {
        this.target = target;
    }
}
