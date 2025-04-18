package star.llms.prompts.dataset.data.enums;

public enum JUnitAnnotationType {
    AFTER("After"),
    AFTER_ALL("AfterAll"),
    AFTER_EACH("AfterEach"),
    AFTER_CLASS("AfterClass"),
    BEFORE("Before"),
    BEFORE_ALL("BeforeAll"),
    BEFORE_CLASS("BeforeClass"),
    BEFORE_EACH("BeforeEach"),
    CATEGORY("Category"),
    CSV_SOURCE("CsvSource"),
    CSV_FILE_SOURCE("CsvFileSource"),
    DISABLED("Disabled"),
    DISPLAY_NAME("DisplayName"),
    DISPLAY_NAME_GENERATION("DisplayNameGeneration"),
    ENUM_SOURCE("EnumSource"),
    IGNORE("Ignore"),
    INDICATIVE_SENTENCES_GENERATION("IndicativeSentencesGeneration"),
    METHOD_SOURCE("MethodSource"),
    NESTED("Nested"),
    ORDER("Order"),
    PARAMETERS("Parameters"),
    PARAMETERIZED_TEST("ParameterizedTest"),
    REPEATED_TEST("RepeatedTest"),
    RUN_WITH("RunWith"),
    TAG("Tag"),
    TAGS("Tags"),
    TEST("Test"),
    TEST_FACTORY("TestFactory"),
    TEST_INSTANCE("TestInstance"),
    TEST_METHOD_ORDER("TestMethodOrder"),
    TEST_TEMPLATE("TestTemplate"),
    TIMEOUT("Timeout"),
    VALUE_SOURCE("ValueSource");

    private final String annotationName;

    JUnitAnnotationType(String annotationName) {
        this.annotationName = annotationName;
    }

    public String getAnnotationName() {
        return annotationName;
    }

    public static JUnitAnnotationType fromString(String annotationName) {
        for (JUnitAnnotationType value : JUnitAnnotationType.values()) {
            if (value.annotationName.equals(annotationName)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No constant with annotationName " + annotationName + " found");
    }

    public static boolean isJunitAnnotation(String annotationName) {
        for (JUnitAnnotationType value : JUnitAnnotationType.values()) {
            if (value.annotationName.equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTestAnnotation(String annotationName) {
        return annotationName.equals(TEST.getAnnotationName()) || annotationName.equals(PARAMETERIZED_TEST.getAnnotationName()) || annotationName.equals(REPEATED_TEST.getAnnotationName()) || annotationName.equals(TEST_FACTORY.getAnnotationName()) || annotationName.equals(TEST_TEMPLATE.getAnnotationName());
    }

    public static boolean isSetUpTearDownAnnotation(String annotationName) {
        return isJunitAnnotation(annotationName) && !isTestAnnotation(annotationName);
    }
}
