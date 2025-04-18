package star.llms.prompts.dataset.preprocessing.components;

import java.util.List;

public record TestClazz(
        /* The name of the test class. Example: `TestClassName` */
        String identifier,
        /* The package of the class. Example: `star.llms.prompts.dataset.preprocessing.components` */
        String packageIdentifier,
        /* The list of inherited classes. Example: `["SuperClass1, SuperClass2"]` */
        List<String> superclasses,
        /* The interfaces implemented by the test class. Example: `["Interface1, Interface2"]` */
        List<String> interfaces,
        /* The relative path of the test class. Example: `src/main/[java/test]/[path_to_class]/TestClassName.java` */
        String filePath,
        /* The list of fields defined in the test class (public, protected, private). */
        List<Field> fields,
        /* The list of auxiliary methods defined in the test class (that are not test cases). */
        List<Callable> auxiliaryMethods,
        /* The list of setup & tear down methods defined in the test class. */
        List<Callable> setupTearDownMethods,
        /* The list of test cases defined in the test class. */
        List<Callable> testCases
) {}
