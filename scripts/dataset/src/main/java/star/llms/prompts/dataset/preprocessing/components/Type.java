package star.llms.prompts.dataset.preprocessing.components;

import java.util.List;
import java.util.Optional;

/**
 * Represents the type of a field or the return type of a method in a Java class.
 * The type can be a primitive type, a reference type, or a generic type.
 * The signature of the type is represented as a string, denoted as `identifier`.
 * The class of the type is represented as a {@link Clazz} record object. It can be null if
 * the type is a primitive type or a generic type, or if the class is not available (for example if
 * it is a class of an external library of the repository or if the information is not required).
 */
public record Type(
        /* The full identifier of the type. Example: `Type` or `List<Type>`, etc. */
        String identifier,
        /* List of fully qualified identifiers, providing additional information about the tyep (if available) */
        List<String> fullyQualifiedIdentifiers,
        /* A boolean flag to check if the type is void */
        boolean isVoid,
        /* A boolean flag to check if the type is primitive */
        boolean isPrimitive,
        /* A boolean flag to check if the type is generic */
        boolean isGeneric,
        /* An int measuring the array dimension (0 if the type is not an array) */
        int arrayLevel,
        /* The information about the classes involved in the type (it can be null) */
        Optional<List<Clazz>> clazzes
) {}
