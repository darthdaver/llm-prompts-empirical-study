package star.llms.prompts.dataset.preprocessing.components;

import star.llms.prompts.dataset.data.enums.JUnitAssertionType;

import java.util.HashMap;

/*
 * Represents the statistics of a test.
 * The statistics of a test are used to evaluate the quality of the test and categorize them, according to
 * their properties.
 */
public record TestStats(
        /* The identifier of the test case. Example: `testMethodName` */
        String identifier,
        /* The signature of the method or constructor. Example: `public void testMethod(ParamType1 param1, ParamType2 param2)` */
        String signature,
        /* The test class identifier. Example: `TestClassName` */
        String classIdentifier,
        /* The file path of the test case. */
        String filePath,
        /* The length of the test case measured in terms of string characters in the body */
        int testLength,
        /* The number of assertions detected within the test case */
        int numberOfAssertions,
        /* The number of method calls in the test case */
        int numberOfMethodCalls,
        /* The number of variables defined and used within the test case */
        int numberOfVariables,
        /* The counter of the different types of assertions detected within the test case */
        HashMap<String,Integer> assertionsDistribution
) {}