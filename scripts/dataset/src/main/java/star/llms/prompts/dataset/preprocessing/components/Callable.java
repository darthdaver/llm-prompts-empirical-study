package star.llms.prompts.dataset.preprocessing.components;


import org.javatuples.Pair;

import java.util.List;

/**
 * Represents a Java method or constructor, describing all the information in a structured way.
 * The record can describe a test method (test prefix), a source method or constructor (focal method or constructor).
 * The method contains:
 * <ol>
 *     <li>the name of the method or constructor</li>
 *     <li>the annotations of the method or constructor</li>
 *     <li>the modifiers of the method or constructor</li>
 *     <li>the signature of the method or constructor</li>
 *     <li>the exceptions thrown by the method or constructor</li>
 *     <li>the parameters of the method or constructor</li>
 *     <li>the return type of the method or constructor</li>
 *     <li>the documentation of the method or constructor</li>
 *     <li>the body of the method or constructor</li>
 *     <li>the list of the other methods invoked within it. It can be null if the information are not provided or
 *     retrieved. It can also contain a subset list of methods retrieved, if some of them cannot be collected (for
 *     example if the method are defined within an external library).</li>
 * </ol>
 */
public record Callable(
        /* The name of the method or constructor. Example: `methodName` */
        String identifier,
        /* The annotations of the method or constructor. Example: `["@Test"]` */
        List<String> annotations,
        /* The modifiers of the method or constructor. Example: `["public", "static"]` */
        List<String> modifiers,
        /* The signature of the method or constructor. Example: `public void testMethod(ParamType1 param1, ParamType2 param2)` */
        String signature,
        /* The exceptions thrown by the method or constructor. Example: `["Exception1", "Exception2"]` */
        List<String> thrownExceptions,
        /* The parameters of the method or constructor. Example: `[{ name: "param1", type: {@link Type} }, { name: "param2", type: {@link Type} } ]` */
        List<Pair<String, Type>> parameters,
        /* The return type of the method or constructor. */
        Type returnType,
        /* The documentation of the method or constructor. */
        String javadoc,
        /* The body of the method or constructor. */
        String body,
        /* The list of methods invoked within the method or constructor. It can be null. */
        List<Callable> invokedMethods
) {}
