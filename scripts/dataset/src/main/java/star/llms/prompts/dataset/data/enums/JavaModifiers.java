package star.llms.prompts.dataset.data.enums;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum JavaModifiers {
    PUBLIC("public"),
    PRIVATE("private"),
    PROTECTED("protected"),
    DEFAULT("default"),
    STATIC("static"),
    FINAL("final"),
    SYNCHRONIZED("synchronized"),
    ABSTRACT("abstract"),
    NATIVE("native"),
    STRICTFP("strictfp"),
    TRANSIENT("transient"),
    VOLATILE("volatile");

    private final String javaModifier;

    JavaModifiers(String javaModifier) {
        this.javaModifier = javaModifier;
    }

    public String getJavaModifier() {
        return javaModifier;
    }

    public List<String> getListOfJavaModifiers() {
        return Stream.of(JavaModifiers.values()).map(JavaModifiers::getJavaModifier).collect(Collectors.toList());
    }
}
