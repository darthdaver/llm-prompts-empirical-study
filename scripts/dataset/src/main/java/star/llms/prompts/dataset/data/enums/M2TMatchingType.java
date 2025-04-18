package star.llms.prompts.dataset.data.enums;

public enum M2TMatchingType {
    /* Generate the matching between the test and the corresponding focal methods, considering all the occurrence of methods within the test */
    ANY_OCCURRENCE,
    /* Generate the matching between the test and the corresponding focal methods, considering only the last occurrence of a method within the test */
    LAST_OCCURRENCE
}
