package star.llms.prompts.dataset.data.enums;

public enum NamingConvention {
    NORMALIZED_TEST_CLASS("STARNormalizedTest"),
    NORMALIZED_TEST_FILE("STARNormalizedTest.java"),
    TEST_SPLIT_CLASS("STARSplitTest"),
    TEST_SPLIT_FILE("STARSplitTest.java"),
    LIB_FOLDER("processed_libs"),
    SOURCE_LIB_FOLDER("processed_libs/sources"),
    JAR_LIB_FOLDER("processed_libs/jars"),
    DECOMPILED_LIB_FOLDER("processed_libs/decompiled"),
    JAVA_CLASSPATHS_JSON("lib/java_classpaths.json"),;

    private final String conventionName;

    NamingConvention(String conventionName) {
        this.conventionName = conventionName;
    }

    public String getConventionName() {
        return conventionName;
    }
}
