package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.Field;
import star.llms.prompts.dataset.preprocessing.components.Type;

import java.util.List;

public class FieldBuilder {
    private String identifier;
    private String signature;
    private String declarator;
    private List<String> modifiers;
    private Type type;

    public Field build() {
        return new Field(
            this.identifier,
            this.signature,
            this.declarator,
            this.modifiers,
            this.type
        );
    }

    /**
     * Set the name of the field.
     *
     * @param identifier the field name
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Set the full signature of the field.
     *
     * @param signature the full signature of the field
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Set the declarator of the field.
     *
     * @param declarator the declarator of the field
     */
    public void setDeclarator(String declarator) {
        this.declarator = declarator;
    }

    /**
     * Set the modifiers of the field.
     *
     * @param modifiers the list of modifiers of the field
     */
    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * Set the type of the field.
     *
     * @param type the type of the field
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Reset the builder.
     */
    public void reset() {
        this.identifier = null;
        this.signature = null;
        this.declarator = null;
        this.modifiers = null;
        this.type = null;
    }
}
