package star.llms.prompts.dataset.data.enums;

public enum JUnitAssertionType {
    ASSERT_ALL("assertAll"),
    ASSERT_ARRAY_EQUALS("assertArrayEquals"),
    ASSERT_DOES_NOT_THROW("assertDoesNotThrow"),
    ASSERT_EQUALS("assertEquals"),
    ASSERT_FALSE("assertFalse"),
    ASSERT_INSTANCE_OF("assertInstanceOf"),
    ASSERT_ITERABLE_EQUALS("assertIterableEquals"),
    ASSERT_LINES_MATCH("assertLinesMatch"),
    ASSERT_NOT_EQUALS("assertNotEquals"),
    ASSERT_NOT_NULL("assertNotNull"),
    ASSERT_NOT_SAME("assertNotSame"),
    ASSERT_NULL("assertNull"),
    ASSERT_SAME("assertSame"),
    ASSERT_THAT("assertThat"),
    ASSERT_THROWS("assertThrows"),
    ASSERT_THROWS_EXACTLY("assertThrowsExactly"),
    ASSERT_TIMEOUT("assertTimeout"),
    ASSERT_TIMEOUT_PREEMPTIVELY("assertTimeoutPreemptively"),
    ASSERT_TRUE("assertTrue"),
    FAIL("fail");

    private final String assertionMethodName;

    JUnitAssertionType(String assertionMethodName) {
        this.assertionMethodName = assertionMethodName;
    }

    public String getAssertionMethodName() {
        return assertionMethodName;
    }

    public static JUnitAssertionType fromString(String assertionMethodName) {
        for (JUnitAssertionType value : JUnitAssertionType.values()) {
            if (value.assertionMethodName.equals(assertionMethodName)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No constant with assertionMethodName " + assertionMethodName + " found");
    }

    public static boolean isJUnitAssertion(String assertionMethodName) {
        for (JUnitAssertionType value : JUnitAssertionType.values()) {
            if (value.assertionMethodName.equals(assertionMethodName)) {
                return true;
            }
        }
        return false;
    }
}
