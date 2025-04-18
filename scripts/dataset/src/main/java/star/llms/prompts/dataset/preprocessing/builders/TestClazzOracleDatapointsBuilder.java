package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.*;

import java.util.List;

public class TestClazzOracleDatapointsBuilder {

    private String junitVersion;
    private TestClazz testClass;
    private Clazz focalClass;
    private List<OracleDatapoint> datapoints;

    public TestClazzOracleDatapoints build() {
        return new TestClazzOracleDatapoints(
            this.junitVersion,
            this.testClass,
            this.focalClass,
            this.datapoints
        );
    }

    /**
     * Set the junit version of the TestClazzOracleDatapoints.
     *
     * @param junitVersion the junit version used in the test class to generate assertions
     */
    public void setJunitVersion(String junitVersion) {
        this.junitVersion = junitVersion;
    }

    /**
     * Set the test class of the TestClazzOracleDatapoints.
     *
     * @param testClass the test class
     */
    public void setTestClass(TestClazz testClass) {
        this.testClass = testClass;
    }

    /**
     * Set the focal class of the TestClazzOracleDatapoints.
     *
     * @param focalClass the focal class
     */
    public void setFocalClass(Clazz focalClass) {
        this.focalClass = focalClass;
    }

    /**
     * Set the list of datapoints of the TestClazzOracleDatapoints.
     *
     * @param datapoints the list of datapoints
     */
    public void setDatapoints(List<OracleDatapoint> datapoints) {
        this.datapoints = datapoints;
    }

}
