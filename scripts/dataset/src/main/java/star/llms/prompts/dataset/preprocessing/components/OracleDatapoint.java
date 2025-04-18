package star.llms.prompts.dataset.preprocessing.components;

/**
 * Represents an Oracle Datapoint.
 * An Oracle Datapoint is a tuple with the following elements:
 * <ol>
 *    <li>The test prefix, i.e. the corpus of the test with all the preliminary steps to drive the system into a specific state</li>
 *    <li>The target, i.e. the next assertion to generate by the model</li>
 * </ol>
 * The Oracle Datapoint is part of the information used to train the model to generate the next assertion to be added to the given test prefix
 */
public record OracleDatapoint(
        /* The test prefix */
        Callable testPrefix,
        /* The target, i.e. the next assertion to generate */
        String target
) {}
