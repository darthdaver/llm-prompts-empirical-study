package star.llms.prompts.dataset.utils.javaParser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javassistmodel.JavassistMethodDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import star.llms.prompts.dataset.data.enums.CallableExprType;
import star.llms.prompts.dataset.data.enums.JUnitAssertionType;
import star.llms.prompts.dataset.data.enums.NamingConvention;
import star.llms.prompts.dataset.data.exceptions.CandidateCallableDeclarationNotFoundException;
import star.llms.prompts.dataset.data.exceptions.CandidateCallableMethodUsageNotFoundException;
import star.llms.prompts.dataset.data.exceptions.MultipleCandidatesException;
import star.llms.prompts.dataset.data.exceptions.UnrecognizedExprException;
import star.llms.prompts.dataset.utils.FilesUtils;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.helper.StmtVisitorHelper;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.plumelib.util.CollectionsPlume.mapList;

/**
 * This class provides static methods for JavaParser utilities.
 */
public class JavaParserUtils {

    private static JavaParser javaParser;
    private static final Logger logger = LoggerFactory.getLogger(JavaParserUtils.class);
    /** Regex to match the Javadoc of a class or method. */
    private static final Pattern javadocPattern = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
    /** Regex to match the binary name of a class (e.g. "package.submodule.InnerClass$OuterClass") */
    private static final Pattern PACKAGE_CLASS = Pattern.compile("[a-zA-Z_][a-zA-Z\\d_]*(\\.[a-zA-Z_][a-zA-Z\\d_]*)*");
    /** Regex to match {@link ReflectionMethodDeclaration#toString()}. */
    private static final Pattern REFLECTION_METHOD_DECLARATION = Pattern.compile(
            "^ReflectionMethodDeclaration\\{method=((.*) )?\\S+ \\S+\\(.*}$"
    );
    /** Regex to match {@link JavassistMethodDeclaration#toString()}. */
    private static final Pattern JAVASSIST_METHOD_DECLARATION = Pattern.compile(
            "^JavassistMethodDeclaration\\{ctMethod=.*\\[((.*) )?\\S+ \\(.*\\).*]}$"
    );
    /** Artificial class name. */
    private static final String SYNTHETIC_CLASS_NAME = "Tracto__AuxiliaryClass";
    /** Artificial class source code. */
    private static final String SYNTHETIC_CLASS_SOURCE = "public class " + SYNTHETIC_CLASS_NAME + " {}";
    /** Artificial method name. */
    private static final String SYNTHETIC_METHOD_NAME = "__tracto__auxiliaryMethod";



    /** Do not instantiate this class. */
    private JavaParserUtils() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * Creates a JavaParser object capable of resolving symbols from a given
     * source directory (like a java repository).
     *
     * @param repoRootPath the path to a Java repository (containing a single or multiple Java projects)
     * @param classpath a string containing additional paths to libraries and jars (each reference in the
     *                  classpath is separated by a ":")
     * @return the corresponding JavaParser
     */
    public static JavaParser setRepoJavaParser(Path repoRootPath, String classpath) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        // TODO: Verify code without ReflectionTypeSolver and using java source code libraries
        // typeSolver.add(new ReflectionTypeSolver());
        List<Path> repoProjectsPaths = findPotentialProjectsRoots(repoRootPath);
        for(Path projectPath : repoProjectsPaths) {
            typeSolver.add(new JavaParserTypeSolver(projectPath.toFile()));
        }
        String[] classpathElements = classpath.split(":");
        for (String classPathElement : classpathElements) {
            if (classPathElement.endsWith(".jar")) {
                try {
                    typeSolver.add(new JarTypeSolver(classPathElement));
                } catch (IOException e) {
                    throw new Error("Unable to resolve jar " + classPathElement, e);
                }
            } else {
                Path startPath = Paths.get(classPathElement);
                try {
                    List<Path> libraryRootPaths = findPotentialProjectsRoots(Path.of(classPathElement));

                    if (!libraryRootPaths.isEmpty()) {
                        for(Path libraryRootPath : libraryRootPaths) {
                            typeSolver.add(new JavaParserTypeSolver(libraryRootPath.toFile()));
                        }
                    } else {
                        boolean jarFilesFolder = false;
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(startPath, "*.jar")) {
                            for (Path entry : stream) {
                                if (Files.isRegularFile(entry)) {
                                    jarFilesFolder = true;
                                }
                            }
                        }
                        if (jarFilesFolder) {
                            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    // Check if the file is a .jar file
                                    if (file.toString().endsWith(".jar")) {
                                        typeSolver.add(new JarTypeSolver(file));
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolSolver);
        javaParser = new JavaParser(parserConfiguration);
        return javaParser;
    }

    /**
     * Gets a compilation unit from a Java file path.
     *
     * @param path a Java file
     * @return the corresponding JavaParser compilation unit. Returns
     * {@code Optional.empty()} if an error occurs while attempting to parse
     * the file.
     */
    public static CompilationUnit getCompilationUnit(Path path) throws IOException {
        if (javaParser == null) {
            throw new IllegalStateException("JavaParser must be set with a repository root path, before calling this method.");
        }
        return javaParser.parse(path).getResult().orElseThrow();
    }

    /**
     * Generate a full signature of a variable declaration, given the list of its modifiers.
     * A signature follows the format:
     *     "[modifiers] [type] [variableName] [=] [variableValue];"
     *
     * @param modifiers the list of java modifiers of the variable declarator.
     * @param variable the variable from which to generate the full signature
     * @return the full signature as string
     */
    public static String getSignatureFromVariableDeclarator(List<String> modifiers, VariableDeclarator variable) {
        return modifiers.stream().collect(Collectors.joining(" ")) + " " + variable.getType().asString() + " " + variable + ";";
    }

    /**
     * Returns the signature of a JavaParser method declaration.
     *
     * @param methodDeclaration a JavaParser method declaration
     * @return a string representation of the signature. Signature follows the
     * format:
     *  "[modifiers] [typeParameters] [type] [methodName]([parameters]) throws [exceptions]"
     */
    public static String getMethodSignature(MethodDeclaration methodDeclaration) {
        String method = methodDeclaration.toString();
        if (methodDeclaration.getBody().isPresent()) {
            // Remove body
            method = method.replace(methodDeclaration.getBody().get().toString(), "");
        }
        for (Node comment: methodDeclaration.getAllContainedComments()) {
            // Remove comments within method signature
            method = method.replace(comment.toString(), "");
        }
        // Last line is method signature, remove everything before that
        method = method.replaceAll("[\\s\\S]*\n", "");
        return method.trim().replaceAll(";$", "");
    }

    /**
     * Returns the package name of a class from a {@link TypeDeclaration} (wrapped in an {@link Optional} or an empty
     * optional if the fully qualified name of the type declaration cannot be retrieved.
     * @param typeDeclaration a JavaParser type declaration representing a Java class
     * @return the package name of the class as a string (wrapped in  an {@link Optional}), or an empty optional if the
     * fully qualified name of the type declaration cannot be retrieved
     * @throws IllegalStateException if the fully qualified name of the type declaration cannot be retrieved
     */
    public static Optional<String> getPackageNameFromTypeDeclaration(TypeDeclaration typeDeclaration) {
        Optional<String> fullyQualifiedName = typeDeclaration.getFullyQualifiedName();
        if (fullyQualifiedName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fullyQualifiedName.get().substring(0, fullyQualifiedName.get().lastIndexOf('.')));
    }

    public static List<String> getExceptionsAsString(List<ReferenceType> exceptions) {
        return exceptions.stream().map(ReferenceType::asString).collect(Collectors.toList());
    }

    /**
     * Gets the modifiers of a JavaParser MethodUsage. Unfortunately, the
     * implementations of ResolvedMethodDeclaration have different approaches
     * for storing modifier information. This method uses regexes to handle
     * retrieving this information from {@link ReflectionMethodDeclaration} or
     * {@link JavassistMethodDeclaration} implementations of
     * ResolvedMethodDeclaration.
     *
     * @param methodDeclaration a resolved method declaration. Must be either
     *                          {@link ReflectionMethodDeclaration} or
     *                          {@link JavassistMethodDeclaration}.
     * @return the modifiers of the method
     */
    private static List<String> getMethodModifiers(ResolvedMethodDeclaration methodDeclaration) {
        Matcher reflectionMatcher = REFLECTION_METHOD_DECLARATION.matcher(methodDeclaration.toString());
        Matcher javassistMatcher = JAVASSIST_METHOD_DECLARATION.matcher(methodDeclaration.toString());
        if (!reflectionMatcher.find() && !javassistMatcher.find()) {
            throw new IllegalStateException("Could not parse method signature: " + methodDeclaration);
        }
        String methodModifiers;
        if (methodDeclaration instanceof ReflectionMethodDeclaration) {
            methodModifiers = reflectionMatcher.group(1);
        } else {
            methodModifiers = javassistMatcher.group(1);
        }
        if (methodModifiers == null) {
            methodModifiers = "";
        }
        return Stream.of(methodModifiers.split(" ")).map(m -> m.trim()).collect(Collectors.toList());
    }

    /**
     * Returns the signature of a JavaParser callable declaration. Uses the
     * method source code and removes method body, contained comments, the
     * Javadoc comment, and other special characters (e.g. "\n").
     *
     * @param jpCallable a JavaParser callable declaration
     * @return a string representation of the signature. A signature follows
     * the format:
     *     "[modifiers] [type] [methodName]([parameters]) throws [exceptions]"
     */
    public static String getCallableSignature(CallableDeclaration<?> jpCallable) {
        StringBuilder sb = new StringBuilder();
        // add modifiers
        List<String> modifiers = jpCallable.getModifiers()
                .stream()
                .map(Modifier::toString)
                .toList();
        sb.append(String.join("", modifiers));
        // add type parameters
        List<String> typeParameters = jpCallable.getTypeParameters()
                .stream()
                .map(TypeParameter::toString)
                .toList();
        if (!typeParameters.isEmpty()) {
            sb.append("<")
                    .append(String.join(", ", typeParameters))
                    .append(">")
                    .append(" ");
        }
        // add return type (if not a constructor)
        if (jpCallable.isMethodDeclaration()) {
            sb.append(jpCallable.asMethodDeclaration().getType())
                    .append(" ");
        }
        // add method name
        sb.append(jpCallable.getNameAsString());
        // add formal parameters
        List<String> parameters = jpCallable.getParameters()
                .stream()
                .map(com.github.javaparser.ast.body.Parameter::toString)
                .toList();
        sb.append("(")
                .append(String.join(", ", parameters))
                .append(")");
        // add exceptions
        List<String> exceptions = jpCallable.getThrownExceptions()
                .stream()
                .map(ReferenceType::asString)
                .toList();
        if (!exceptions.isEmpty()) {
            sb.append(" throws ")
                    .append(String.join(", ", exceptions));
        }
        return sb.toString().replaceAll(" +", " ").trim();
    }

    /**
     * Gets the method signature from a JavaParser MethodUsage. Unfortunately,
     * depending on the implementation of the ResolvedMethodDeclaration, it is
     * not possible to recover specific features, such as:
     * <ul>
     *     <li>Modifiers</li>
     *     <li>Annotations</li>
     *     <li>Parameter names</li>
     * </ul>
     * This method considers three implementations of
     * ResolvedMethodDeclaration: JavaParserMethodDeclaration,
     * ReflectionMethodDeclaration, and JavassistMethodDeclaration. A
     * signature follows the format:
     *     "[modifiers] [typeParameters] [type] [methodName]([parameters]) throws [exceptions]"
     * All type names do not use package names for compatibility with the
     * XText grammar.
     */
    public static String getMethodSignature(MethodUsage methodUsage) {
        ResolvedMethodDeclaration methodDeclaration = methodUsage.getDeclaration();
        // Consider JavaParserMethodDeclaration.
        if (methodDeclaration instanceof JavaParserMethodDeclaration jpMethodDeclaration) {
            MethodDeclaration jpMethod = jpMethodDeclaration.getWrappedNode();
            return getMethodSignature(jpMethod);
        }
        List<String> methodModifiers = getMethodModifiers(methodDeclaration);
        List<String> typeParameterList = getTypeParameters(methodUsage);
        List<String> formalParameterList = getParameters(methodUsage);
        List<String> exceptionList = getExceptions(methodUsage);
        return (methodModifiers.stream().collect(Collectors.joining(" ")) +
                " " + (typeParameterList.isEmpty() ? "" : "<" + String.join(", ", typeParameterList) + ">") +
                " " + getTypeWithoutPackages(methodDeclaration.getReturnType()) +
                " " + methodDeclaration.getName() +
                "(" + String.join(", ", formalParameterList) + ")" +
                (exceptionList.isEmpty() ? "" : " throws " + String.join(", ", exceptionList)))
                .replaceAll(" +", " ").trim();
    }

    /**
     * Gets the simple names of all exceptions that can be thrown by a given
     * method.
     *
     * @param methodUsage the method in the form of {@link MethodUsage}
     * @return a list of string representing the simple names of all the exceptions that can be thrown by the method
     */
    public static List<String> getExceptions(MethodUsage methodUsage) {
        List<ResolvedType> exceptions = methodUsage.getDeclaration().getSpecifiedExceptions();
        return mapList(JavaParserUtils::getTypeWithoutPackages, exceptions);
    }

    /**
     * Gets the Javadoc comment of a given class.
     *
     * @param jpClass a JavaParser class
     * @return the class Javadoc comment, in Javadoc format, surrounded by
     * "&#47;&#42;&#42; ... &#42;&#42;&#47;" for compatibility with the XText
     * grammar (empty string if not found)
     */
    public static String getClassJavadoc(TypeDeclaration<?> jpClass) {
        Optional<JavadocComment> optionalJavadocComment = jpClass.getJavadocComment();
        return optionalJavadocComment
                .map(javadocComment -> "/**" + javadocComment.getContent() + "*/")
                .orElseGet(() -> getJavadocByPattern(jpClass));
    }

    /**
     * Gets the Javadoc comment of a given method.
     *
     * @param jpCallable a JavaParser method
     * @return the method/constructor Javadoc comment, in Javadoc format,
     * surrounded by "&#47;&#42; ... &#42;&#47;" (empty string if not found)
     */
    public static String getCallableJavadoc(CallableDeclaration<?> jpCallable) {
        Optional<JavadocComment> optionalJavadocComment = jpCallable.getJavadocComment();
        return optionalJavadocComment
                .map(javadocComment -> "    /**" + javadocComment.getContent().replaceAll("@exception ", "@throws ") + "*/")
                .orElseGet(() -> getJavadocByPattern(jpCallable));
    }

    /**
     * Gets the source code of a given method or constructor.
     *
     * @param jpCallable a method or constructor
     * @return a string representation of the source code
     */
    public static String getCallableSourceCode(CallableDeclaration<?> jpCallable) {
        String jpSignature = JavaParserUtils.getCallableSignature(jpCallable);
        Optional<BlockStmt> jpBody = jpCallable instanceof MethodDeclaration
                ? ((MethodDeclaration) jpCallable).getBody()
                : Optional.ofNullable(((ConstructorDeclaration) jpCallable).getBody());
        return jpSignature + (jpBody.isEmpty() ? ";" : jpBody.get().toString());
    }

    /**
     * Gets the Javadoc comment of a class or method using a regex. This
     * method should ONLY be called by
     * {@link JavaParserUtils#getCallableJavadoc(CallableDeclaration)} or
     * {@link JavaParserUtils#getClassJavadoc(TypeDeclaration)} (e.g. after
     * attempting to recover the Javadoc using the JavaParser API).
     *
     * @param jpBody a Java class or method
     * @return the matched Javadoc comment surrounded by
     * "&#47;&#42;&#42; ... &#42;&#42;&#47;" for compatibility with the XText
     * grammar (empty string if not found)
     */
    private static String getJavadocByPattern(BodyDeclaration<?> jpBody) {
        String input = jpBody.toString();
        Matcher matcher = javadocPattern.matcher(input);
        if (matcher.find()) {
            String content = matcher.group(1);
            if (jpBody instanceof TypeDeclaration<?>) {
                // class javadoc format
                return "/**" + content + "*/";
            } else {
                // method javadoc format
                return "    /**" + content + "*/";
            }
        }
        return "";
    }

    /**
     * Gets all formal parameters in the method definition. This method
     * returns the type of each parameter, followed by an artificial name. For
     * example,
     *     "MethodUsage[get(int i)]"    &rarr;    "List.of("int arg1")"
     */
    private static List<String> getParameters(MethodUsage methodUsage) {
        ResolvedMethodDeclaration methodDeclaration = methodUsage.getDeclaration();
        // iterate through each parameter in the method declaration.
        List<String> methodParameters = new ArrayList<>();
        for (int i = 0; i < methodDeclaration.getNumberOfParams(); i++) {
            methodParameters.add(getTypeWithoutPackages(methodDeclaration.getParam(i).getType()) + " arg" + i);
        }
        return methodParameters;
    }

    /**
     * A resolved type may be void, primitive, an array, a reference type, etc.
     * (including arrays of reference types). If the type involves a reference
     * type, this method returns the fully qualified name without packages.
     *
     * @param resolvedType JavaParser resolved type
     * @return the name of a type without packages. If the resolved type is an
     * array of reference types, then this method removes the packages from
     * the fully qualified name of the element types.
     */
    public static String getTypeWithoutPackages(ResolvedType resolvedType) {
        String typeName = resolvedType.describe();
        ResolvedType elementType = getElementType(resolvedType);
        if (elementType.isReferenceType()) {
            // use the original type name to avoid removing array brackets
            return getTypeWithoutPackages(typeName);
        } else {
            return typeName;
        }
    }

    /**
     * Removes the package name from a fully qualified name of a type for
     * compatibility with the XText grammar. Also removes package from type
     * parameters.
     *
     * @param fullyQualifiedName a fully qualified name of a type
     * @return the type name without packages. Includes outer classes, e.g.,
     *     package.Outer.Inner    =>    Outer.Inner
     */
    public static String getTypeWithoutPackages(String fullyQualifiedName) {
        // regex is used instead of String.lastIndexOf to avoid removing outer classes
        Matcher matcher = PACKAGE_CLASS.matcher(fullyQualifiedName);
        // continuously remove packages until none remain
        while (matcher.find()) {
            if (matcher.group().contains(".")) {
                // gets the class name using a JavaParser ResolvedReferenceTypeDeclaration
                String classNameWithoutPackages = getResolvedReferenceTypeDeclaration(matcher.group())
                        .getClassName();
                // replaces all instances of the fully qualified name with the JavaParser type name
                fullyQualifiedName = fullyQualifiedName.replaceAll(
                        matcher.group(),
                        classNameWithoutPackages
                );
            }
        }
        return fullyQualifiedName;
    }

    /**
     * Returns the {@link ResolvedReferenceTypeDeclaration} of a given fully
     * qualified type name.
     *
     * @param fqName fully qualified type name, e.g., {@code java.util.List}
     * @return the corresponding JavaParser ResolvedReferenceTypeDeclaration
     * @throws UnsolvedSymbolException if the type cannot be resolved
     * @throws UnsupportedOperationException if the type is an array or
     * primitive type
     */
    public static ResolvedReferenceTypeDeclaration getResolvedReferenceTypeDeclaration(String fqName) throws UnsolvedSymbolException, UnsupportedOperationException {
        return getResolvedType(fqName).asReferenceType().getTypeDeclaration().get();
    }

    // TODO: Check that this method is not called with binary names as argument
    /**
     * @param fqName fully qualified name, e.g., {@code java.util.List}
     */
    public static ResolvedType getResolvedType(String fqName) throws UnsupportedOperationException {
        CompilationUnit cu = javaParser.parse(SYNTHETIC_CLASS_SOURCE).getResult().get();
        BlockStmt syntheticMethodBody = getClassOrInterface(cu, SYNTHETIC_CLASS_NAME).addMethod(SYNTHETIC_METHOD_NAME).getBody().get();
        syntheticMethodBody.addStatement(fqName + " type1Var;");
        return getClassOrInterface(cu, SYNTHETIC_CLASS_NAME)
                .getMethodsByName(SYNTHETIC_METHOD_NAME).get(0)
                .getBody().get()
                .getStatements().getLast().get()
                .asExpressionStmt().getExpression()
                .asVariableDeclarationExpr().getVariables().get(0)
                .resolve().getType();
    }

    /**
     * Retrieve a class declaration from a compilation unit, given its name.
     * @param cu the compilation unit
     * @param name the name of the type declaration to find within the compilation unit
     * @return the type declaration found
     * @throws RuntimeException if the type declaration with name {@code name} is not found within the compilation
     * unit {@code cu}
     */
    public static TypeDeclaration<?> getClassOrInterface(CompilationUnit cu, String name) {
        try {
            List<ClassOrInterfaceDeclaration> classOrInterfaceList = cu.getLocalDeclarationFromClassname(name);
            if (!classOrInterfaceList.isEmpty()) {
                return classOrInterfaceList.get(0);
            }
        } catch (NoSuchElementException ignored) {
            // There are other alternatives to retrieve class, interface, etc., as below
        }
        Optional<ClassOrInterfaceDeclaration> classOrInterface = cu.getClassByName(name);
        if (classOrInterface.isPresent()) {
            return classOrInterface.get();
        }
        Optional<ClassOrInterfaceDeclaration> interface0 = cu.getInterfaceByName(name);
        if (interface0.isPresent()) {
            return interface0.get();
        }
        Optional<EnumDeclaration> enum0 = cu.getEnumByName(name);
        if (enum0.isPresent()) {
            return enum0.get();
        }
        Optional<AnnotationDeclaration> annotation = cu.getAnnotationDeclarationByName(name);
        if (annotation.isPresent()) {
            return annotation.get();
        }
        throw new RuntimeException("Could not find class, interface, enum or annotation '" + name + "' in compilation unit.");
    }

    /**
     * Returns the element of a resolved type. Recursively strips all array
     * variables. For example:
     *     Object[][] => Object
     *
     * @param resolvedType a type
     * @return the element type
     */
    public static ResolvedType getElementType(ResolvedType resolvedType) {
        while (resolvedType.isArray()) {
            resolvedType = resolvedType.asArrayType().getComponentType();
        }
        return resolvedType;
    }

    /**
     * Gets all type parameters of a given method as declared in source code.
     * @param methodUsage the method in the form of {@link MethodUsage}
     * @return a list of string representing the type of all the parameters of the method
     */
    private static List<String> getTypeParameters(MethodUsage methodUsage) {
        return methodUsage.getDeclaration().getTypeParameters()
                .stream()
                .map(ResolvedTypeParameterDeclaration::getName)
                .toList();
    }

    /**
     * Navigate the directories from the root of a given repository and find all the paths that matches
     * the common patterns identifying Java projects (both to source and test files) within it.
     * @param rootPath the path to a Java repository (containing a single or multiple Java projects) or to a container of
     *                 Java libraries (jars or source code)
     * @return a list of paths to the source code and the test code of the Java projects found within
     * the given repository.
     */
    private static List<Path> findPotentialProjectsRoots(Path rootPath) {
        // Define the list of potential projects roots and initialize it with an empty list
        List<Path> potentialProjectsRoots = new ArrayList<>();

        if (rootPath.endsWith(NamingConvention.DECOMPILED_LIB_FOLDER.getConventionName())) {
            return FilesUtils.listDirectories(rootPath);
        }

        // Walk the repository root path and find potential projects roots
        // Filter the paths contain the common patterns identifying Java projects
        // Collect the paths to the source code and the test code of the Java projects found
        try {
            potentialProjectsRoots = Files.walk(rootPath)
                    .filter(Files::isDirectory)
                    .map(path -> {
                        if(Files.exists(path.resolve("src/main/java"))) {
                            return path.resolve("src/main/java");
                        } else if (Files.exists(path.resolve("src/java/main"))) {
                            return path.resolve("src/java/main");
                        } else if (Files.exists(path.resolve("src/java"))) {
                            return path.resolve("src/java");
                        } else if (Files.exists(path.resolve("src/source"))) {
                            return path.resolve("src/source");
                        } else if (Files.exists(path.resolve("src"))) {
                            return path.resolve("src");
                        } else if (Files.exists(path.resolve("code"))) {
                            return path.resolve("code");
                        } else if (Files.exists(path.resolve("source"))) {
                            return path.resolve("source");
                        } else if (Files.exists(path.resolve("java"))) {
                            return path.resolve("java");
                        } else if (Files.exists(path.resolve("src/test/java"))) {
                            return path.resolve("src/test/java");
                        } else if (Files.exists(path.resolve("src/tests/java"))) {
                            return path.resolve("src/tests/java");
                        } else if (Files.exists(path.resolve("src/java/test"))) {
                            return path.resolve("src/java/test");
                        } else if (Files.exists(path.resolve("src/test"))) {
                            return path.resolve("src/test");
                        } else if (Files.exists(path.resolve("test/java"))) {
                            return path.resolve("test/java");
                        } else if (Files.exists(path.resolve("test"))) {
                            return path.resolve("test");
                        } else if (Files.exists(path.resolve("unittest"))) {
                            return path.resolve("unittest");
                        }
                        return null;
                    })
                    .filter(path -> path != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error while trying to find potential projects roots in repository path {}: ", rootPath, e);
        }
        potentialProjectsRoots.add(rootPath);
        // Return the list of potential projects roots found within the repository
        return potentialProjectsRoots;
    }

    /**
     * Get modifiers as a list of strings
     *
     * @param modifiers The original list of modifiers
     * @return A list of strings representing the modifiers
     * */
    public static List<String> getModifiersAsStrings(List<Modifier> modifiers) {
        return modifiers.stream().map(m -> m.toString().trim().toLowerCase()).collect(Collectors.toList());
    }

    /**
     * Get annotations as a list of strings
     *
     * @param annotations The original list of annotations
     * @return A list of strings representing the annotations
     * */
    public static List<String> getAnnotationsAsStrings(List<AnnotationExpr> annotations) {
        return annotations.stream().map(a -> a.toString().trim()).collect(Collectors.toList());
    }

    /**
     * Process the callable expression return the corresponding {@link CallableDeclaration}
     * @param typeDeclaration the type declaration corresponding to the class where to search the definition of the method
     *                        or constructor (i.e. the callable expression) defined
     * @param callExpr the callable expression to resolve
     * @param exprType the type of the callable expression (i.e. method or constructor)
     * @return the corresponding {@link CallableDeclaration} of the callable expression, if found. Otherwise, return null.
     * @throws MultipleCandidatesException if multiple candidates are found for the callable expression and the method
     *                                     or constructor cannot be resolved uniquely
     * @throws CandidateCallableDeclarationNotFoundException if the method or constructor corresponding to the callable
     *                                                       expression cannot be found and no candidates are available
     */
    public static CallableDeclaration resolveCallExpression(TypeDeclaration typeDeclaration, Expression callExpr, CallableExprType exprType) throws MultipleCandidatesException, CandidateCallableDeclarationNotFoundException {
        // Define the focal method. Initially null.
        CallableDeclaration focalMethod = null;
        try {
            if (exprType == CallableExprType.METHOD) {
                // Try to resolve the method call expression to a method declaration
                MethodCallExpr methodCallExpr = callExpr.asMethodCallExpr();
                Node resolvedCallExpr = methodCallExpr.resolve().toAst().orElseThrow(() -> new UnsolvedSymbolException(methodCallExpr.getNameAsString()));
                if (resolvedCallExpr instanceof MethodDeclaration) {
                    focalMethod = (MethodDeclaration) resolvedCallExpr;
                    // Retrieve the method within the type declaration to avoid to lose the symbol solver functionality
                    for (MethodDeclaration m : (List<MethodDeclaration>) typeDeclaration.getMethods()) {
                        if (m.getDeclarationAsString(true, true, true).equals(focalMethod.getDeclarationAsString(true, true, true))) {
                            return m;
                        }
                    }
                    //logger.error("Resolved method {} not found in type declaration {}", focalMethod.getNameAsString(), typeDeclaration.getNameAsString());
                    return focalMethod;
                }
            } else if (exprType == CallableExprType.CONSTRUCTOR) {
                // Try to resolve the constructor call expression to a constructor declaration
                ObjectCreationExpr constructorCallExpr = callExpr.asObjectCreationExpr();
                focalMethod = (ConstructorDeclaration) constructorCallExpr.resolve().toAst().orElseThrow(() -> new UnsolvedSymbolException(constructorCallExpr.getTypeAsString()));
                // Retrieve the method within the type declaration to avoid to lose the symbol solver functionality
                for (ConstructorDeclaration c : (List<ConstructorDeclaration>) typeDeclaration.getConstructors()) {
                    if (c.getDeclarationAsString(true, true, true).equals(focalMethod.getDeclarationAsString(true, true, true))) {
                        return c;
                    }
                }
                //logger.error("Resolved constructor {} not found in type declaration {}", focalMethod.getNameAsString(), typeDeclaration.getNameAsString());
                return focalMethod;
            }
        } catch (UnsolvedSymbolException e) {
            // The method call expression could not be resolved to a method declaration by
            // Java Symbol Solver
            focalMethod = null;
        }
        // If the method call expression could not be resolved to a method declaration by Java
        // Symbol Solver, try to find the method declaration by comparing the method call expression
        // with the list of methods in the class file (analyzing the signature and the number and types
        // of the parameters).
        if (focalMethod == null) {
            return searchCandidateCallableDeclaration(typeDeclaration, callExpr, exprType);
        }
        return focalMethod;
    }

    /**
     * Search the method declaration corresponding to a given callable expression, by comparing the method call expression
     * with the list of methods in the class file where the callable declaration is supposed to be defined (analyzing the
     * signature and the number and types of the parameters).
     * The method is useful when is not possible to resolve the method call expression to a method declaration by Java Symbol
     * Solver and it is necessary to apply heuristics to find the callable declaration.
     * @param typeDeclaration the type declaration corresponding to the class where to search the definition of the method
     *                        or constructor (i.e. the callable expression) defined
     * @param lastCallExpr the callable expression to resolve
     * @param exprType the type of the callable expression (i.e. method or constructor)
     * @return the corresponding {@link CallableDeclaration} of the callable expression, if found. Otherwise, return null.
     * @throws MultipleCandidatesException if multiple candidates are found for the callable expression and the method
     *                                    or constructor cannot be resolved uniquely
     * @throws CandidateCallableDeclarationNotFoundException if the method or constructor corresponding to the callable
     *                                                       expression cannot be found and no candidates are available
     */
    public static CallableDeclaration searchCandidateCallableDeclaration(TypeDeclaration typeDeclaration, Expression lastCallExpr, CallableExprType exprType) throws MultipleCandidatesException, CandidateCallableDeclarationNotFoundException {
        // Define the list of all the callable methods/constructors in the class file. Initially empty.
        List<? extends CallableDeclaration<?>> classCallableList = new ArrayList<>();
        // Define the name of the focal method. Initially null.
        String focalMethodName = null;
        // Define the list of arguments of the focal method. Initially empty.
        NodeList<Expression> focalMethodArgs = new NodeList<>();
        // Define the focal method. Initially null.
        CallableDeclaration focalMethod = null;
        // Define a list of candidate methods/constructors (methods/constructors with the same name and number of parameters).
        // Initially empty.
        List<CallableDeclaration> candidatesCallables = new ArrayList<>();

        if (exprType == CallableExprType.METHOD) {
            classCallableList = typeDeclaration.getMethods();
            focalMethodName = ((MethodCallExpr) lastCallExpr).getNameAsString();
            focalMethodArgs = ((MethodCallExpr) lastCallExpr).getArguments();
        } else if (exprType == CallableExprType.CONSTRUCTOR) {
            classCallableList = typeDeclaration.getConstructors();
            focalMethodName = ((ObjectCreationExpr) lastCallExpr).getTypeAsString();
            focalMethodArgs = ((ObjectCreationExpr) lastCallExpr).getArguments();
        }
        // Iterate over the list of methods/constructors in the class file
        for (CallableDeclaration callable : classCallableList) {
            // Check if the method/constructor name is the same
            if (callable.getNameAsString().equals(focalMethodName)) {
                // Check if the number of parameters is the same
                if (callable.getParameters().size() == focalMethodArgs.size()) {
                    // Add method/constructor to candidates if the previous conditions are satisfied
                    candidatesCallables.add(callable);
                } else if (callable.getParameters().size() > 0) {
                    // Check if the number of parameters is the same, but the method/constructor is a varargs
                    Parameter lastCandidateParam = (Parameter) callable.getParameters().getLast().get();
                    if (lastCandidateParam.isVarArgs() && callable.getParameters().size() <= focalMethodArgs.size()) {
                        candidatesCallables.add(callable);
                    }
                }
            }
        }
        // If no candidate methods are found, throw an exception (focal method not found)
        if (candidatesCallables.size() == 0) {
            return focalMethod;
        } else if (candidatesCallables.size() == 1) {
            // If only one candidate method/constructor is found, set the focal method to the candidate method/constructor
            focalMethod = candidatesCallables.get(0);
        } else {
            List<ResolvedType> focalMethodResolvedTypes = focalMethodArgs.stream().map(a -> a.calculateResolvedType()).collect(Collectors.toList());
            focalMethod = matchCallableDeclaration(focalMethodResolvedTypes, candidatesCallables);
        }
        return focalMethod;
    }


    /**
     * Search the resolved method declaration corresponding to a given callable expression, by comparing the method call expression
     * with the list of methods in the resolved class file where the callable declaration is supposed to be defined (analyzing the
     * signature and the number and types of the parameters).
     * The method is useful when is not possible to resolve the method call expression to a method declaration by Java Symbol
     * Solver, and it is necessary to apply heuristics to find the callable declaration.
     * @param resolvedReferenceType the resolved reference declaration corresponding to the class where to search the
     *                              definition of the method or constructor (i.e. the callable expression) defined
     * @param methodCallExpr the callable expression to resolve
     * @return the corresponding {@link ResolvedMethodDeclaration} of the callable expression, if found. Otherwise, return null.
     * @throws MultipleCandidatesException if multiple candidates are found for the callable expression and the method
     *                                    or constructor cannot be resolved uniquely
     * @throws CandidateCallableDeclarationNotFoundException if the method or constructor corresponding to the callable
     *                                                       expression cannot be found and no candidates are available
     */
    public static ResolvedMethodDeclaration searchCandidateResolvedMethodDeclaration(ResolvedReferenceType resolvedReferenceType, MethodCallExpr methodCallExpr) throws MultipleCandidatesException, CandidateCallableDeclarationNotFoundException {
        // Define the list of arguments of the focal method. Initially empty.
        NodeList<Expression> focalMethodArgs = new NodeList<>();
        // Define the focal method. Initially null.
        ResolvedMethodDeclaration focalMethod = null;
        // Define a list of candidate methods/constructors (methods/constructors with the same name and number of parameters).
        // Initially empty.
        List<ResolvedMethodDeclaration> candidates = new ArrayList<>();

        List<ResolvedMethodDeclaration> classMethodsList = resolvedReferenceType.getAllMethods();
        String focalMethodName = methodCallExpr.getNameAsString();
        focalMethodArgs = methodCallExpr.getArguments();
        // Iterate over the list of methods/constructors in the class file
        for (ResolvedMethodDeclaration m : classMethodsList) {
            // Check if the method/constructor name is the same
            if (m.getName().equals(focalMethodName)) {
                // Check if the number of parameters is the same
                if (m.getNumberOfParams() == focalMethodArgs.size()) {
                    // Add method/constructor to candidates if the previous conditions are satisfied
                    candidates.add(m);
                } else if (m.getNumberOfParams() > 0) {
                    // Check if the number of parameters is the same, but the method/constructor is a varargs
                    ResolvedParameterDeclaration lastCandidateParam = m.getParam(m.getNumberOfParams() - 1);
                    if (lastCandidateParam.isVariadic() && m.getNumberOfParams() <= focalMethodArgs.size()) {
                        candidates.add(m);
                    }
                }
            }
        }
        // If no candidate methods are found, throw an exception (focal method not found)
        if (candidates.size() == 0) {
            return focalMethod;
        } else if (candidates.size() == 1) {
            // If only one candidate method/constructor is found, set the focal method to the candidate method/constructor
            focalMethod = candidates.get(0);
        } else {
            List<ResolvedType> focalMethodResolvedTypes = focalMethodArgs.stream().map(a -> a.calculateResolvedType()).collect(Collectors.toList());
            focalMethod = matchResolvedMethodDeclaration(focalMethodResolvedTypes, candidates);
        }
        return focalMethod;
    }


    /**
     * Match the resolved method declaration with the most similar signature to the given method call expression. The method supports
     * the searchCandidateResolvedMethodDeclaration method in finding the most similar candidate method/constructor resolving the
     * method call expression, comparing the signature and the number and types of the parameters with the list of candidate
     * methods/constructors in the resolved class.
     *
     * @param focalMethodArgs the list of types of the arguments of the callable expression
     * @param candidates the list of candidate methods/constructors in the class file
     * @return the most similar candidate method/constructor to the method call expression in the class file
     * @throws CandidateCallableDeclarationNotFoundException if no candidate method/constructor fully match the signature
     *                                                       of the callable expression
     * @throws MultipleCandidatesException if multiple candidate methods/constructors are found, and it is impossible to
     *                                     discern the focal method
     */
    private static ResolvedMethodDeclaration matchResolvedMethodDeclaration(List<ResolvedType> focalMethodArgs, List<ResolvedMethodDeclaration> candidates) throws CandidateCallableDeclarationNotFoundException, MultipleCandidatesException {
        // Define the focal method.
        ResolvedMethodDeclaration focalMethod;
        // Initialize the number of parameters in common to -1 (no parameters in common)
        int bestParamsTypesInCommon = -1;
        // Initialize the boolean flag multipleCandidates to false (only one candidate method is
        // the most similar to the method call expression)
        boolean multipleCandidates = false;
        // Initialize the most similar candidate method to null
        ResolvedMethodDeclaration mostSimilar = null;
        // Iterate over the list of candidate methods/constructors
        for (ResolvedMethodDeclaration candidate : candidates) {
            // Initialize the number of parameters in common with the current candidate to 0
            int paramsTypesInCommon = 0;
            // Iterate over the list of parameters of the current candidate method/constructor
            for (int i = 0; i < focalMethodArgs.size(); i++) {
                // Get the i-th parameter of the current candidate method/constructor (or the last parameter if the
                // number of parameters of the candidate method/constructor is less than the number of parameters of
                // the method call expression because the last param of the candidate method/constructor is a varargs)
                ResolvedParameterDeclaration param = i < candidate.getNumberOfParams() ? candidate.getParam(i) : candidate.getLastParam();
                // Get the type of the i-th parameter of the focal method call in the test
                ResolvedType focalMethodArg = focalMethodArgs.get(i);
                String focalMethodTypeName = focalMethodArgs.get(i).describe();
                // Perform preliminary checks to avoid comparing incompatible types
                if (!param.getType().describe().equals("java.lang.Object") && !param.getType().isTypeVariable()) {
                    if (focalMethodArg.isArray() && !param.getType().isArray()) {
                        continue;
                    }
                    if (!focalMethodArg.isArray() && param.getType().isArray()) {
                        continue;
                    }
                    if (focalMethodArg.describe().startsWith("java.util.List") && !param.getType().describe().startsWith("List")) {
                        continue;
                    }
                    if (!focalMethodArg.describe().startsWith("java.util.List") && param.getType().describe().startsWith("List")) {
                        continue;
                    }
                    if (focalMethodArg.isArray() && (param.getType().isArray())) {
                        if (focalMethodArg.asArrayType().arrayLevel() != param.getType().asArrayType().arrayLevel()) {
                            continue;
                        }
                    }
                    // Get the type of the i-th parameter of the current candidate method, as a string
                    String paramTypeName = param.getType().describe();
                    boolean matchFound = false;
                    // Compare the parameters type names
                    if (focalMethodTypeName.contains(paramTypeName.replaceAll("<.*?>|List|\\[\\]|<|>", ""))) {
                        matchFound = true;
                    } else {
                        if (param.getType().isReferenceType() && focalMethodArg.isReferenceType()) {
                            List<String> paramAncestors = param.getType().asReferenceType().getAllAncestors().stream().map(ResolvedReferenceType::describe).collect(Collectors.toList());
                            for (String ancestor : paramAncestors) {
                                if (focalMethodTypeName.contains(ancestor.replaceAll("<.*?>|List|\\[\\]|<|>", ""))) {
                                    paramTypeName = ancestor;
                                    matchFound = true;
                                    break;
                                }
                            }
                            if (!matchFound) {
                                List<String> focalAncestors = focalMethodArg.asReferenceType().getAllAncestors().stream().map(ResolvedReferenceType::describe).collect(Collectors.toList());
                                for (String ancestor : focalAncestors) {
                                    if (paramTypeName.contains(ancestor.replaceAll("<.*?>|List|\\[\\]|<|>", ""))) {
                                        focalMethodTypeName = ancestor;
                                        matchFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (matchFound) {
                        // Pattern to capture content within < and >
                        Pattern pattern = Pattern.compile("<(.*?)>");
                        Matcher matcherFocalMethod = pattern.matcher(focalMethodTypeName);
                        if (matcherFocalMethod.find()) {
                            String genericTypeFocalMethod = matcherFocalMethod.group(1); // Save the captured content
                            Matcher matcherParam = pattern.matcher(paramTypeName);

                            if (genericTypeFocalMethod.equals("?") || genericTypeFocalMethod.length() == 1) {
                                paramsTypesInCommon += 1;
                            } else {
                                if (matcherParam.find()) {
                                    String genericTypeParam = matcherParam.group(1);
                                    if (genericTypeParam.equals("?") || genericTypeParam.length() == 1 || genericTypeFocalMethod.equals(genericTypeParam)) {
                                        paramsTypesInCommon += 1;
                                    }
                                } else {
                                    paramsTypesInCommon += 1;
                                }
                            }
                        } else {
                            // If the type names corresponds, increment the number of parameters in common
                            paramsTypesInCommon += 1;
                        }
                    }
                } else {
                    // Any type inherits from Object. Increment the number of parameters in common
                    paramsTypesInCommon += 1;
                }
            }
            // If the number of parameters in common with the current candidate method/constructor is
            // greater or equal to the best number of parameters in common found so far, update the best
            // number of parameters in common and assign the current candidate method/constructor to the
            // most similar candidate method/constructor
            if (paramsTypesInCommon >= bestParamsTypesInCommon) {
                if (paramsTypesInCommon == bestParamsTypesInCommon) {
                    if (mostSimilar.isAbstract() && !candidate.isAbstract()) {
                        mostSimilar = candidate;
                    } else if (candidate.getQualifiedName().startsWith("java.util.List") && mostSimilar.getQualifiedName().startsWith("java.util.Collection")) {
                        mostSimilar = candidate;
                    } else if (mostSimilar.getQualifiedName().startsWith("java.util.List") && candidate.getQualifiedName().startsWith("java.util.Collection")) {
                        continue;
                    } else if (mostSimilar.isAbstract() && candidate.isAbstract()) {
                        multipleCandidates = true;
                    }
                } else {
                    bestParamsTypesInCommon = paramsTypesInCommon;
                    multipleCandidates = false;
                    mostSimilar = candidate;
                }
            }
        }
        // If only one candidate method/constructor is the most similar to the focal method call expression, set
        // the focal method to the most similar candidate method
        if (!(multipleCandidates || mostSimilar == null)) {
            focalMethod = mostSimilar;
            if (bestParamsTypesInCommon < focalMethodArgs.size()) {
                // If the number of parameters in common is less than the number of parameters of
                // the method call expression, log a warning
                throw new CandidateCallableDeclarationNotFoundException("[WARNING] - No candidate method fully match the signature of the callable expression. No focal method found.");
            }
        } else {
            if (multipleCandidates) {
                // Multiple candidates, impossible to discern
                throw new MultipleCandidatesException(String.format("[WARNING] - Multiple candidate methods/constructors found. Impossible to discern the focal method."));
            } else {
                // No candidate available
                throw new CandidateCallableDeclarationNotFoundException("[WARNING] - No candidate method fully match the signature of the callable expression. No focal method found.");
            }
        }
        return focalMethod;
    }


    /**
     * Match the callable declaration with the most similar signature to the given method call expression. The method supports
     * the searchCandidateCallableDeclaration method in finding the most similar candidate method/constructor resolving the
     * method call expression, comparing the signature and the number and types of the parameters with the list of candidate
     * methods/constructors in the class file.
     *
     * @param focalMethodArgs the list of types of the arguments of the callable expression
     * @param candidatesCallables the list of candidate methods/constructors in the class file
     * @return the most similar candidate method/constructor to the method call expression in the class file
     * @throws CandidateCallableDeclarationNotFoundException if no candidate method/constructor fully match the signature
     *                                                       of the callable expression
     * @throws MultipleCandidatesException if multiple candidate methods/constructors are found, and it is impossible to
     *                                     discern the focal method
     */
    private static CallableDeclaration matchCallableDeclaration(List<ResolvedType> focalMethodArgs, List<CallableDeclaration> candidatesCallables) throws CandidateCallableDeclarationNotFoundException, MultipleCandidatesException {
        // Define the focal method.
        CallableDeclaration focalMethod;
        // Initialize the number of parameters in common to -1 (no parameters in common)
        int bestParamsTypesInCommon = -1;
        // Initialize the boolean flag multipleCandidates to false (only one candidate method is
        // the most similar to the method call expression)
        boolean multipleCandidates = false;
        // Initialize the most similar candidate method to null
        CallableDeclaration mostSimilar = null;
        // Iterate over the list of candidate methods/constructors
        for (CallableDeclaration callable : candidatesCallables) {
            // Get the list of parameters of the candidate method/constructor
            NodeList<com.github.javaparser.ast.body.Parameter> paramsList = callable.getParameters();
            // Initialize the number of parameters in common with the current candidate to 0
            int paramsTypesInCommon = 0;
            // Iterate over the list of parameters of the current candidate method/constructor
            for (int i = 0; i < focalMethodArgs.size(); i++) {
                // Get the i-th parameter of the current candidate method/constructor or the last parameter if the
                // number of parameters of the current candidate method/constructor is less than the number of parameters
                // of the focal method call expression (this happens when the last parameter is a varargs)
                com.github.javaparser.ast.body.Parameter param = i < paramsList.size() ? paramsList.get(i) : paramsList.getLast().get();
                // Get the type of the i-th parameter of the focal method call in the test
                ResolvedType focalMethodArg = focalMethodArgs.get(i);
                String focalMethodTypeName = focalMethodArgs.get(i).describe();
                if (!param.getType().resolve().describe().equals("java.lang.Object") || !param.getType().resolve().isTypeVariable()) {
                    // Perform preliminary checks to avoid comparing incompatible types
                    if (focalMethodArg.isArray() && !(param.getType().isArrayType() || param.isVarArgs())) {
                        continue;
                    }
                    if (!focalMethodArg.isArray() && (param.getType().isArrayType())) {
                        continue;
                    }
                    if (focalMethodArg.describe().startsWith("java.util.List") && !param.getTypeAsString().startsWith("List")) {
                        continue;
                    }
                    if (!focalMethodArg.describe().startsWith("java.util.List") && param.getTypeAsString().startsWith("List")) {
                        continue;
                    }
                    if (focalMethodArg.isArray() && (param.getType().isArrayType())) {
                        //if (param.isVarArgs() && (focalMethodArg.asArrayType().arrayLevel() > 1)) {
                        //    continue;
                        //} else
                        if (!param.isVarArgs() && (focalMethodArg.asArrayType().arrayLevel() != param.getType().asArrayType().getArrayLevel())) {
                            continue;
                        }
                    }
                    // Get the type of the i-th parameter of the current candidate method, as a string
                    String paramTypeName = param.getTypeAsString();
                    // Compare the parameters type names
                    if (focalMethodTypeName.contains(paramTypeName.replaceAll("<.*?>|List|\\[\\]|<|>", ""))) {
                        // Pattern to capture content within < and >
                        Pattern pattern = Pattern.compile("<(.*?)>");
                        Matcher matcherFocalMethod = pattern.matcher(focalMethodTypeName);
                        if (matcherFocalMethod.find()) {
                            String genericTypeFocalMethod = matcherFocalMethod.group(1); // Save the captured content
                            Matcher matcherParam = pattern.matcher(paramTypeName);

                            if (genericTypeFocalMethod.equals("?") || genericTypeFocalMethod.length() == 1) {
                                paramsTypesInCommon += 1;
                            } else {
                                if (matcherParam.find()) {
                                    String genericTypeParam = matcherParam.group(1);
                                    if (genericTypeParam.equals("?") || genericTypeParam.length() == 1 || genericTypeFocalMethod.equals(genericTypeParam)) {
                                        paramsTypesInCommon += 1;
                                    }
                                } else {
                                    paramsTypesInCommon += 1;
                                }
                            }
                        } else {
                            // If the type names corresponds, increment the number of parameters in common
                            paramsTypesInCommon += 1;
                        }
                    }
                } else {
                    // Any type inherits from Object. Increment the number of parameters in common
                    paramsTypesInCommon += 1;
                }
            }
            // If the number of parameters in common with the current candidate method/constructor is
            // greater or equal to the best number of parameters in common found so far, update the best
            // number of parameters in common and assign the current candidate method/constructor to the
            // most similar candidate method/constructor
            if (paramsTypesInCommon >= bestParamsTypesInCommon) {
                if (paramsTypesInCommon == bestParamsTypesInCommon) {
                    if ((mostSimilar.isAbstract() && !callable.isAbstract()) || (mostSimilar.isPrivate() && callable.isPublic())) {
                        mostSimilar = callable;
                    } else if (mostSimilar.isAbstract() && callable.isAbstract()) {
                        multipleCandidates = true;
                    }
                } else {
                    bestParamsTypesInCommon = paramsTypesInCommon;
                    multipleCandidates = false;
                    mostSimilar = callable;
                }
            }
        }
        // If only one candidate method/constructor is the most similar to the focal method call expression, set
        // the focal method to the most similar candidate method
        if (!(multipleCandidates || mostSimilar == null)) {
            focalMethod = mostSimilar;
            if (bestParamsTypesInCommon < focalMethodArgs.size()) {
                // If the number of parameters in common is less than the number of parameters of
                // the method call expression, log a warning
                throw new CandidateCallableDeclarationNotFoundException("[WARNING] - No candidate method fully match the signature of the callable expression. No focal method found.");
            }
        } else {
            if (multipleCandidates) {
                // Multiple candidates, impossible to discern
                throw new MultipleCandidatesException(String.format("[WARNING] - Multiple candidate methods/constructors found. Impossible to discern the focal method."));
            } else {
                // No candidate available
                throw new CandidateCallableDeclarationNotFoundException("[WARNING] - No candidate method fully match the signature of the callable expression. No focal method found.");
            }
        }
        return focalMethod;
    }

    /**
     * Search the {@link MethodUsage} corresponding to a given method call expression, by comparing the method call expression
     * with the list of methods in the class file where the method call expression is supposed to be defined (analyzing the
     * signature and the number and types of the parameters).
     * The method is useful when is not possible to resolve the method call expression to a method declaration by Java Symbol
     * Solver, and it is necessary to apply heuristics to find the method in the form of {@link MethodUsage}.
     * @param typeDeclaration the type declaration corresponding to the class where to search the definition of the method
     *                        defined in the method call expression
     * @param expr the method call expression to resolve
     * @return the corresponding {@link MethodUsage} of the method call expression, if found. Otherwise, return null.
     * @throws CandidateCallableMethodUsageNotFoundException if the method corresponding to the method call expression
     *                                                       cannot be found and no candidates are available
     * @throws MultipleCandidatesException if multiple candidates are found for the method call expression and the method
     *                                     cannot be resolved uniquely
     */
    public static MethodUsage searchCandidateMethodUsage(TypeDeclaration typeDeclaration, Expression expr) throws CandidateCallableMethodUsageNotFoundException, MultipleCandidatesException {
        try {
            return iterateOverCandidatesMethodUsage(typeDeclaration.resolve().getAllMethods(), expr.asMethodCallExpr());
        } catch (CandidateCallableMethodUsageNotFoundException | UnsolvedSymbolException | IllegalStateException e) {
            return null;
        }
    }

    private static MethodUsage iterateOverCandidatesMethodUsage(Set<MethodUsage> methods, MethodCallExpr methodCallExpr) throws CandidateCallableMethodUsageNotFoundException, MultipleCandidatesException {
        List<MethodUsage> methodNotConverted = new ArrayList<>();
        List<MethodDeclaration> methodConverted = new ArrayList<>();
        // Define the focal method. Initially null.
        MethodUsage focalMethod = null;
        // Define a list of candidate methods/constructors (methods/constructors with the same name and number of parameters).
        // Initially empty.
        List<MethodUsage> candidatesMethodUsage = new ArrayList<>();
        // Get the name of the focal method
        String focalMethodName = methodCallExpr.getNameAsString();
        // Get the list of arguments of the focal method
        NodeList<Expression> focalMethodArgs = methodCallExpr.getArguments();

        for(MethodUsage m : methods) {
            try {
                methodConverted.add((MethodDeclaration) m.getDeclaration().toAst().get());
            } catch(Exception e) {
                methodNotConverted.add(m);
            }
        }

        // Iterate over the list of methods/constructors in the class file
        for (MethodUsage methodUsage : methodNotConverted) {
            // Check if the method/constructor name is the same
            if (methodUsage.getName().equals(focalMethodName)) {
                // Check if the number of parameters is the same
                if (methodUsage.getParamTypes().size() == focalMethodArgs.size()) {
                    // Add method/constructor to candidates if the previous conditions are satisfied
                    candidatesMethodUsage.add(methodUsage);
                } else if (methodUsage.getParamTypes().size() > 0) {
                    // Check if the number of parameters is the same, but the method/constructor is a varargs
                    ResolvedType lastCandidateParam = methodUsage.getParamTypes().get(methodUsage.getParamTypes().size() - 1);
                    if (lastCandidateParam.isArray() && methodUsage.getParamTypes().size() <= focalMethodArgs.size()) {
                        candidatesMethodUsage.add(methodUsage);
                    }
                }
            }
        }

        if (candidatesMethodUsage.size() == 0) {
            throw new CandidateCallableMethodUsageNotFoundException("[WARNING] - No candidate method usage found in the original class, analyzing both the name and the number of parameters. Focal method not found. ");
        } else if (candidatesMethodUsage.size() == 1) {
            // If only one candidate method/constructor is found, set the focal method to the candidate method/constructor
            focalMethod = candidatesMethodUsage.get(0);
        } else {
            // If multiple candidate methods are found, try to discern the focal method by analyzing
            // the types of the parameters of the focal method call expression and the parameters of the
            // candidate methods/constructors.
            // Initialize the number of parameters in common to -1 (no parameters in common)
            int bestParamsTypesInCommon = -1;
            // Initialize the boolean flag multipleCandidates to false (only one candidate method/constructor is
            // the most similar to the method call expression)
            boolean multipleCandidates = false;
            // Initialize the most similar candidate method/constructor to null
            MethodUsage mostSimilar = null;
            // Iterate over the list of candidate methods/constructors
            for (MethodUsage candidate : candidatesMethodUsage) {
                // Get the list of parameters of the candidate method/constructor
                List<ResolvedType> paramsTypeList = candidate.getParamTypes();
                // Initialize the number of parameters in common with the current candidate to 0
                int paramsTypesInCommon = 0;
                // Iterate over the list of parameters of the current candidate method/constructor
                for (int i = 0; i < focalMethodArgs.size(); i++) {
                    // Get the i-th parameter of the current candidate method/constructor or the last parameter if the
                    // number of parameters of the current candidate method/constructor is less than the number of parameters
                    // of the focal method call expression (this happens when the last parameter is a varargs)
                    ResolvedType paramType = i < paramsTypeList.size() ? paramsTypeList.get(i) : paramsTypeList.get(paramsTypeList.size() - 1);
                    // Get the type of the i-th parameter of the focal method call in the test
                    // prefix, as a string
                    String focalMethodTypeName = focalMethodArgs.get(i).calculateResolvedType().describe();
                    // Get the type of the i-th parameter of the current candidate method, as a string
                    String paramTypeName = paramType.describe();
                    // Compare the parameters type names
                    if (focalMethodTypeName.endsWith(paramTypeName) || paramTypeName.endsWith("Object") || paramType.isTypeVariable()) {
                        // If the type names corresponds or the candidate param type is Object,
                        // increment the number of parameters in common (any type inherits from Object)
                        paramsTypesInCommon += 1;
                    }
                }
                // If the number of parameters in common with the current candidate method/constructor is
                // greater or equal to the best number of parameters in common found so far, update the best
                // number of parameters in common and assign the current candidate method/constructor to the
                // most similar candidate method/constructor
                if (paramsTypesInCommon >= bestParamsTypesInCommon) {
                    if (paramsTypesInCommon == bestParamsTypesInCommon) {
                        if (mostSimilar.getDeclaration().isAbstract() && !candidate.getDeclaration().isAbstract()) {
                            mostSimilar = candidate;
                        } else if (mostSimilar.getDeclaration().isAbstract() && candidate.getDeclaration().isAbstract()) {
                            multipleCandidates = true;
                        }
                    } else {
                        bestParamsTypesInCommon = paramsTypesInCommon;
                        multipleCandidates = false;
                        mostSimilar = candidate;
                    }
                }
            }
            // If only one candidate method/constructor is the most similar to the focal method call expression, set
            // the focal method to the most similar candidate method
            if (!(multipleCandidates || mostSimilar == null)) {
                focalMethod = mostSimilar;
                if (bestParamsTypesInCommon < focalMethodArgs.size()) {
                    // If the number of parameters in common is less than the number of parameters of
                    // the method call expression, log a warning
                    throw new CandidateCallableMethodUsageNotFoundException("[WARNING] - No candidate method fully match the signature of the callable expression. No focal method found.");
                }
            } else {
                // Multiple candidates, impossible to discern
                throw new MultipleCandidatesException("[WARNING] - Multiple candidate methods/constructors found. Impossible to discern the focal method.");
            }
        }
        return focalMethod;
    }


    /**
     * Search the {@link MethodUsage} corresponding to a given method call expression, by comparing the method call expression
     * with the list of methods in the {@link ResolvedReferenceType} where the method call expression is supposed to be defined (analyzing the
     * signature and the number and types of the parameters).
     * The method is useful when is not possible to resolve the method call expression to a method declaration by Java Symbol
     * Solver, and it is necessary to apply heuristics to find the method in the form of {@link MethodUsage}.
     *
     * @param resolvedReferenceType the resolved reference type corresponding to the class of the external library where
     *                              to search the definition of the method defined in the method call expression
     * @param expr the method call expression to resolve
     * @return the corresponding {@link MethodUsage} of the method call expression, if found. Otherwise, return null.
     * @throws CandidateCallableMethodUsageNotFoundException if the method corresponding to the method call expression
     *                                                       cannot be found and no candidates are available
     * @throws MultipleCandidatesException if multiple candidates are found for the method call expression and the method
     *                                     cannot be resolved uniquely
     */
    public static MethodUsage searchCandidateMethodUsage(ResolvedReferenceType resolvedReferenceType, Expression expr) throws CandidateCallableMethodUsageNotFoundException, MultipleCandidatesException {
        return iterateOverCandidatesMethodUsage(resolvedReferenceType.getDeclaredMethods(), expr.asMethodCallExpr());
    }

    /**
     * Match the method declaration with the most similar signature to the given method in the form of {@link MethodUsage}.
     * @param referenceMethodUsage the method usage to resolve
     * @param methodDeclarationList the list of methods in the reference class where to search the corresponding method declaration
     * @return the corresponding {@link MethodDeclaration} of the method usage, if found.
     * @throws CandidateCallableDeclarationNotFoundException if the method corresponding to the method usage
     *                                                       cannot be found and no candidates are available
     * @throws MultipleCandidatesException if multiple candidates are found for the method usage and the method
     *                                     cannot be resolved uniquely
     */
    public static CallableDeclaration matchMethodUsageWithMethodDeclaration(MethodUsage referenceMethodUsage, List<MethodDeclaration> methodDeclarationList) throws  CandidateCallableDeclarationNotFoundException, MultipleCandidatesException {
        String referenceMethodUsageName = referenceMethodUsage.getName();
        List<ResolvedType> referenceMehodUsageArgs = referenceMethodUsage.getParamTypes();
        // Define the focal method. Initially null.
        CallableDeclaration focalMethod = null;
        // Define a list of candidate methods (methods with the same name and number of parameters).
        // Initially empty.
        List<CallableDeclaration> candidatesMethods = new ArrayList<>();
        // Iterate over the list of methods/constructors in the class file
        for (MethodDeclaration method : methodDeclarationList) {
            // Check if the method/constructor name is the same
            if (method.getNameAsString().equals(referenceMethodUsageName)) {
                // Check if the number of parameters is the same
                if (method.getParameters().size() == referenceMehodUsageArgs.size()) {
                    // Add method/constructor to candidates if the previous conditions are satisfied
                    candidatesMethods.add(method);
                }
            }
        }
        // If no candidate methods are found, throw an exception (focal method not found)
        if (candidatesMethods.size() == 0) {
            return focalMethod;
        } else if (candidatesMethods.size() == 1) {
            // If only one candidate method/constructor is found, set the focal method to the candidate method/constructor
            focalMethod = candidatesMethods.get(0);
        } else {
            focalMethod = matchCallableDeclaration(referenceMethodUsage.getParamTypes(), candidatesMethods);
        }
        return focalMethod;
    }

    /**
     * Parse the expression and return a list of all the occurrences of the method calls and object creations defined in the expression.
     * @param expr the expression to parse
     * @param removeAssertion filter out potential expressions referring to assertions
     * @return a list of pairs of expressions and the type of the expression (method or constructor)
     * @throws UnrecognizedExprException if the expression cannot be parsed
     */
    public static List<Pair<Expression,CallableExprType>> parseExpression(Expression expr, boolean removeAssertion) throws UnrecognizedExprException {
        List<Pair<Expression,CallableExprType>> methodCallsAndObjectCreationsList = new ArrayList<>();

        methodCallsAndObjectCreationsList.addAll(expr.findAll(MethodCallExpr.class).stream().map(e -> new Pair<>((Expression) e, CallableExprType.METHOD)).toList());
        if (removeAssertion) {
            methodCallsAndObjectCreationsList = methodCallsAndObjectCreationsList.stream().filter(p -> !JUnitAssertionType.isJUnitAssertion(p.getValue0().asMethodCallExpr().getNameAsString())).collect(Collectors.toList());
        }
        methodCallsAndObjectCreationsList.addAll(expr.findAll(ObjectCreationExpr.class).stream().map(e -> new Pair<>((Expression) e, CallableExprType.CONSTRUCTOR)).toList());
        return methodCallsAndObjectCreationsList;
    }

    /**
     * Retrieve all the classes extended by the compilation unit passed to the method.
     *
     * @param cu The compilation unit to process
     * @return the list of classes extended by the class represented by the compilation unit.
     */
    private static List<ClassOrInterfaceDeclaration> getExtendedClassesFromCompilationUnit(CompilationUnit cu) {
        List<ClassOrInterfaceDeclaration> extendedClasses = new ArrayList<>();

        // Get the primary type (the main class)
        Optional<ClassOrInterfaceDeclaration> primaryType = cu.getPrimaryType()
                .filter(ClassOrInterfaceDeclaration.class::isInstance)
                .map(ClassOrInterfaceDeclaration.class::cast);

        // Check if primary type is present and it extends any other classes
        if (primaryType.isPresent()) {
            ClassOrInterfaceDeclaration classDeclaration = primaryType.get();
            if (classDeclaration.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDeclaration.getExtendedTypes()) {
                    // TODO: Check the extended classes from the imports
                }
            }
        }
        return extendedClasses;
    }

    /**
     * Retrieve the last statement in a method declaration.
     *
     * @param callableDeclaration the method declaration to process
     * @return the last statement in the method declaration
     */
    public static Statement getLastStatementInMethodDeclaration(MethodDeclaration callableDeclaration) {
        List<Statement> statements = callableDeclaration.getBody().get().findAll(Statement.class);
        return statements.get(statements.size() - 1);
    }

    /**
     * Visit all the statements within a statement (it could be a block of statements or an expression statement, for example)
     * to find the last occurrence of a given statement type. The method returns the statement itself, if it is the last
     * occurrence of the given type
     *
     * @param stmt the statement to visit
     * @param type the type of statement to find
     * @return the last occurrence of the statement type. If the statement is the last occurrence of that type, returns
     * itself. If the statement is not found, return an empty optional.
     */
    public static Optional<Statement> getLastStatementTypeOccurrence(Statement stmt, Class type) {
        Optional<Statement> lastOccurrence = StmtVisitorHelper.getLastStatementOccurrence(stmt, type);
        return lastOccurrence;
    }

    /**
     * Retrieve the last assertion, assuming it is in the last statement of a method declaration.
     *
     * @param callableDeclaration the method declaration to process
     * @return the last assertion in the last statement of the method declaration (if any), otherwise return an empty optional
     */
    public static Optional<Statement> getLastAssertionInMethodDeclaration(MethodDeclaration callableDeclaration) throws IllegalStateException {
        // TODO: Check if assert stmt and expression stmt inteleave in the same nested block
        Statement lastStmt = getLastStatementInMethodDeclaration(callableDeclaration);
        if (lastStmt.isAssertStmt()) {
            return Optional.of(lastStmt);
        }
        Optional<Statement> exprStmt = getLastStatementTypeOccurrence(lastStmt,ExpressionStmt.class);
        if (exprStmt.isPresent()) {
            // Get the expression from the statement
            Expression currentExpr = exprStmt.get().asExpressionStmt().getExpression();
            // Check if the expression is a variable declaration expression and extract the initializer, if present
            if (currentExpr.isVariableDeclarationExpr()) {
                Optional<Expression> initializer = currentExpr.asVariableDeclarationExpr().getVariable(0).getInitializer();
                if (initializer.isPresent()) {
                    currentExpr = initializer.get();
                }
            }
            // Check if the expression is an assertion
            if (currentExpr.isMethodCallExpr() && JUnitAssertionType.isJUnitAssertion(currentExpr.asMethodCallExpr().getNameAsString())) {
                return exprStmt;
            } else {
                Optional<Node> parentNode = exprStmt.get().getParentNode();
                while (parentNode.isPresent() && !parentNode.get().equals(callableDeclaration)) {
                    if (parentNode.get() instanceof MethodCallExpr) {
                        MethodCallExpr methodCallExpr = (MethodCallExpr) parentNode.get();
                        if (JUnitAssertionType.isJUnitAssertion(methodCallExpr.getNameAsString())) {
                            while (parentNode.isPresent() && !(parentNode.get() instanceof ExpressionStmt)) {
                                parentNode = parentNode.get().getParentNode();
                            }
                            if (parentNode.isPresent()) {
                                return Optional.of((Statement) parentNode.get());
                            }
                            return Optional.empty();
                        }
                    }
                    parentNode = parentNode.get().getParentNode();
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieve the type declaration from the fully qualified name of the class and the root path of the repository where
     * the class should be located (source code or decompiled library), or an empty optional if the class cannot be found.
     *
     * @param fqn the fully qualified name of the class to retrieve
     * @param repoSourcePath the root path of the repository where the class should be located
     * @return the type declaration of the class corresponding to the fully qualified name or an empty optional if the class
     *        cannot be found
     */
    public static Optional<TypeDeclaration> retrieveTypeDeclarationFromFullyQualifiedName(String fqn, Path repoSourcePath) {
        Path fqnPath = FilesUtils.getFQNPath(fqn);
        Path referencePath = repoSourcePath;
        List<Path> candidatePrefixPaths = new ArrayList<>();
        candidatePrefixPaths.add(repoSourcePath);
        boolean hasLibFolder = FilesUtils.hasChildDirectory(referencePath, NamingConvention.LIB_FOLDER.getConventionName());
        while(!hasLibFolder && !(referencePath.getParent() == null)) {
            referencePath = referencePath.getParent();
            hasLibFolder = FilesUtils.hasChildDirectory(referencePath, NamingConvention.LIB_FOLDER.getConventionName());
        }
        if (hasLibFolder) {
            candidatePrefixPaths = FilesUtils.listDirectories(
                    referencePath.resolve(NamingConvention.DECOMPILED_LIB_FOLDER.getConventionName())
            );
        }
        candidatePrefixPaths.add(repoSourcePath);
        for(Path candidatePrefixPath : candidatePrefixPaths) {
            Path classFilePath = candidatePrefixPath.resolve(fqnPath);
            if (Files.exists(classFilePath)) {
                try {
                    CompilationUnit cu = JavaParserUtils.getCompilationUnit(classFilePath);
                    if (cu.getPrimaryType().isPresent()) {
                        return Optional.of(cu.getPrimaryType().get());
                    } else {
                        return Optional.empty();
                    }
                } catch (IOException e) {
                    logger.error("Error reading file: " + classFilePath);
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
