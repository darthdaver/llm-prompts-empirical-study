package star.llms.prompts.dataset.preprocessing.components;

import java.util.List;

/**
 * Represents a field defined within in a Java class.
 */
public record Field(
        /* The name of the field. Example: `fieldName` */
        String identifier,
        /* The full signature of the field. Example: `public static final FieldType field1 = "Field Value"` */
        String fullSignature,
        /* The declarator of the field. Example: `field1 = "Field Value"` */
        String declarator,
        /* The modifiers of the field. Example: `["public", "static", "final"]` */
        List<String> modifiers,
        /* The type of the field. Example: `FieldType` */
        Type type
){}
