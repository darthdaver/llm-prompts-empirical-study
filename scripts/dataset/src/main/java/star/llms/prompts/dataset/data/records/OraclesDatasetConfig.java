package star.llms.prompts.dataset.data.records;
import com.fasterxml.jackson.annotation.JsonProperty;
import star.llms.prompts.dataset.data.enums.*;

/**
 * The record class maps a JSON object representing the configuration choices
 * for the oracles dataset into a Java object.
 */
public record OraclesDatasetConfig(
        /* The split strategy: statement or assertion. */
        @JsonProperty("split") SplitStrategyType splitStrategy,
        /* The try-catch-finally strategy: standard or flat. */
        @JsonProperty("try-catch-finally") TryCatchFinallyStrategyType tryCatchFinallyStrategy,
        /* The assert-throws strategy: standard or flat. */
        @JsonProperty("assertThrows") AssertThrowsStrategyType assertThrowsStrategy,
        /* The statement strategy: keep, mask, or remove. */
        @JsonProperty("statement") StatementStrategyType statementStrategy,
        /* The condition strategy: keep, mask, or remove. */
        @JsonProperty("condition") AssertionStrategyType conditionStrategy,
        /* The assertion strategy for the oracles dataset: keep, mask, or remove. */
        @JsonProperty("assertion") AssertionStrategyType assertionStrategy,
        /* The target of the dataset: statement or assertion. */
        @JsonProperty("target") TargetStrategyType targetStrategy,
        /* The mask label in case the assertion strategy is set to mask. Can be null */
        @JsonProperty("mask-placeholder") String mask,
        /* The no-assertion target in case the split strategy is set to statement. Can be null */
        @JsonProperty("no-assertion-target-placeholder") String noAssertionTarget,
        /* Boolean flag for the integration of the statements of auxiliary methods called within the test cases */
        @JsonProperty("integrate-auxiliary-methods") boolean integrateAuxiliaryMethods,
        /* Boolean flag for the integration of the statements after the last assertion */
        @JsonProperty("keep-statements-after-last-assertion") boolean keepStatementsAfterLastAssertion
) {}
