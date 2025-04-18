package star.llms.prompts.dataset.data.enums;

public enum JUnitVersion {
    JUNIT4("JUnit 4"),
    JUNIT5("JUnit 5");

    private final String version;

    JUnitVersion(String annotationName) {
        this.version = annotationName;
    }

    public String getVersion() { return version; }
}
