package star.llms.prompts.dataset.preprocessing.components;

import java.util.List;

public record Clazz(
        /* The name of the class. Example: `ClassName` */
        String identifier,
        /* The package of the class. Example: `star.llms.prompts.dataset.preprocessing.components` */
        String packageIdentifier,
        /* The list of inherited classes. Example: `["SuperClass1, SuperClass2"]` */
        List<String> superclasses,
        /* The interfaces implemented by the class. Example: `["Interface1, Interface2"]` */
        List<String> interfaces,
        /* The relative path of the class. Example: `src/main/[java/test]/[path_to_class]/ClassName.java` */
        String filePath,
        /* The list of fields defined in the class (public, protected, private). */
        List<Field> fields,
        /* The list of constructors defined in the class */
        List<Callable> constructors,
        /* The list of methods defined in the class */
        List<Callable> methods

) {}
