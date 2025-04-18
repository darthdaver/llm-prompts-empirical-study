package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.Clazz;
import star.llms.prompts.dataset.preprocessing.components.Type;

import java.util.List;
import java.util.Optional;

public class TypeBuilder {

    private String identifier;
    private List<String> fullyQualifiedIdentifiers;
    private boolean isVoid;
    private boolean isPrimitive;
    private boolean isGeneric;
    private int arrayLevel;
    private List<Clazz> clazzes;

    public Type build() {
        return new Type(
                this.identifier,
                this.fullyQualifiedIdentifiers,
                this.isVoid,
                this.isPrimitive,
                this.isGeneric,
                this.arrayLevel,
                this.clazzes.size() > 0 ? Optional.of(this.clazzes) : Optional.empty()
        );
    }

    /**
     * Set the identifier of the type.
     *
     * @param identifier the type identifier
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Set the fullyQualifiedIdentifiers of the type.
     *
     * @param fullyQualifiedIdentifiers the fullyQualifiedIdentifier of the type
     */
    public void setFullyQualifiedIdentifiers(List<String> fullyQualifiedIdentifiers) {
        this.fullyQualifiedIdentifiers = fullyQualifiedIdentifiers;
    }

    /**
     * Set the isVoid boolean attribute. True if the corresponding type is a void type.
     * False otherwise.
     *
     * @param isVoid the type identifier
     */
    public void setIsVoid(boolean isVoid) {
        this.isVoid = isVoid;
    }

    /**
     * Set the isPrimitive boolean attribute. True if the corresponding type is a primitive type.
     * False otherwise.
     *
     * @param isPrimitive the type identifier
     */
    public void setIsPrimitive(boolean isPrimitive) {
        this.isPrimitive = isPrimitive;
    }

    /**
     * Set the isGeneric boolean attribute. True if the corresponding type is a generic type.
     * False otherwise.
     *
     * @param isGeneric the type identifier
     */
    public void setIsGeneric(boolean isGeneric) {
        this.isGeneric = isGeneric;
    }

    /**
     * Set the isArray boolean attribute. True if the corresponding type is an array type.
     * False otherwise.
     *
     * @param arrayLevel the type identifier
     */
    public void setArrayLevel(int arrayLevel) {
        this.arrayLevel = arrayLevel;
    }

    /**
     * Set the class of the type.
     *
     * @param clazz the class of the type. It can be null
     */
    public void setClazzes(List<Clazz> clazz) {
        this.clazzes = clazz;
    }
}
