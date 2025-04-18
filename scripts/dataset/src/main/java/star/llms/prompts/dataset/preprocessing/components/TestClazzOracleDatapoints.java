package star.llms.prompts.dataset.preprocessing.components;

import java.util.List;

/**
 * A full representation of an Oracle Datapoint.
 * An Oracle Datapoint is a tuple of the following elements:
 * <ol>
 *     <li>The junit version used within the test class to generate assertions</li>
 *    <li>The test class, i.e. the class containing the test case from which the oracles datapoints are extracted.</li>
 *    <li>The focal class, i.e. the corresponding class tested in the test class</li>
 *    <li>The list of datapoints extracted from the test cases at any occurrence of an assertion, in the test class</li>
 * </ol>
 * The Oracle Datapoint is used to train the model to generate the next assertion to be added to the given test prefix
 */
public record TestClazzOracleDatapoints(
        /* The junit version used within the test class to generate the assertions */
        String junitVersion,
        /* The test class */
        TestClazz testClass,
        /* The corresponding focal class */
        Clazz focalClass,
        /* The list of datapoints associated to the test cases defined within the test class */
        List<OracleDatapoint> datapoints
) {
}
