package star.llms.prompts.dataset.preprocessing.utils;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.*;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration;
import javassist.expr.NewArray;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import star.llms.prompts.dataset.data.enums.*;
import star.llms.prompts.dataset.data.exceptions.*;
import star.llms.prompts.dataset.data.records.OraclesDatasetConfig;
import star.llms.prompts.dataset.data.records.RepositoryTrack;
import star.llms.prompts.dataset.preprocessing.builders.*;
import star.llms.prompts.dataset.preprocessing.components.*;
import star.llms.prompts.dataset.preprocessing.components.Type;
import star.llms.prompts.dataset.utils.FilesUtils;
import star.llms.prompts.dataset.utils.javaParser.JavaParserUtils;
import star.llms.prompts.dataset.utils.javaParser.visitors.declarations.FieldDeclarationVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.expressions.MethodCallExprVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.expressions.NameExprVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.expressions.VariableDeclarationExprVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.AssertStmtVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.ExpressionStmtVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.ReturnStmtVisitor;
import star.llms.prompts.dataset.utils.javaParser.visitors.statements.helper.StmtVisitorHelper;

import javax.swing.plaf.nimbus.State;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class provides static utility methods to process test files and
 * generate Oracles Datapoints.
 */
public class TestUtils {

    /* A unique id for placeholder variable names when inserting oracles. */
    private static int variableID = 0;

    /* The logger for the class. */
    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);
    /* helper labels to identify fake elements added to avoid JavaParser parsing problems, like in try-catch statements */
    private static final String FAKE_ELEMENT_LABEL = "<FAKE_ELEMENT>";
    private static final String THROW_EXCEPTION_LABEL = "THROW EXCEPTION";

    /* Do not instantiate this class. */
    private TestUtils() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * Initialize a new split test case. It clones the original test method and renames it with
     * the index of the split test case. Returns the new cloned split test case.
     *
     * @param originalTestMethod the original test method
     * @param idx the index of the split test case
     * @return the new cloned split test case
     */
    private static MethodDeclaration initializeSplitTestCase(MethodDeclaration originalTestMethod, String testCaseName, int idx) {
        MethodDeclaration splitTestCase = originalTestMethod.clone();
        String delimiter = "_split_";
        int delimiterPosition = testCaseName.indexOf("_split_");
        if (delimiterPosition != -1) {
            String delimiterPlusIdx = testCaseName.substring(delimiterPosition, testCaseName.length());
            testCaseName = testCaseName.replace(delimiterPlusIdx, "");
        }
        splitTestCase.setName(testCaseName + delimiter + idx);
        return splitTestCase;
    }

    /**
     * Initialize a new split test case. It clones the original test method, renames it with
     * the index of the split test case, and initializes the body of the new test case.
     * Returns the new cloned split test case.
     *
     * @param originalTestMethod the original test method
     * @param idx the index of the split test case
     * @param body the body of the split test case
     * @return the new cloned split test case
     */
    private static MethodDeclaration initializeSplitTestCase(MethodDeclaration originalTestMethod, String testCaseName, int idx, BlockStmt body) {
        MethodDeclaration splitTestCase = TestUtils.initializeSplitTestCase(originalTestMethod, testCaseName, idx);
        splitTestCase.setBody(body);
        return splitTestCase;
    }

    /**
     * Perform the matching between the focal method(s) and the test prefix.
     * Extracts each method under test from the test prefix and generate triples of (focalMethod, testPrefix, docstring).
     * The focal method is considered the last statement in the test method that belongs to the fullyQualifiedClassName.
     * Saves the output file in output/toga/input.
     *
     * @param testFilePath the path to the test class file
     * @param repoRootPath the path to the source code of the project under test
     * @param outputDirPath the path to the output folder
     * @param m2tMatchingType the type of matching to use (any occurrence or last occurrence)
     *
     */
    public static void matchMethodsToTests(Path testFilePath, Path repoRootPath, Path outputDirPath, M2TMatchingType m2tMatchingType) {

    }

    public static JUnitVersion getJunitVersion(NodeList<ImportDeclaration> imports) {
        for (ImportDeclaration importDeclaration : imports) {
            if (importDeclaration.getNameAsString().contains("junit")) {
                if (importDeclaration.getNameAsString().contains("jupiter")) {
                    return JUnitVersion.JUNIT5;
                } else {
                    return JUnitVersion.JUNIT4;
                }
            }
        }
        throw new IllegalStateException("No JUnit version found in the imports");
    }

    /**
     * Check if the test case is in the filter list collected during the tracking of repositories.
     *
     * @param originalTestCase the test case in the test class
     * @param testCaseFilterList the list of test cases collected during the tracking of repositories
     * @return true if the test case is in the filter list and the body coincides with the test case, false otherwise
     */
    private static boolean matchTestCaseWithRepoTrackTestCase(MethodDeclaration originalTestCase, List<RepositoryTrack.TestCase> testCaseFilterList) {
        // Check if the test case is in the filter list
        if (testCaseFilterList != null && !testCaseFilterList.isEmpty()) {
            String testCaseName = originalTestCase.getNameAsString();
            String testCaseSignature = originalTestCase.getSignature().toString();
            boolean found = false;
            for (RepositoryTrack.TestCase testCaseFilter : testCaseFilterList) {
                if (testCaseName.equals(testCaseFilter.name())) {
                    // Check if the test case signature is in the filter list
                    if (testCaseFilter.signature().replaceAll("\\s+", "").contains(testCaseSignature.replaceAll("\\s+", ""))) {
                        // Check if the body coincides with the test case
                        if (testCaseFilter.body() != null) {
                            Optional<BlockStmt> testCaseBody = originalTestCase.getBody();
                            if (testCaseBody.isPresent()) {
                                // Check if the body of the test case is in the filter list
                                String testCaseBodyString = testCaseBody.get().toString();
                                String testCaseBodyNormalized = testCaseBodyString.replaceAll("\\s+", " ").trim();
                                String testCaseFilterBodyNormalized = testCaseFilter.body().replaceAll("\\s+", " ").trim();
                                if (testCaseBodyNormalized.equals(testCaseFilterBodyNormalized)) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            return found;
        }
        return false;
    }

    /**
     * Process the test class and integrate additional assertion in auxiliary methods, if present.
     *
     * @param config the configuration options for the dataset to generate
     * @param repoRootPath the path to the root of the repository
     * @param testFilePath the path to the test class file
     * @param outputPath the path to the normalized test class file
     * @param testCaseFilterList the list of test cases to process in the class
     * @return the path to the normalized test class file
     */
    public static Pair<Path, List<TestStats>> normalizeTest(
            OraclesDatasetConfig config,
            Path repoRootPath,
            Path testFilePath,
            Path outputPath,
            List<RepositoryTrack.TestCase> testCaseFilterList
    ) {
        // Instantiate the list of test statistics
        List<TestStats> testStatsList = new ArrayList<>();
        // Get the relative path of the test file
        String testFileRelativePath = testFilePath.toString().replace(repoRootPath.getParent().toString(), "");
        // Regular output path for report analysis
        Path regularOutputPath = Paths.get(outputPath.toString(), "normalized", testFileRelativePath.replace(".java", NamingConvention.NORMALIZED_TEST_FILE.getConventionName()));
        // Error output path for error analysis
        Path errorOutputPath = Paths.get(outputPath.toString(), "error", "normalized", testFileRelativePath.replace(".java", NamingConvention.NORMALIZED_TEST_FILE.getConventionName()));
        // Test repository output path for further processing
        Path testRepoOutputPath = Paths.get(testFilePath.toString().replace(".java", NamingConvention.NORMALIZED_TEST_FILE.getConventionName()));
        // Create a list to store the normalized test cases generated from the original test cases of the given test class
        List<MethodDeclaration> normalizedTestCases = new ArrayList<>();
        try {
            // Parse the test class
            CompilationUnit cu = JavaParserUtils.getCompilationUnit(testFilePath);
            // Set the methods of the test case to the normalized test methods
            TypeDeclaration testClass = cu.getPrimaryType().get();
            // Get the list of all the methods defined within the test class
            List<MethodDeclaration> testClassMethods = cu.findAll(MethodDeclaration.class);
            // Distribute the methods of the class according to their meaning
            Triplet<List<MethodDeclaration>, HashMap<String, MethodDeclaration>, HashMap<String, MethodDeclaration>> testClassMethodsDistribution = distributeMethods(testClassMethods);
            // Get the list of all the original test cases within the given test class
            List<MethodDeclaration> originalTestCases = testClassMethodsDistribution.getValue0();
            // Get the list of all the auxiliary methods within the given test class
            HashMap<String, MethodDeclaration> auxiliaryMethods = testClassMethodsDistribution.getValue2();
            // Define list of auxiliary methods integrated into the normalized test case body
            // (they have to be removed from the test class since integrated and no more called)
            List<MethodDeclaration> integratedAuxiliaryMethods = new ArrayList<>();
            int testCaseIdx = 0;
            // Iterate over the original test cases
            for (MethodDeclaration originalTestCase : originalTestCases) {
                // Check if the test case is in the filter list
                if (!matchTestCaseWithRepoTrackTestCase(originalTestCase, testCaseFilterList)) {
                    continue;
                }
                // Get the list of statements within the original test case
                List<Statement> originalTestCaseStatements = originalTestCase.getBody().orElseThrow().getStatements();
                // Generate a mold of the test case and set the body to an empty block
                MethodDeclaration normalizedTestCase = originalTestCase.clone();
                BlockStmt normalizedTestCaseBody = new BlockStmt();
                normalizedTestCase.setBody(normalizedTestCaseBody);

                if (!(auxiliaryMethods.isEmpty() || !config.integrateAuxiliaryMethods())) {
                    // Parse the statements block and normalize the test case
                    boolean thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            originalTestCaseStatements,
                            normalizedTestCaseBody,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                    String starTestCodeKey = testClass.getNameAsString() + NamingConvention.NORMALIZED_TEST_CLASS.getConventionName() + "-" + originalTestCase.getSignature();
                    String starTestCodeValue = testClass.getNameAsString() + "-" + normalizedTestCase.getNameAsString() + "-" + testCaseIdx++;
                    // Instantiate the test stats builder to collect the statistics of the test
                    TestStatsBuilder testStatsBuilder = new TestStatsBuilder();
                    testStatsBuilder.setIdentifier(originalTestCase.getNameAsString());
                    testStatsBuilder.setSignature(originalTestCase.getSignature().toString());
                    testStatsBuilder.setClassIdentifier(testClass.getNameAsString());
                    testStatsBuilder.setFilePath(testFilePath.toString());
                    testStatsBuilder.setNumberOfAssertions(getNumberOfAssertions(normalizedTestCaseBody.getStatements()));
                    testStatsBuilder.setTestLength(normalizedTestCaseBody.toString().length());
                    testStatsBuilder.setAssertionsDistribution(getAssertionsDistribution(normalizedTestCaseBody.getStatements()));
                    // Count the number of methods found in the test
                    int methodCalls = 0;
                    for (Statement statement : normalizedTestCaseBody.getStatements()) {
                        MethodCallExprVisitor methodCallExprCollector = new MethodCallExprVisitor();
                        List<MethodCallExpr> methodCallExprs = methodCallExprCollector.visit(statement);
                        methodCalls += methodCallExprs.size();
                    }
                    testStatsBuilder.setNumberOfMethodCalls(methodCalls);

                    VariableDeclarationExprVisitor variableDeclarationExprCollector = new VariableDeclarationExprVisitor();
                    List<String> declaredVarList = variableDeclarationExprCollector.visit(normalizedTestCaseBody).stream().map(VariableDeclarationExpr::toString).collect(Collectors.toList());
                    NameExprVisitor nameExprCollector = new NameExprVisitor();
                    List<String> usedVarList = nameExprCollector.visit(normalizedTestCaseBody).stream().map(NameExpr::toString).collect(Collectors.toList());
                    List<String> paramList = normalizedTestCase.getParameters().stream().map(p -> p.getNameAsString()).collect(Collectors.toList());
                    Set<String> allVarList = new HashSet<>();
                    allVarList.addAll(declaredVarList);
                    allVarList.addAll(usedVarList);
                    allVarList.addAll(paramList);
                    testStatsBuilder.setNumberOfVariables(allVarList.size());
                    testStatsList.add(testStatsBuilder.build());

                    if (thrownedException) {
                        // Define the error test class compilation unit
                        CompilationUnit errorCu = cu;
                        // Define the error test class
                        TypeDeclaration errorTestClass = cu.getPrimaryType().get();
                        // Check if the error test class already exists. Otherwise, create a new one from the original test class
                        if (!Files.exists(errorOutputPath)) {
                            errorTestClass.setMembers(new NodeList<>());
                        } else {
                            // Get the error test class from the existing file
                            errorCu = JavaParserUtils.getCompilationUnit(errorOutputPath);
                            try {
                                errorTestClass = errorCu.getPrimaryType().get();
                            } catch (NoSuchElementException e) {
                                errorTestClass = errorCu.getType(0);
                            }
                        }
                        // Get the list of methods already added in the error test class (if any)
                        List<MethodDeclaration> errorTestClassMethods = errorTestClass.getMethods();
                        // Check if the error test class already contains the original test case
                        if (!errorTestClassMethods.stream().anyMatch(m -> m.getNameAsString().equals(originalTestCase.getNameAsString()))) {
                            // Add the original test case to the error test class
                            errorTestClass.addMember(originalTestCase);
                            // Save the error test class to the error output path
                            FilesUtils.writeJavaFile(errorOutputPath, errorCu);
                        }
                    }
                    normalizedTestCases.add(normalizedTestCase);
                }
            }
            testClass.setName(testClass.getNameAsString() + NamingConvention.NORMALIZED_TEST_CLASS.getConventionName());
            for (MethodDeclaration originalTestCase : originalTestCases) {
                testClass.remove(originalTestCase);
            }
            for (MethodDeclaration integratedAuxiliaryMethod : integratedAuxiliaryMethods) {
                testClass.remove(integratedAuxiliaryMethod);
            }
            for (MethodDeclaration normalizedTestCase : normalizedTestCases) {
                testClass.addMember(normalizedTestCase);
            }
            // Save the split test class to the output paths (regular and test repository)
            FilesUtils.writeJavaFile(regularOutputPath, cu);
            FilesUtils.writeJavaFile(testRepoOutputPath, cu);
        } catch (IOException e) {
            logger.error("Error reading file: " + testFilePath);
        }
        return new Pair<>(testRepoOutputPath, testStatsList);
    }

    /**
     * Split the test cases in the test class, according to the strategy.
     *
     * If strategy is set to `assertion`, the test is split at any occurrence of an assertion.
     * If strategy is set to `statement`, the test is split at any statement.
     *
     * For example, given the following pseudo test class:
     *
     * <pre>
     *     public class TestClass {
     *          @Test
     *          public void testMethod1() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *              var result3 = method3();
     *              assert2();
     *              assert3();
     *          }
     *
     *          @Test
     *          public void testMethod2() { ... }
     *     }
     * </pre>
     *
     * The STATEMENT strategy will return the following list of methods (in the form of
     * {@link com.github.javaparser.ast.body.MethodDeclaration}):
     *
     * <pre>
     *     [
     *          public void testMethod1_split_0() {
     *              var result1 = method1();
     *          },
     *          public void testMethod1_split_1() {
     *              var result1 = method1();
     *              var result2 = method2();
     *          },
     *          public void testMethod1_split_2() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *          },
     *          public void testMethod1_split_3() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *              var result3 = method3();
     *          }
     *          ...
     *     ]
     * </pre>
     *
     * The ASSERTION stretegy will return the following list of methods (in the form of
     * {@link com.github.javaparser.ast.body.MethodDeclaration}):
     *
     * <pre>
     *     [
     *          public void testMethod1_split_0() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *          },
     *          public void testMethod1_split_1() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *              var result3 = method3();
     *              assert2();
     *         },
     *         public void testMethod1_split_2() {
     *              var result1 = method1();
     *              var result2 = method2();
     *              assert1();
     *              var result3 = method3();
     *              assert2();
     *              assert3();
     *         }
     *    ]
     * </pre>
     *
     * @param config the configuration options for the dataset to generate
     * @param repoRootPath the path to the root of the repository
     * @param testFilePath the path to the test class file
     * @param outputPath the path to the split test class file
     * @throws IllegalStateException if no JUnit version is found in the imports and the test class cannot include
     * assertions
     * @return the path to the split test class file
     */
    public static Path splitTest(
            OraclesDatasetConfig config,
            Path repoRootPath,
            Path testFilePath,
            Path outputPath
    ) throws IllegalStateException, UnsupportedOperationException {
        String testFileRelativePath = testFilePath.toString().replace(repoRootPath.getParent().toString(), "");
        // Regular output path for report analysis
        Path regularOutputPath = Paths.get(outputPath.toString(), "regular", testFileRelativePath.replace(NamingConvention.NORMALIZED_TEST_FILE.getConventionName(), NamingConvention.TEST_SPLIT_FILE.getConventionName()));
        // Error output path for error analysis
        Path errorOutputPath = Paths.get(outputPath.toString(), "error", "split", testFileRelativePath.replace(NamingConvention.NORMALIZED_TEST_FILE.getConventionName(), NamingConvention.TEST_SPLIT_FILE.getConventionName()));
        // Test repository output path for further processing
        Path testRepoOutputPath = Paths.get(testFilePath.toString().replace(NamingConvention.NORMALIZED_TEST_FILE.getConventionName(), NamingConvention.TEST_SPLIT_FILE.getConventionName()));
        // Create a list to store the split test cases generated from the original test cases of the given test class
        List<MethodDeclaration> splitTestCases = new ArrayList<>();
        try {
            // Parse the test class
            CompilationUnit cu = JavaParserUtils.getCompilationUnit(testFilePath);
            TypeDeclaration testClass = cu.getPrimaryType().get();
            // Get Junit version
            JUnitVersion junitVersion = TestUtils.getJunitVersion(cu.getImports());
            // Get the list of all the methods defined within the test class
            List<MethodDeclaration> testClassMethods = cu.findAll(MethodDeclaration.class);
            // Distribute the methods of the class according to their meaning
            Triplet<List<MethodDeclaration>, HashMap<String, MethodDeclaration>, HashMap<String, MethodDeclaration>> testClassMethodsDistribution = distributeMethods(testClassMethods);
            // Get the list of all the original test cases within the given test class
            List<MethodDeclaration> originalTestCases = testClassMethodsDistribution.getValue0();
            // Get the list of all the setup and tear down methods within the given test class
            HashMap<String, MethodDeclaration> setUpTearDownMethods = testClassMethodsDistribution.getValue1();
            // Get the list of all the auxiliary methods within the given test class
            HashMap<String, MethodDeclaration> auxiliaryMethods = testClassMethodsDistribution.getValue2();
            // Iterate over the original test cases
            for (MethodDeclaration originalTestCase : originalTestCases) {
                // Get the list of statements within the original test case
                List<Statement> originalTestCaseStatements = originalTestCase.getBody().orElseThrow().getStatements();
                // Parse the statements block and split the test case into multiple test cases
                try {
                    // Generate a mold of the test case and set the body to an empty block
                    MethodDeclaration testCaseMold = originalTestCase.clone();
                    testCaseMold.setBody(new BlockStmt());
                    // Parse the statements of the original test case and split it into multiple test cases
                    Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                            config,
                            junitVersion,
                            originalTestCaseStatements,
                            testCaseMold,
                            0,
                            auxiliaryMethods,
                            BlockStatementsType.METHOD_BODY,
                            0
                    );
                    // Add the split test cases generated from the original test case to the list
                    splitTestCases.addAll(result.getValue1());
                } catch (IllegalStateException e) {
                    // Log the error occurred while parsing the statements of the test case
                    logger.error(e.getMessage());
                    // Define the error test class compilation unit
                    CompilationUnit errorCu = cu.clone();
                    // Define the error test class
                    TypeDeclaration errorTestClass = errorCu.getPrimaryType().get();
                    // Check if the error test class already exists. Otherwise, create a new one from the original test class
                    if (!Files.exists(errorOutputPath)) {
                        errorTestClass.setName(errorTestClass.getNameAsString().replace(NamingConvention.NORMALIZED_TEST_CLASS.getConventionName(), NamingConvention.TEST_SPLIT_CLASS.getConventionName()));
                        errorTestClass.setMembers(new NodeList<>());
                    } else {
                        // Get the error test class from the existing file
                        errorCu = JavaParserUtils.getCompilationUnit(errorOutputPath);
                        errorTestClass = errorCu.getPrimaryType().get();
                    }
                    // Get the list of methods already added in the error test class (if any)
                    List<MethodDeclaration> errorTestClassMethods = errorTestClass.getMethods();
                    // Check if the error test class already contains the original test case
                    if (!errorTestClassMethods.stream().anyMatch(m -> m.getNameAsString().equals(originalTestCase.getNameAsString()))) {
                        // Add the original test case to the error test class
                        errorTestClass.addMember(originalTestCase);
                        // Save the error test class to the error output path
                        FilesUtils.writeJavaFile(errorOutputPath, errorCu);
                    }
                }
            }
            // Set the methods of the test case to the split test methods
            testClass.setName(testClass.getNameAsString().replace(NamingConvention.NORMALIZED_TEST_CLASS.getConventionName(), NamingConvention.TEST_SPLIT_CLASS.getConventionName()));
            for (MethodDeclaration originalTestCase : originalTestCases) {
                testClass.remove(originalTestCase);
            }
            for (MethodDeclaration splitTestCase : splitTestCases) {
                testClass.addMember(splitTestCase);
            }
            // Save the split test class to the output paths (regular and test repository)
            FilesUtils.writeJavaFile(regularOutputPath, cu);
            FilesUtils.writeJavaFile(testRepoOutputPath, cu);
        } catch (IOException e) {
            logger.error("Error reading file: " + testFilePath);
        }
        return testRepoOutputPath;
    }

    /**
     * Parse the statements of the given block and split the test case into multiple test cases at any occurrence of an
     * assertion. The list of statements represent the statements within a block. Each statement is analyzed through the
     * help of JavaParser.
     * If the statement is a simple expression the statement is processed and added to the current test case.
     * If the statement is an assertion, the assertion is added to the current test case and the current test is
     * completed (a new split test is created, starting from the state of the last test prefix, with the last assertion added,
     * the index of the split test cases is incremented accordingly)
     * If the statement is a complex statement (representing for example a try-catch, a for loop, a while loop, etc.) the
     * statement is analyzed progressively, calling the method recursively, incrementing the recursion level, and defining
     * the type of the block statement to analyze in the next recursion step.
     * The method returns the last test prefix generated, and the list of the intermediary test prefixes, split at any
     * occurrence of an assertion.
     *
     * @param config the configuration options for the dataset to generate
     * @param junitVersion the version of JUnit used in the test class
     * @param statements the list of statements to parse
     * @param testCaseMold the mold of the test case
     * @param startIdx the index of the split test cases
     * @param auxiliaryMethods the auxiliary methods of the test class
     * @param blockStatementsType the type of block statements
     * @param recursionLevel the level of recursion of the current block of statements analyzed
     * @return the pair of the last split test case generated and the list of split test cases
     * @throws IllegalStateException if an error occurs while parsing the statements of the block
     */
    private static Pair<MethodDeclaration, List<MethodDeclaration>> parseSplitTestStatementsBlock(
            OraclesDatasetConfig config,
            JUnitVersion junitVersion,
            List<Statement> statements,
            MethodDeclaration testCaseMold,
            int startIdx,
            HashMap<String, MethodDeclaration> auxiliaryMethods,
            BlockStatementsType blockStatementsType,
            int recursionLevel
    ) throws IllegalStateException {
        // Create a list to store the split test cases generated from the given block of statements of the original test case
        List<MethodDeclaration> splitTestCases = new ArrayList<>();
        // Get the number of assertions in the test case
        int numberOfAssertions = getNumberOfAssertions(statements);
        // Initialize the index of the split test cases (incremental index to name the split test cases)
        int idx = startIdx;
        // Get the name prefix of the original test case
        String testCasePrefixName = testCaseMold.getNameAsString();
        // Initialize the first split test case (clone the test and assign the name to the split test case)
        MethodDeclaration splitTestCase = initializeSplitTestCase(testCaseMold, testCasePrefixName, idx);
        // Get the body of the initialized split test case
        BlockStmt splitTestCaseBody = splitTestCase.getBody().get();
        // Iterate over the statements of the original test case
        for (Statement statement : statements) {
            // Create flag to check if the statement is an assertion
            boolean isAssertion = false;
            // Check if the statement is a potential assertion
            if (statement.isAssertStmt()) {
                // Add the statement to the current split test case body
                addStatement(statement, splitTestCaseBody, blockStatementsType);
                // Flag the statement as an assertion
                isAssertion = true;
            } else if (statement.isBlockStmt()) {
                // Add a test case with the empty body
                //if (!(blockStatementsType == BlockStatementsType.TRY)) {
                //    // Initialize a new split test case, starting from the body of the previous one
                //    MethodDeclaration newSplitTestCase = initializeSplitTestCase(splitTestCase, testCasePrefixName, ++idx);
                //    // Add the split test case to the list
                //    splitTestCases.add(splitTestCase);
                //    // Update the split test case with the new one
                //    splitTestCase = newSplitTestCase;
                //}
                // Parse the statements of the do body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        statement.asBlockStmt().getStatements(),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        blockStatementsType,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the block statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the do statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else if (statement.isDoStmt()) {
                // Parse the do statement and split the test case into multiple test cases
                Statement doStmtBody = statement.asDoStmt().getBody();
                // Clone the do statement and set the body to an empty block
                DoStmt doStmtClone = statement.asDoStmt().clone();
                doStmtClone.setBody(new BlockStmt());
                // Add the cloned do statement to the current split test case body
                addStatement(doStmtClone, splitTestCaseBody, blockStatementsType);
                // Parse the statements of the do body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        new ArrayList<>(Arrays.asList(doStmtBody)),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        BlockStatementsType.DO,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the do statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the do statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else if (statement.isExpressionStmt()) {
                // Get the expression from the statement
                Expression currentExpr = statement.asExpressionStmt().getExpression();
                // Check if the expression is a variable declaration expression and extract the initializer, if present
                if (currentExpr.isVariableDeclarationExpr()) {
                    Optional<Expression> initializer = currentExpr.asVariableDeclarationExpr().getVariable(0).getInitializer();
                    if (initializer.isPresent()) {
                        currentExpr = initializer.get();
                    }
                }
                // Check if the expression is an assertion
                if (currentExpr.isMethodCallExpr() && JUnitAssertionType.isJUnitAssertion(currentExpr.asMethodCallExpr().getNameAsString())) {
                    // Add the statement to the flat body
                    if (!(JUnitAssertionType.fromString(((MethodCallExpr) currentExpr).getNameAsString()) == JUnitAssertionType.ASSERT_THROWS)) {
                        addStatement(statement, splitTestCaseBody, blockStatementsType);
                        // Flag the statement as an assertion
                        isAssertion = true;
                    } else {
                        if (config.assertThrowsStrategy() == AssertThrowsStrategyType.STANDARD) {
                            // Add the statement to the flat body
                            addStatement(statement, splitTestCaseBody, blockStatementsType);
                            // Flag the statement as an assertion
                            isAssertion = true;
                        } else {
                            BlockStmt newSplitTestCaseBody = splitTestCaseBody.clone();
                            newSplitTestCaseBody.addStatement(statement);
                            MethodCallExpr assertThrowsMethodCallExpr = statement.asExpressionStmt().getExpression().asMethodCallExpr();
                            Expression expr;
                            if (junitVersion == JUnitVersion.JUNIT4) {
                                expr = assertThrowsMethodCallExpr.getArguments().getLast().get();
                            } else {
                                expr = assertThrowsMethodCallExpr.getArguments().get(1);
                            }

                            if (expr.isMethodCallExpr()) {
                                ExpressionStmt exprStmt = new ExpressionStmt();
                                exprStmt.setExpression(expr.clone());
                                addStatement(exprStmt, splitTestCaseBody, blockStatementsType);
                            } else if (expr.isLambdaExpr()) {
                                LambdaExpr lambdaExpr = expr.asLambdaExpr();
                                if (lambdaExpr.getParameters().size() > 0) {
                                    throw new IllegalStateException("Unexpected assertThrows lambda expression with parameters not supported");
                                }
                                Optional<Expression> lambdaExprBody = lambdaExpr.getExpressionBody();

                                if (lambdaExprBody.isPresent()) {
                                    // The lambda expression is not a block statement but a single expression
                                    Expression lambdaSingleExpr = lambdaExprBody.get();
                                    if (lambdaSingleExpr.isMethodCallExpr()) {
                                        ExpressionStmt exprStmt = new ExpressionStmt();
                                        exprStmt.setExpression(lambdaSingleExpr.clone());
                                        addStatement(exprStmt, splitTestCaseBody, blockStatementsType);
                                        if (JUnitAssertionType.isJUnitAssertion(lambdaSingleExpr.asMethodCallExpr().getNameAsString()) || config.splitStrategy() == SplitStrategyType.STATEMENT) {
                                            // Initialize a new split test case, starting from the body of the previous one
                                            MethodDeclaration newSplitTestCase = initializeSplitTestCase(splitTestCase, testCasePrefixName, ++idx);
                                            // Add the split test case to the list
                                            splitTestCases.add(splitTestCase);
                                            // Update the split test case with the new one
                                            splitTestCase = newSplitTestCase;
                                            // Get the body of the new split test case
                                            splitTestCaseBody = splitTestCase.getBody().orElseThrow();
                                        }
                                    } else {
                                        throw new IllegalStateException("Unexpected assertThrows lambda expression with non-method call expression");
                                    }
                                } else {
                                    // The lambda expression is a block statement
                                    Statement lambdaStmt = lambdaExpr.getBody();
                                    if (lambdaStmt.isBlockStmt()) {
                                        BlockStmt blockStmt = lambdaStmt.asBlockStmt();
                                        Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                                                config,
                                                junitVersion,
                                                blockStmt.getStatements(),
                                                splitTestCase,
                                                idx,
                                                auxiliaryMethods,
                                                blockStatementsType,
                                                recursionLevel + 1
                                        );
                                        // Update the split test case with the last generated from the block statement
                                        splitTestCase = result.getValue0();
                                        // Update the split test case body
                                        splitTestCaseBody = splitTestCase.getBody().get();
                                        // Add the split test cases generated from the block statement to the list
                                        splitTestCases.addAll(result.getValue1());
                                        // Update idx of the split tests
                                        idx += result.getValue1().size();
                                    } else {
                                        throw new IllegalStateException("Unexpected assertThrows lambda expression with non-block statement");
                                    }
                                }
                            }
                            Statement fakeStmt = new AssertStmt(new StringLiteralExpr(FAKE_ELEMENT_LABEL), new StringLiteralExpr(FAKE_ELEMENT_LABEL));
                            fakeStmt.addOrphanComment(new LineComment(THROW_EXCEPTION_LABEL));
                            addStatement(fakeStmt, splitTestCaseBody, blockStatementsType);
                            splitTestCases.add(splitTestCase);
                            MethodDeclaration newSplitTestCase = initializeSplitTestCase(splitTestCase, testCasePrefixName, idx);
                            newSplitTestCase.setBody(newSplitTestCaseBody);
                            // Initialize a new split test case, starting from the body of the previous one
                            // Update the split test case with the new one
                            splitTestCase = newSplitTestCase;
                            // Get the body of the new split test case
                            splitTestCaseBody = newSplitTestCaseBody;
                        }
                    }
                } else if (currentExpr.isLambdaExpr()) {
                    LambdaExpr lambdaExpr = currentExpr.asLambdaExpr();
                    Optional<Expression> lambdaExprBody = lambdaExpr.getExpressionBody();
                    if (lambdaExprBody.isPresent()) {
                        // The lambda expression is not a block statement but a single expression
                        currentExpr = lambdaExprBody.get();
                        if (currentExpr.isMethodCallExpr()) {
                            if (JUnitAssertionType.isJUnitAssertion(currentExpr.asMethodCallExpr().getNameAsString())) {
                                // Add the statement to the flat body
                                if (!(JUnitAssertionType.fromString(((MethodCallExpr) currentExpr).getNameAsString()) == JUnitAssertionType.ASSERT_THROWS)) {
                                    addStatement(statement, splitTestCaseBody, blockStatementsType);
                                } else {
                                    throw new IllegalStateException("assertThrows within lambda expression, not supported");
                                }
                                // Flag the statement as an assertion
                                isAssertion = true;
                            }
                        } else {
                            addStatement(statement, splitTestCaseBody, blockStatementsType);
                        }
                    } else {
                        Statement lambdaBody = lambdaExpr.getBody();
                        LambdaExpr lambdaExprClone = lambdaExpr.clone();
                        lambdaExprClone.setParameters(new NodeList<>(lambdaExpr.getParameters()));
                        ExpressionStmt lambdaExprStmt = new ExpressionStmt(lambdaExpr.clone());
                        addStatement(lambdaExprStmt, splitTestCaseBody, blockStatementsType);
                        Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(lambdaBody)),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.LAMBDA,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the lambda expression
                        splitTestCase = result.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the lambda expression to the list
                        splitTestCases.addAll(result.getValue1());
                        // Update idx of the split tests
                        idx += result.getValue1().size();
                    }
                } else {
                    if (currentExpr.findAll(BlockStmt.class).isEmpty()) {
                        addStatement(statement, splitTestCaseBody, blockStatementsType);
                    } else {
                        if (currentExpr.findAll(BlockStmt.class).size() > 1) {
                            throw new IllegalStateException("Unexpected multiple block statement within expression statement, not supported.");
                        }
                        BlockStmt blockStmt = currentExpr.findAll(BlockStmt.class).get(0);
                        if (!blockStmt.getParentNode().isPresent() || ! (blockStmt.getParentNode().get() instanceof LambdaExpr)) {
                            throw new IllegalStateException("Unexpected block statement parent node of block in expression different from lambda expression, not supported.");
                        }
                        ExpressionStmt stmtClone = null;
                        Node parentNode = currentExpr.getParentNode().get();
                        while (parentNode != null) {
                            if (parentNode instanceof ExpressionStmt) {
                                stmtClone = ((ExpressionStmt) parentNode).clone();
                                break;
                            }
                            parentNode = parentNode.getParentNode().orElse(null);
                        }
                        if (stmtClone == null) {
                            throw new IllegalStateException("Unexpected block statement parent node of block in expression different from statement, not supported.");
                        }
                        stmtClone.findAll(BlockStmt.class).get(0).setStatements(new NodeList<>());
                        addStatement(stmtClone, splitTestCaseBody, blockStatementsType);
                        Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                blockStmt.getStatements(),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.LAMBDA,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the block statement
                        splitTestCase = result.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the block statement to the list
                        splitTestCases.addAll(result.getValue1());
                        // Update idx of the split tests
                        idx += result.getValue1().size();
                    }
                }
            } else if (statement.isForEachStmt()) {
                // Parse the for each statement and split the test case into multiple test cases
                Statement forEachStmtBody = statement.asForEachStmt().getBody();
                // Clone the for each statement and set the body to an empty block
                ForEachStmt forEachStmtClone = statement.asForEachStmt().clone();
                forEachStmtClone.setBody(new BlockStmt());
                // Add the cloned for each statement to the current split test case body
                addStatement(forEachStmtClone, splitTestCaseBody, blockStatementsType);
                // Parse the statements of the for each body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        new ArrayList<>(Arrays.asList(forEachStmtBody)),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        BlockStatementsType.FOR_EACH,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the for each statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the for each statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else if (statement.isForStmt()) {
                // Parse the for statement and split the test case into multiple test cases
                Statement forStmtBody = statement.asForStmt().getBody();
                // Clone the for statement and set the body to an empty block
                ForStmt forStmtClone = statement.asForStmt().clone();
                forStmtClone.setBody(new BlockStmt());
                // Add the cloned for statement to the current split test case body
                addStatement(forStmtClone, splitTestCaseBody, blockStatementsType);
                // Parse the statements of the for body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        new ArrayList<>(Arrays.asList(forStmtBody)),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        BlockStatementsType.FOR,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the for statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the for statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else if (statement.isIfStmt()) {
                IfStmt ifStmt = statement.asIfStmt();
                List<Pair<Optional<Expression>,Statement>> ifStmts = new ArrayList<>();
                ifStmts.add(new Pair<>(Optional.of(ifStmt.getCondition()),ifStmt.getThenStmt()));
                if (ifStmt.getElseStmt().isPresent()) {
                    Optional<Statement> elseIfStmt = ifStmt.getElseStmt();
                    while (elseIfStmt.isPresent()) {
                        if (elseIfStmt.get().isIfStmt()) {
                            ifStmts.add(new Pair<>(Optional.of(elseIfStmt.get().asIfStmt().getCondition()), elseIfStmt.get().asIfStmt().getThenStmt()));
                        } else {
                            ifStmts.add(new Pair<>(Optional.empty(), elseIfStmt.get()));
                        }
                        elseIfStmt = elseIfStmt.get().isIfStmt() ? elseIfStmt.get().asIfStmt().getElseStmt() : Optional.empty();
                    }
                }
                // Define if statement clone
                IfStmt ifStmtClone = null;
                for (Pair<Optional<Expression>,Statement> ifStmtPair : ifStmts) {
                    Optional<Expression> ifCondition = ifStmtPair.getValue0();
                    Statement ifStmtBody = ifStmtPair.getValue1();
                    boolean rootIf = false;
                    if (ifCondition.isPresent()) {
                        if (ifStmtClone == null) {
                            rootIf = true;
                            ifStmtClone = new IfStmt(ifCondition.get(), new BlockStmt(), null);
                            // Add the cloned if statement to the current split test case body
                            addStatement(ifStmtClone, splitTestCaseBody, blockStatementsType);
                        } else {
                            IfStmt lastIfStmt = traverseIfElseStatement(ifStmtClone);
                            lastIfStmt.setElseStmt(new IfStmt(ifCondition.get(), new BlockStmt(), null));
                        }
                    } else {
                        IfStmt lastIfStmt = traverseIfElseStatement(ifStmtClone);
                        lastIfStmt.setElseStmt(new BlockStmt());
                    }
                    // Parse the statements of the if body and split it into multiple test cases
                    Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                            config,
                            junitVersion,
                            new ArrayList<>(Arrays.asList(ifStmtBody)),
                            splitTestCase,
                            idx,
                            auxiliaryMethods,
                            rootIf ? BlockStatementsType.IF : BlockStatementsType.ELSE,
                            recursionLevel + 1
                    );
                    // Update the split test case with the last generated from the if statement
                    splitTestCase = result.getValue0();
                    // Update the split test case body
                    splitTestCaseBody = splitTestCase.getBody().get();
                    // Add the split test cases generated from the if statement to the list
                    splitTestCases.addAll(result.getValue1());
                    // Update idx of the split tests
                    idx += result.getValue1().size();
                    // Update the if statement clone
                    ifStmtClone = JavaParserUtils.getLastStatementTypeOccurrence(splitTestCaseBody, IfStmt.class)
                            .orElseThrow(() -> new IllegalStateException(
                                    "No statement of type " + IfStmt.class + " found in the block of statements."
                            ))
                            .asIfStmt();
                }
            } else if (statement.isSynchronizedStmt()) {
                // Parse the synchronized statement and split the test case into multiple test cases
                Statement synchronizedStmtBody = statement.asSynchronizedStmt().getBody();
                // Clone the synchronized statement and set the body to an empty block
                SynchronizedStmt synchronizedStmtClone = statement.asSynchronizedStmt().clone();
                synchronizedStmtClone.setBody(new BlockStmt());
                // Add the cloned synchronized statement to the current split test case body
                addStatement(synchronizedStmtClone, splitTestCaseBody, blockStatementsType);
                // Parse the statements of the synchronized body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        new ArrayList<>(Arrays.asList(synchronizedStmtBody)),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        BlockStatementsType.METHOD_BODY,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the synchronized statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the synchronized statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else if (statement.isSwitchStmt()) {
                throw new UnsupportedOperationException("Switch statement not supported yet.");
                // TODO: Implement switch statement
                // Parse the switch statement and split the test case into multiple test cases
                // SwitchStmt switchStmt = statement.asSwitchStmt();
                // for (SwitchEntry switchEntry : switchStmt.getEntries()) {
                //     // Clone the switch statement and set the body to an empty block
                //     SwitchStmt switchStmtClone = statement.asSwitchStmt().clone();
                //     switchStmtClone.setEntries(new NodeList<>());
                //     // Clone the current switch entry and set the statements to an empty block
                //     SwitchEntry switchEntryClone = switchEntry.clone();
                //     switchEntryClone.setStatements(new NodeList<>());
                //     // Add the cloned switch entry to the cloned switch statement
                //     switchStmtClone.getEntries().add(switchEntryClone);
                //     // Add the cloned switch statement to the current split test case body
                //     addStatement(switchStmtClone, splitTestCaseBody, blockStatementsType);
                //     // Parse the statements of the switch entry and split it into multiple test cases
                //     Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                //             config,
                //             junitVersion,
                //             switchEntry.getStatements(),
                //             splitTestCase,
                //             idx,
                //             auxiliaryMethods,
                //             BlockStatementsType.SWITCH,
                //             recursionLevel + 1
                //     );
                //     // Update the split test case with the last generated from the switch entry
                //     splitTestCase = result.getValue0();
                //     // Update the split test case body
                //     splitTestCaseBody = splitTestCase.getBody().get();
                //     // Add the split test cases generated from the switch entry to the list
                //     splitTestCases.addAll(result.getValue1());
                //     // Update idx of the split tests
                //     idx += result.getValue1().size();
                // }
            } else if (statement.isTryStmt()) {
                // Convert the try/catch statement into a plain assertion (assertThrows)
                // Get the try statement
                TryStmt tryStmt = statement.asTryStmt();

                if (config.tryCatchFinallyStrategy() == TryCatchFinallyStrategyType.ASSERT_THROW) {
                    // Check the try statement
                    checkTryStmt(testCasePrefixName, tryStmt);
                    // Check if the try statement contains a fail method call and, in that case, update
                    // the number of assertions (a fail() assertion in the catch block does not split the test,
                    // because it is removed in the current approach)
                    if (tryStmt.getCatchClauses().size() > 0) {
                        Optional<BlockStatementsType> failAssertionPosition = findFailMethodCall(tryStmt);
                        if (failAssertionPosition.isPresent() && failAssertionPosition.get() == BlockStatementsType.CATCH) {
                            numberOfAssertions--;
                        }
                    }
                    // Flatten the try statement into a block statement
                    BlockStmt flatTryStmt = flatTryStmt(junitVersion, tryStmt);
                    // Parse the statements of the synchronized body and split it into multiple test cases
                    Pair<MethodDeclaration, List<MethodDeclaration>> resultTry = parseSplitTestStatementsBlock(
                            config,
                            junitVersion,
                            new ArrayList<>(flatTryStmt.getStatements()),
                            splitTestCase,
                            idx,
                            auxiliaryMethods,
                            blockStatementsType,
                            recursionLevel + 1
                    );
                    // Update the split test case with the last generated from the synchronized statement
                    splitTestCase = resultTry.getValue0();
                    // Update the split test case body
                    splitTestCaseBody = splitTestCase.getBody().get();
                    // Add the split test cases generated from the synchronized statement to the list
                    splitTestCases.addAll(resultTry.getValue1());
                    // Update idx of the split tests
                    idx += resultTry.getValue1().size();

                    // Remove the fake catch clause from the cloned try statement
                    TryStmt lastsplitTestTry = JavaParserUtils.getLastStatementTypeOccurrence(splitTestCaseBody, TryStmt.class).get().asTryStmt();
                    lastsplitTestTry.setCatchClauses(new NodeList<>());
                    for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                        // Parse the statements of the catch block and split it into multiple test cases
                        Statement catchStmtBody = catchClause.getBody();
                        // Clone the catch statement and set the body to an empty block
                        CatchClause catchClauseClone = catchClause.clone();
                        catchClauseClone.setBody(new BlockStmt());
                        addCatchClause(catchClauseClone, splitTestCaseBody);
                        // Parse the statements of the catch body and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> resultCatch = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(catchStmtBody)),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.CATCH,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the catch statement
                        splitTestCase = resultCatch.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the catch statement to the list
                        splitTestCases.addAll(resultCatch.getValue1());
                        // Update idx of the split tests
                        idx += resultCatch.getValue1().size();
                    }

                    if (tryStmt.getFinallyBlock().isPresent()) {
                        // Add the cloned while statement to the current split test case body
                        addStatement(new BlockStmt(), splitTestCaseBody, BlockStatementsType.FINALLY);
                        // Parse the statements of the try block and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(tryStmt.getFinallyBlock().get())),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.FINALLY,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the while statement
                        splitTestCase = result.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the while statement to the list
                        splitTestCases.addAll(result.getValue1());
                        // Update idx of the split tests
                        idx += result.getValue1().size();
                    }
                } else {
                    // Parse the while statement and split the test case into multiple test cases
                    Statement tryStmtBody = tryStmt.getTryBlock();
                    // Clone the while statement and set the body to an empty block
                    TryStmt tryStmtClone = new TryStmt();
                    // Add temporary catch clause to the cloned try statement to avoid parsing problems
                    // The fake catch close contains a block comment with a specific label, to easily remove it later
                    com.github.javaparser.ast.type.Type type = new ClassOrInterfaceType(null, "Exception");
                    Parameter param = new Parameter(type, "e");
                    CatchClause fakeCatchClause = new CatchClause(param, new BlockStmt());
                    fakeCatchClause.setComment(new BlockComment(TestUtils.FAKE_ELEMENT_LABEL));
                    // Replace the catch clauses from the cloned try statement with the temporary fake catch clause
                    tryStmtClone.setCatchClauses(new NodeList<>(fakeCatchClause));
                    if (config.tryCatchFinallyStrategy() == TryCatchFinallyStrategyType.FLAT) {
                        BlockStmt newSplitTestCaseBody = splitTestCaseBody.clone();
                        tryStmtClone.setTryBlock((BlockStmt) tryStmtBody);
                        newSplitTestCaseBody.addStatement(tryStmtClone);
                        TryStmt tryStmtFilteredStmtClone = tryStmtClone.clone();

                        ExpressionStmtVisitor exprStmtCollector = new ExpressionStmtVisitor();
                        List<ExpressionStmt> exprStmts = exprStmtCollector.visit(tryStmtFilteredStmtClone);

                        for (ExpressionStmt exprStmt : exprStmts) {
                            if (exprStmt.getExpression().isMethodCallExpr()) {
                                MethodCallExpr methodCallExpr = exprStmt.getExpression().asMethodCallExpr();
                                if (JUnitAssertionType.isJUnitAssertion(methodCallExpr.getNameAsString())) {
                                    if (methodCallExpr.getNameAsString().contains("fail")) {
                                        Optional<Node> parentNode = exprStmt.getParentNode();
                                        while (parentNode.isPresent()) {
                                            if (parentNode.get() instanceof BlockStmt) {
                                                BlockStmt blockStmt = (BlockStmt) parentNode.get();
                                                blockStmt.remove(exprStmt);
                                                break;
                                            }
                                            if (parentNode.get() instanceof LambdaExpr) {
                                                logger.error("Fail method call within lambda expression, not supported");
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Parse the statements of the try block and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> resultTry = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(tryStmtFilteredStmtClone.getTryBlock().getStatements()),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                blockStatementsType,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the try statement
                        splitTestCase = resultTry.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the try statement to the list
                        splitTestCases.addAll(resultTry.getValue1());
                        // Update idx of the split tests
                        idx += resultTry.getValue1().size();
                        if (resultTry.getValue1().size() > 0) {
                            if (config.splitStrategy() == SplitStrategyType.STATEMENT && splitTestCases.size() > 0) {
                                MethodDeclaration lastAddedSplitTestCase = splitTestCases.get(splitTestCases.size() - 1);
                                if (lastAddedSplitTestCase.getBody().get().equals(splitTestCaseBody)) {
                                    continue;
                                }
                            }
                        }
                        splitTestCase.setBody(newSplitTestCaseBody);
                        // Initialize a new split test case, starting from the body of the previous one
                        MethodDeclaration newSplitTestCase = initializeSplitTestCase(splitTestCase, testCasePrefixName, idx);
                        // Update the split test case with the new one
                        splitTestCase = newSplitTestCase;
                        // Get the body of the new split test case
                        splitTestCaseBody = splitTestCase.getBody().orElseThrow();
                    } else if (config.tryCatchFinallyStrategy() == TryCatchFinallyStrategyType.STANDARD) {
                        // Add the cloned while statement to the current split test case body
                        addStatement(tryStmtClone, splitTestCaseBody, blockStatementsType);
                        // Parse the statements of the try block and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> resultTry = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(tryStmtBody)),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.TRY,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the while statement
                        splitTestCase = resultTry.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the while statement to the list
                        splitTestCases.addAll(resultTry.getValue1());
                        // Update idx of the split tests
                        idx += resultTry.getValue1().size();
                    }
                    // Remove the fake catch clause from the cloned try statement
                    TryStmt lastsplitTestTry = JavaParserUtils.getLastStatementTypeOccurrence(splitTestCaseBody, TryStmt.class).get().asTryStmt();
                    lastsplitTestTry.setCatchClauses(new NodeList<>());
                    for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                        // Parse the statements of the catch block and split it into multiple test cases
                        Statement catchStmtBody = catchClause.getBody();
                        // Clone the catch statement and set the body to an empty block
                        CatchClause catchClauseClone = catchClause.clone();
                        catchClauseClone.setBody(new BlockStmt());
                        addCatchClause(catchClauseClone, splitTestCaseBody);
                        // Parse the statements of the catch body and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> resultCatch = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(catchStmtBody)),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.CATCH,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the catch statement
                        splitTestCase = resultCatch.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the catch statement to the list
                        splitTestCases.addAll(resultCatch.getValue1());
                        // Update idx of the split tests
                        idx += resultCatch.getValue1().size();
                    }

                    if (tryStmt.getFinallyBlock().isPresent()) {
                        // Add the cloned while statement to the current split test case body
                        addStatement(new BlockStmt(), splitTestCaseBody, BlockStatementsType.FINALLY);
                        // Parse the statements of the try block and split it into multiple test cases
                        Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                                config,
                                junitVersion,
                                new ArrayList<>(Arrays.asList(tryStmt.getFinallyBlock().get())),
                                splitTestCase,
                                idx,
                                auxiliaryMethods,
                                BlockStatementsType.FINALLY,
                                recursionLevel + 1
                        );
                        // Update the split test case with the last generated from the while statement
                        splitTestCase = result.getValue0();
                        // Update the split test case body
                        splitTestCaseBody = splitTestCase.getBody().get();
                        // Add the split test cases generated from the while statement to the list
                        splitTestCases.addAll(result.getValue1());
                        // Update idx of the split tests
                        idx += result.getValue1().size();
                    }
                }
            } else if (statement.isWhileStmt()) {
                // Parse the while statement and split the test case into multiple test cases
                Statement whileStmtBody = statement.asWhileStmt().getBody();
                // Clone the while statement and set the body to an empty block
                WhileStmt whileStmtClone = statement.asWhileStmt().clone();
                whileStmtClone.setBody(new BlockStmt());
                // Add the cloned while statement to the current split test case body
                addStatement(whileStmtClone, splitTestCaseBody, blockStatementsType);
                // Parse the statements of the while body and split it into multiple test cases
                Pair<MethodDeclaration, List<MethodDeclaration>> result = parseSplitTestStatementsBlock(
                        config,
                        junitVersion,
                        new ArrayList<>(Arrays.asList(whileStmtBody)),
                        splitTestCase,
                        idx,
                        auxiliaryMethods,
                        BlockStatementsType.WHILE,
                        recursionLevel + 1
                );
                // Update the split test case with the last generated from the while statement
                splitTestCase = result.getValue0();
                // Update the split test case body
                splitTestCaseBody = splitTestCase.getBody().get();
                // Add the split test cases generated from the while statement to the list
                splitTestCases.addAll(result.getValue1());
                // Update idx of the split tests
                idx += result.getValue1().size();
            } else {
                // Add the statement to the current split test case body
                addStatement(statement, splitTestCaseBody, blockStatementsType);
                // continue;
            }
            if ((isAssertion && config.splitStrategy() == SplitStrategyType.ASSERTION) || config.splitStrategy() == SplitStrategyType.STATEMENT) {
                if (splitTestCases.size() > 0) {
                    MethodDeclaration lastAddedSplitTestCase = splitTestCases.get(splitTestCases.size() - 1);
                    if(lastAddedSplitTestCase.getBody().get().equals(splitTestCaseBody)) {
                        continue;
                    }
                }
                // Initialize a new split test case, starting from the body of the previous one
                MethodDeclaration newSplitTestCase = initializeSplitTestCase(splitTestCase, testCasePrefixName, ++idx);

                // List<Comment> comments = newSplitTestCase.getAllContainedComments();
                // for (Comment comment : comments) {
                //     if (comment.isLineComment()) {
                //         String commentContent = comment.getContent();
                //         if (commentContent.equals(TestUtils.THROW_EXCEPTION_LABEL)) {
                //             newSplitTestCase.getBody().get().remove(comment.getParentNode().get());
                //         }
                //     }
                // }
                // Add the split test case to the list
                splitTestCases.add(splitTestCase);
                // Update the split test case with the new one
                splitTestCase = newSplitTestCase;
                // Get the body of the new split test case
                splitTestCaseBody = splitTestCase.getBody().orElseThrow();
            }
        }
        // Check if the number of split test cases generated is equal to the number of assertions in the original test case
        if ((numberOfAssertions != splitTestCases.size() && config.splitStrategy() == SplitStrategyType.ASSERTION)) {
            String errMsg = "Unexpected number of split test cases generated from test case " + testCasePrefixName + ". Expected: " + numberOfAssertions + ", Found: " + splitTestCases.size();
            if (config.numAssertionsMatchStrategy() == NumAssertionsMatchStrategyType.STRICT) {
                throw new IllegalStateException(errMsg);
            } else if (config.numAssertionsMatchStrategy() == NumAssertionsMatchStrategyType.LOOSE) {
                // Check if the number of assertions is greater than the number of split test cases
                logger.warn(errMsg);
            }
        }
        // Check if the current split test case has more statements than the last split test case added to the list
        if (splitTestCases.size() > 0 && recursionLevel == 0 && config.keepStatementsAfterLastAssertion()) {
            MethodDeclaration lastAddedSplitTestCase = splitTestCases.get(splitTestCases.size() - 1);
            if(!lastAddedSplitTestCase.getBody().get().equals(splitTestCaseBody)) {
                // Add the last split test case to the list
                splitTestCases.add(splitTestCase);
            }
        }
        // Return the list of split test cases generated
        return new Pair<>(splitTestCase, splitTestCases);
    }

    private static boolean parseNormalizeTestStatementsBlock(
            TypeDeclaration testClass,
            MethodDeclaration originalTestCase,
            List<Statement> statements,
            BlockStmt currentBlockStmt,
            HashMap<String, MethodDeclaration> auxiliaryMethods,
            List<MethodDeclaration> integratedAuxiliaryMethods
    ) throws IllegalStateException {
        boolean thrownedException = false;
        // Iterate over the statements of the original test case
        for (Statement statement : statements) {
            try {
                // Check if the statement is a potential assertion
                if (statement.isAssertStmt()) {
                    currentBlockStmt.addStatement(statement);
                } else if (statement.isBlockStmt()) {
                    // Parse the statements of the block and add them to the current block
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            statement.asBlockStmt().getStatements(),
                            currentBlockStmt,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else if (statement.isDoStmt()) {
                    // Parse the do statement
                    Statement doStmtBody = statement.asDoStmt().getBody();
                    // Clone the do statement and set the body to an empty block
                    DoStmt doStmtClone = statement.asDoStmt().clone();
                    BlockStmt doStmtCloneBody = new BlockStmt();
                    doStmtClone.setBody(doStmtCloneBody);
                    // Add the cloned do statement to the current block
                    currentBlockStmt.addStatement(doStmtClone);
                    // Parse the statements of the do body and add them to the cloned do statement
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(doStmtBody)),
                            doStmtCloneBody,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else if (statement.isExpressionStmt()) {
                    Expression exprStmt = statement.asExpressionStmt().getExpression();
                    if (exprStmt.isMethodCallExpr() && !(JUnitAssertionType.isJUnitAssertion(exprStmt.asMethodCallExpr().getNameAsString()))) {
                        try {
                            Optional<MethodDeclaration> auxiliaryMethodDeclaration = integrateAuxiliaryMethodIntoBlockStmt(testClass, originalTestCase, currentBlockStmt, exprStmt.asMethodCallExpr(), auxiliaryMethods);
                            if (auxiliaryMethodDeclaration.isPresent()) {
                                integratedAuxiliaryMethods.add(auxiliaryMethodDeclaration.get());
                            } else {
                                currentBlockStmt.addStatement(statement);
                            }
                        } catch (Exception e) {
                            // Log the error occurred while parsing the statements of the test case
                            logger.error(e.getMessage());
                            thrownedException = true;
                        }
                    } else if (exprStmt.isLambdaExpr()) {
                        LambdaExpr lambdaExpr = exprStmt.asLambdaExpr();
                        Statement lambdaBody = lambdaExpr.getBody();
                        LambdaExpr lambdaExprClone = lambdaExpr.clone();
                        BlockStmt lambdaBodyClone = new BlockStmt();
                        lambdaExprClone.setParameters(new NodeList<>(lambdaExpr.getParameters()));
                        lambdaExprClone.setBody(lambdaBodyClone);
                        ExpressionStmt lambdaExprStmt = new ExpressionStmt(lambdaExprClone);
                        currentBlockStmt.addStatement(lambdaExprStmt);
                        if (lambdaBody.isExpressionStmt()) {
                            try {
                                Optional<MethodDeclaration> auxiliaryMethodDeclaration = integrateAuxiliaryMethodIntoBlockStmt(testClass, originalTestCase, lambdaBodyClone, lambdaBody.asExpressionStmt().getExpression().asMethodCallExpr(), auxiliaryMethods);
                                if (auxiliaryMethodDeclaration.isPresent()) {
                                    integratedAuxiliaryMethods.add(auxiliaryMethodDeclaration.get());
                                } else {
                                    lambdaExprClone.setBody(lambdaBody.clone());
                                }
                            } catch (Exception e) {
                                // Log the error occurred while parsing the statements of the test case
                                logger.error(e.getMessage());
                                thrownedException = true;
                                lambdaExprClone.setBody(lambdaBody.clone());
                            }
                        } else {
                            // Parse the statements of the lambda body and add them to the cloned lambda expression
                            thrownedException = parseNormalizeTestStatementsBlock(
                                    testClass,
                                    originalTestCase,
                                    new ArrayList<>(Arrays.asList(lambdaBody)),
                                    lambdaBodyClone,
                                    auxiliaryMethods,
                                    integratedAuxiliaryMethods
                            );
                        }
                    } else {
                        currentBlockStmt.addStatement(statement);
                    }
                } else if (statement.isForEachStmt()) {
                    // Parse the for each statement and add its body to the current block
                    Statement forEachStmtBody = statement.asForEachStmt().getBody();
                    // Clone the for each statement and set the body to an empty block
                    ForEachStmt forEachStmtClone = statement.asForEachStmt().clone();
                    BlockStmt forEachCloneBody = new BlockStmt();
                    forEachStmtClone.setBody(forEachCloneBody);
                    // Add the cloned for each statement to the current block
                    currentBlockStmt.addStatement(forEachStmtClone);
                    // Parse the statements of the for each body and add them to the cloned for each statement
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(forEachStmtBody)),
                            forEachCloneBody,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else if (statement.isForStmt()) {
                    // Parse the for statement and split the test case into multiple test cases
                    Statement forStmtBody = statement.asForStmt().getBody();
                    // Clone the for statement and set the body to an empty block
                    ForStmt forStmtClone = statement.asForStmt().clone();
                    BlockStmt forStmtCloneBody = new BlockStmt();
                    forStmtClone.setBody(forStmtCloneBody);
                    // Add the cloned for statement to the current block
                    currentBlockStmt.addStatement(forStmtClone);
                    // Parse the statements of the for current body and add them to the cloned for statement
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(forStmtBody)),
                            forStmtCloneBody,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else if (statement.isIfStmt()) {
                    IfStmt ifStmt = statement.asIfStmt();
                    List<Pair<Optional<Expression>, Statement>> ifStmts = new ArrayList<>();
                    ifStmts.add(new Pair<>(Optional.of(ifStmt.getCondition()), ifStmt.getThenStmt()));
                    if (ifStmt.getElseStmt().isPresent()) {
                        Optional<Statement> elseIfStmt = ifStmt.getElseStmt();
                        while (elseIfStmt.isPresent()) {
                            if (elseIfStmt.get().isIfStmt()) {
                                ifStmts.add(new Pair<>(Optional.of(elseIfStmt.get().asIfStmt().getCondition()), elseIfStmt.get().asIfStmt().getThenStmt()));
                            } else {
                                ifStmts.add(new Pair<>(Optional.empty(), elseIfStmt.get()));
                            }
                            elseIfStmt = elseIfStmt.get().isIfStmt() ? elseIfStmt.get().asIfStmt().getElseStmt() : Optional.empty();
                        }
                    }
                    // Define if statement clone
                    IfStmt ifStmtClone = null;
                    for (Pair<Optional<Expression>, Statement> ifStmtPair : ifStmts) {
                        Optional<Expression> ifCondition = ifStmtPair.getValue0();
                        Statement ifStmtBody = ifStmtPair.getValue1();
                        BlockStmt ifStmtCloneBody = new BlockStmt();
                        if (ifCondition.isPresent()) {
                            if (ifStmtClone == null) {
                                ifStmtClone = new IfStmt(ifCondition.get(), ifStmtCloneBody, null);
                                // Add the cloned if statement to the current block
                                currentBlockStmt.addStatement(ifStmtClone);
                            } else {
                                IfStmt lastIfStmt = traverseIfElseStatement(ifStmtClone);
                                lastIfStmt.setElseStmt(new IfStmt(ifCondition.get(), ifStmtCloneBody, null));
                            }
                        } else {
                            IfStmt lastIfStmt = traverseIfElseStatement(ifStmtClone);
                            lastIfStmt.setElseStmt(ifStmtCloneBody);
                        }
                        // Parse the statements of the if body and split it into multiple test cases
                        thrownedException = parseNormalizeTestStatementsBlock(
                                testClass,
                                originalTestCase,
                                new ArrayList<>(Arrays.asList(ifStmtBody)),
                                ifStmtCloneBody,
                                auxiliaryMethods,
                                integratedAuxiliaryMethods
                        );
                        // Update the if statement clone
                        ifStmtClone = JavaParserUtils.getLastStatementTypeOccurrence(currentBlockStmt, IfStmt.class)
                                .orElseThrow(() -> new IllegalStateException(
                                        "No statement of type " + IfStmt.class + " found in the block of statements."
                                ))
                                .asIfStmt();
                    }
                } else if (statement.isSynchronizedStmt()) {
                    // Parse the synchronized statement and split the test case into multiple test cases
                    Statement synchronizedStmtBody = statement.asSynchronizedStmt().getBody();
                    // Clone the synchronized statement and set the body to an empty block
                    SynchronizedStmt synchronizedStmtClone = statement.asSynchronizedStmt().clone();
                    BlockStmt synchronizedStmtBodyClone = new BlockStmt();
                    synchronizedStmtClone.setBody(synchronizedStmtBodyClone);
                    // Add the cloned synchronized statement to the current block
                    currentBlockStmt.addStatement(synchronizedStmtClone);
                    // Parse the statements of the synchronized body and split it into multiple test cases
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(synchronizedStmtBody)),
                            synchronizedStmtBodyClone,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else if (statement.isSwitchStmt()) {
                    throw new UnsupportedOperationException("Switch statement not supported yet.");
                } else if (statement.isTryStmt()) {
                    // Convert the try/catch statement into a plain assertion (assertThrows)
                    // Get the try statement
                    TryStmt tryStmt = statement.asTryStmt();
                    // Parse the while statement and split the test case into multiple test cases
                    Statement tryStmtBody = tryStmt.getTryBlock();
                    // Clone the while statement and set the body to an empty block
                    TryStmt tryStmtClone = new TryStmt();
                    BlockStmt tryStmtBodyClone = new BlockStmt();
                    tryStmtClone.setTryBlock(tryStmtBodyClone);
                    NodeList<CatchClause> catchClausesClone = new NodeList<>();
                    tryStmtClone.setCatchClauses(catchClausesClone);
                    // Add the cloned while statement to the current block
                    currentBlockStmt.addStatement(tryStmtClone);
                    // Parse the statements of the try block and split it into multiple test cases
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(tryStmtBody)),
                            tryStmtBodyClone,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                    for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                        // Parse the statements of the catch block and split it into multiple test cases
                        Statement catchStmtBody = catchClause.getBody();
                        // Clone the catch statement and set the body to an empty block
                        CatchClause catchClauseClone = catchClause.clone();
                        BlockStmt catchStmtBodyClone = new BlockStmt();
                        catchClauseClone.setBody(catchStmtBodyClone);
                        catchClausesClone.add(catchClauseClone);
                        // Parse the statements of the catch body and split it into multiple test cases
                        thrownedException = parseNormalizeTestStatementsBlock(
                                testClass,
                                originalTestCase,
                                new ArrayList<>(Arrays.asList(catchStmtBody)),
                                catchStmtBodyClone,
                                auxiliaryMethods,
                                integratedAuxiliaryMethods
                        );
                    }
                    if (tryStmt.getFinallyBlock().isPresent()) {
                        // Add the cloned while statement to the current split test case body
                        BlockStmt finallyStmtBodyClone = new BlockStmt();
                        tryStmtClone.setFinallyBlock(finallyStmtBodyClone);
                        // Parse the statements of the try block and split it into multiple test cases
                        thrownedException = parseNormalizeTestStatementsBlock(
                                testClass,
                                originalTestCase,
                                new ArrayList<>(Arrays.asList(tryStmt.getFinallyBlock().get())),
                                finallyStmtBodyClone,
                                auxiliaryMethods,
                                integratedAuxiliaryMethods
                        );
                    }
                } else if (statement.isWhileStmt()) {
                    // Parse the while statement and split the test case into multiple test cases
                    Statement whileStmtBody = statement.asWhileStmt().getBody();
                    // Clone the while statement and set the body to an empty block
                    WhileStmt whileStmtClone = statement.asWhileStmt().clone();
                    BlockStmt whileStmtBodyClone= new BlockStmt();
                    whileStmtClone.setBody(whileStmtBodyClone);
                    // Add the cloned while statement to the current block
                    currentBlockStmt.addStatement(whileStmtClone);
                    // Parse the statements of the while body and split it into multiple test cases
                    thrownedException = parseNormalizeTestStatementsBlock(
                            testClass,
                            originalTestCase,
                            new ArrayList<>(Arrays.asList(whileStmtBody)),
                            whileStmtBodyClone,
                            auxiliaryMethods,
                            integratedAuxiliaryMethods
                    );
                } else {
                    // Add the statement to the current split test case body
                    currentBlockStmt.addStatement(statement);
                    // continue;
                }
            } catch (Exception e) {
                // Log the error occurred while parsing the statements of the test case
                logger.error(e.getMessage());
                thrownedException = true;
                currentBlockStmt.addStatement(statement);
            }
        }
        // Return if an exception occurred while parsing the statements of the test case
        return thrownedException;
    }

    /**
     * Given a BlockStmt representing the body of a method, and the mapping of the parameters to replace in the block of
     * statements, replace the parameters with the actual arguments in the block of statements.
     * @param blockStmt the body of the method to process
     * @param paramMapping the mapping of the parameters to replace in the block of statements
     */
    private static void replaceParams(BlockStmt blockStmt, Map<String, Expression> paramMapping) {
        NameExprVisitor nameExprCollector = new NameExprVisitor();
        List<NameExpr> nameExprs = nameExprCollector.visit(blockStmt);
        nameExprs.forEach(nameExpr -> {
            String varName = nameExpr.getNameAsString();
            if (paramMapping.containsKey(varName)) {
                nameExpr.replace(paramMapping.get(varName).clone());
            }
        });
    }

    /**
     * Given a BlockStmt representing the body of a method, and the mapping of the variables to replace in the block of
     * statements, replace the variables with the renamed names in the block of statements.
     * @param blockStmt the body of the method to process
     * @param varMapping the mapping of the variables to replace in the block of statements
     */
    private static void replaceVariables(BlockStmt blockStmt, Map<String, String> varMapping) {
        // Rename variable declarations in the block of statements
        VariableDeclarationExprVisitor varDeclarationExprCollector = new VariableDeclarationExprVisitor();
        List<VariableDeclarationExpr> varDeclarationExprs = varDeclarationExprCollector.visit(blockStmt);
        varDeclarationExprs.forEach(varDecl -> {
            varDecl.getVariables().forEach(var -> {
                String varName = var.getNameAsString();
                if (varMapping.containsKey(varName)) {
                    var.setName(varMapping.get(varName));
                }
            });
        });
        // Rename variable usages in the block of statements
        NameExprVisitor nameExprCollector = new NameExprVisitor();
        List<NameExpr> nameExprs = nameExprCollector.visit(blockStmt);
        nameExprs.forEach(nameExpr -> {
            String varName = nameExpr.getNameAsString();
            if (varMapping.containsKey(varName)) {
                nameExpr.setName(varMapping.get(varName));
            }
        });
    }

    private static Optional<MethodDeclaration> integrateAuxiliaryMethodIntoBlockStmt(TypeDeclaration testClass, MethodDeclaration originalTestCase, BlockStmt currentBlockStmt, MethodCallExpr auxiliaryMethodInvoked, HashMap<String, MethodDeclaration> auxiliaryMethods) {
        String methodSignature = "";

        try {
            Optional<Node> resolvedMethod = auxiliaryMethodInvoked.resolve().toAst();
            if (resolvedMethod.isPresent()) {
                methodSignature = ((MethodDeclaration) resolvedMethod.get()).getSignature().asString();
            }
        } catch (UnsolvedSymbolException e) {
            CallableDeclaration callableDeclaration = JavaParserUtils.searchCandidateCallableDeclaration(testClass, auxiliaryMethodInvoked, CallableExprType.METHOD);
            if (callableDeclaration != null) {
                methodSignature = callableDeclaration.getSignature().asString();
            }
        }
        if (auxiliaryMethods.containsKey(methodSignature)) {
            MethodDeclaration auxiliaryMethodDeclaration = auxiliaryMethods.get(methodSignature);
            BlockStmt auxBody = auxiliaryMethodDeclaration.getBody().get();
            ReturnStmtVisitor returnStmtCollector = new ReturnStmtVisitor();
            List<ReturnStmt> returnStmts = returnStmtCollector.visit(auxBody);
            if (returnStmts.size() > 1) {
                logger.error("Multiple return statements in the auxiliary method.");
                return Optional.empty();
            }
            if (returnStmts.size() == 1) {
                ReturnStmt returnStmt = returnStmts.get(0);
                Optional<Expression> returnExpr = returnStmt.getExpression();
                if (returnExpr.isEmpty()) {
                    logger.error("Empty return statement in the auxiliary method.");
                    return Optional.empty();
                }
                if (!(auxBody.getStatements().getLast().get() == returnStmt)) {
                    logger.error("Return statement not at the end of the auxiliary method.");
                    return Optional.empty();
                }
                auxBody.getStatements().remove(returnStmt);
                ExpressionStmt returnExprStmt = new ExpressionStmt(returnExpr.get());
                auxBody.addStatement(returnExprStmt);
            }
            // Clone and modify the auxiliary method body to replace variables
            BlockStmt clonedAuxBody = auxBody.clone();
            // Map parameters from the method call to actual arguments
            Map<String, Expression> paramMapping = new HashMap<>();
            // Map variables defined both in the auxiliary method and in the main method
            Map<String, String> varMapping = new HashMap<>();
            List<Expression> arguments = auxiliaryMethodInvoked.getArguments();
            List<Parameter> parameters = auxiliaryMethodDeclaration.getParameters();
            // Check that the number of invocation arguments and the declaration parameters coincide
            if (arguments.size() != parameters.size()) {
                logger.error("Parameter mismatch between method call and auxiliary method.");
                return Optional.empty();
            }
            for (int i = 0; i < parameters.size(); i++) {
                paramMapping.put(parameters.get(i).getNameAsString(), arguments.get(i));
            }
            // Map the original variables containing the same name of the variables
            // defined in the auxiliary method
            VariableDeclarationExprVisitor varDeclarationExprCollector = new VariableDeclarationExprVisitor();
            List<VariableDeclarationExpr> auxVarDeclarationExprs = varDeclarationExprCollector.visit(auxBody);
            List<VariableDeclarationExpr> mainVarDeclarationExprs = varDeclarationExprCollector.visit(originalTestCase);
            mainVarDeclarationExprs.forEach(varDecl ->
                    varDecl.getVariables().forEach(var -> {
                        auxVarDeclarationExprs.forEach(auxVarDecl ->
                                auxVarDecl.getVariables().forEach(auxVar -> {
                                    if (var.getNameAsString().equals(auxVar.getNameAsString())) {
                                        varMapping.put(var.getNameAsString(), var.getNameAsString() + "_aux");
                                    }
                                })
                        );
                    })
            );
            replaceParams(clonedAuxBody, paramMapping);
            replaceVariables(clonedAuxBody, varMapping);
            // Add the cloned auxiliary method body to the main method body
            currentBlockStmt.getStatements().addAll(clonedAuxBody.getStatements());
            return Optional.of(auxiliaryMethodDeclaration);
        }
        return Optional.empty();
    }

    /**
     * Traverse the if-else chain of an if-statement from the root up to the last if-else condition
     *
     * @param rootIfStmt the root if-statement
     * @return the last if-statement in the if-else chain
     */
    private static IfStmt traverseIfElseStatement(IfStmt rootIfStmt) {
        IfStmt lastIfStmt = rootIfStmt;
        while (lastIfStmt.getElseStmt().isPresent()) {
            lastIfStmt = lastIfStmt.getElseStmt().get().asIfStmt();
        }
        return lastIfStmt;
    }

    /**
     * Add the catch clause to the try statement in the given splitTestCaseBody.
     *
     * @param catchClause the catch clause to add
     * @param splitTestCaseBody the split test case body where is present the try statement
     */
    private static void addCatchClause(
            CatchClause catchClause,
            BlockStmt splitTestCaseBody
    ) {
        Class blockStmtClass = BlockStatementsType.TRY.getJavaParserStmtClass();
        Statement lastStatement = JavaParserUtils.getLastStatementTypeOccurrence(splitTestCaseBody, blockStmtClass)
                .orElseThrow(() -> new IllegalStateException(
                        "No statement of type " + blockStmtClass + " found in the block of statements."
                ));
        lastStatement.asTryStmt().getCatchClauses().add(catchClause);
    }

    /**
     * Add the statement to the split test case body, according to the type of block statement under analysis.
     * If the block statement is a method body, it refers to the main block of code and the statement is added
     * to the split test case body. Otherwise, the statement is added to the last block statement of the split
     * test case body (the one under analysis).
     *
     * @param statement
     * @param splitTestCaseBody
     * @param blockStatementsType
     */
    private static void addStatement(
            Statement statement,
            BlockStmt splitTestCaseBody,
            BlockStatementsType blockStatementsType
    ) {
        // statement.addOrphanComment(TestUtils.MASK_COMMENT);
        if (blockStatementsType == BlockStatementsType.METHOD_BODY) {
            splitTestCaseBody.addStatement(statement);
        } else {
            Class blockStmtClass = blockStatementsType.getJavaParserStmtClass();
            Statement lastStatement = JavaParserUtils.getLastStatementTypeOccurrence(splitTestCaseBody, blockStmtClass)
                    .orElseThrow(() -> new IllegalStateException(
                            "No statement of type " + blockStmtClass + " found in the block of statements."
                    ));
            BlockStmt body;
            switch (blockStatementsType) {
                case DO:
                    body = (BlockStmt) lastStatement.asDoStmt().getBody();
                    body.addStatement(statement);
                    return;
                case FOR_EACH:
                    body = (BlockStmt) lastStatement.asForEachStmt().getBody();
                    body.addStatement(statement);
                    return;
                case FOR:
                    body = (BlockStmt) lastStatement.asForStmt().getBody();
                    body.addStatement(statement);
                    return;
                case LAMBDA:
                    lastStatement.findAll(LambdaExpr.class).get(0).asLambdaExpr().getBody().asBlockStmt().addStatement(statement);
                    return;
                case IF:
                    body = lastStatement.asIfStmt().getThenStmt().asBlockStmt();
                    body.addStatement(statement);
                    return;
                case ELSE:
                    IfStmt ifStmt = lastStatement.asIfStmt();
                    while (ifStmt.getElseStmt().isPresent()) {
                        Statement stmt = ifStmt.getElseStmt().get();
                        if (stmt.isIfStmt()) {
                            ifStmt = stmt.asIfStmt();
                        } else {
                            body = stmt.asBlockStmt();
                            body.addStatement(statement);
                            return;
                        }
                    }
                    body = ifStmt.getThenStmt().asBlockStmt();
                    body.addStatement(statement);
                    return;
                case TRY:
                    body = lastStatement.asTryStmt().getTryBlock();
                    body.addStatement(statement);
                    return;
                case CATCH:
                    body = lastStatement.asTryStmt().getCatchClauses().getLast().get().getBody();
                    body.addStatement(statement);
                    return;
                case FINALLY:
                    if (!lastStatement.asTryStmt().getFinallyBlock().isPresent()) {
                        if (statement.isBlockStmt()) {
                            lastStatement.asTryStmt().setFinallyBlock(statement.asBlockStmt());
                            return;
                        }
                        throw new IllegalStateException("Unexpected finally block statement");
                    }
                    body = lastStatement.asTryStmt().getFinallyBlock().get();
                    body.addStatement(statement);
                    return;
                case SWITCH:
                    SwitchStmt switchStmt = lastStatement.asSwitchStmt();
                    SwitchEntry lastSwitchEntry = switchStmt.getEntry(switchStmt.getEntries().size() - 1);
                    lastSwitchEntry.getStatements().add(statement);
                    return;
                case WHILE:
                    body = (BlockStmt) lastStatement.asWhileStmt().getBody();
                    body.addStatement(statement);
                    return;
                default:
                    throw new IllegalStateException("Unexpected block statement type");
            }
        }
    }

    /**
     * Get the number of assertions in the list of statements.
     *
     * @param statements the list of statements
     * @return the number of assertions
     */
    private static int getNumberOfAssertions(List<Statement> statements) {
        int assertionCounter = 0;
        for (Statement statement : statements) {
            MethodCallExprVisitor methodCallExprCollector = new MethodCallExprVisitor();
            List<MethodCallExpr> methodCallExprs = methodCallExprCollector.visit(statement);
            for(MethodCallExpr methodCallExpr : methodCallExprs) {
                if (JUnitAssertionType.isJUnitAssertion(methodCallExpr.getNameAsString())) {
                    assertionCounter++;
                } else {
                    // TODO: Check if the statement is a method call expression that refers to an auxiliary method
                    //       (defined or inherited) with assertions
                }
            }
            AssertStmtVisitor assertStmtCollector = new AssertStmtVisitor();
            List<AssertStmt> assertStmts = assertStmtCollector.visit(statement);
            assertionCounter += assertStmts.size();
        }
        return assertionCounter;
    }

    private static HashMap<String,Integer> getAssertionsDistribution(List<Statement> statements) {
        HashMap<String, Integer> assertionDistribution = new HashMap<>();
        for (JUnitAssertionType assertionType : JUnitAssertionType.values()) {
            assertionDistribution.put(assertionType.getAssertionMethodName(), 0);
        }

        for (Statement statement : statements) {
            MethodCallExprVisitor methodCallExprCollector = new MethodCallExprVisitor();
            List<MethodCallExpr> methodCallExprs = methodCallExprCollector.visit(statement);
            for(MethodCallExpr methodCallExpr : methodCallExprs) {
                if (JUnitAssertionType.isJUnitAssertion(methodCallExpr.getNameAsString())) {
                    assertionDistribution.put(methodCallExpr.getNameAsString(), assertionDistribution.get(methodCallExpr.getNameAsString()) + 1);
                } else {
                    // TODO: Check if the statement is a method call expression that refers to an auxiliary method
                    //       (defined or inherited) with assertions
                }
            }
            AssertStmtVisitor assertStmtCollector = new AssertStmtVisitor();
            List<AssertStmt> assertStmts = assertStmtCollector.visit(statement);
            assertionDistribution.put("assert", assertStmts.size());
        }
        return assertionDistribution;
    }

    /**
     * Check that the try statement respect the rules for the split test cases.
     * The rules are:
     * <ul>
     *     <li>Only one catch clause is allowed, if the fail() assertion is in the try statement (if you test a method,
     *     you expect only a specific exception to be thrown under a particular condition)</li>
     *     <li>If the {@code TryStmt} contains a catch clause, the exception captured by the catch clause must be unique
     *     (no union type are admitted)</li>
     *     <li>If the {@code TryStmt} contains a catch clause, the last statement in the try block must be a method call
     *     or an object creation invocation</li>
     * </ul>
     *
     *
     * @param testCaseName the name of the test case
     * @param tryStmt the try statement to check
     * @return the list of split test cases
     */
    private static void checkTryStmt(String testCaseName, TryStmt tryStmt) {
        // Get the list of catch clauses from the try block
        NodeList<CatchClause> catchesClauses = tryStmt.getCatchClauses();
        // Get the list of statements from the try block
        List<Statement> tryStmtStatements = tryStmt.getTryBlock().getStatements();
        Optional<BlockStatementsType> failPositionAssertion = findFailMethodCall(tryStmt);
        // Check the number of catch clauses (only one is allowed if the fail assertion is present in the try block)
        if (failPositionAssertion.isPresent()) {
            if (catchesClauses.size() > 1 && failPositionAssertion.get() == BlockStatementsType.TRY) {
                throw new IllegalStateException("Unexpected number of catch clauses in try statement within test case " + testCaseName);
            }
        }
        // Check the last statement in the try block (must be a method call, an object creation invocation or a fail
        // assertion)
        if (catchesClauses.size() == 1) {
            if (catchesClauses.get(0).getParameter().getType().getClass().equals(UnionType.class)) {
                throw new IllegalStateException("Union type in catch clause in try statement within test case " + testCaseName);
            }
            if (tryStmtStatements.size() == 0) {
                throw new IllegalStateException("No statements found in try block of try statement within test case " + testCaseName);
            }
            Statement lastTryStmtStatement = tryStmtStatements.get(tryStmtStatements.size() - 1);
            if (!lastTryStmtStatement.isExpressionStmt()) {
                throw new IllegalStateException("Last statement in try block of try statement within test case " + testCaseName + " is not an expression statement");
            }
            if (!StmtVisitorHelper.getLastExprInStmt(lastTryStmtStatement, MethodCallExpr.class).isPresent() && !StmtVisitorHelper.getLastExprInStmt(lastTryStmtStatement, ObjectCreationExpr.class).isPresent()) {
                throw new IllegalStateException("Last statement in try block of try statement within test case " + testCaseName + " is not a method call or object creation expression");
            }
        }
        // All checks passed
    }

    /**
     * Flatten a try statement into a block statement. If a catch clause is present, it is converted into an assertThrows
     * statement. The statements within the catch block are added to the flat try block. If a finally block is present,
     * the statements within it are added to the flat try block.
     *
     * Preconditions:
     * <ul>
     *     <li>Only one catch clause is allowed</li>
     *     <li>If the {@code TryStmt} contains a catch clause, the last statement in the try block must be a fail() method call</li>
     * </ul>
     *
     * @param junitVersion the version of JUnit used in the test class
     * @param tryStmt the try statement to flatten
     * @return the flat try block
     */
    private static BlockStmt flatTryStmt(JUnitVersion junitVersion, TryStmt tryStmt) {
        BlockStmt flatTryStmt = new BlockStmt();
        // Get the list of statements from the try block
        List<Statement> statements = new ArrayList<>(tryStmt.getTryBlock().getStatements());
        // Check if the try statement contains a catch clause, in which case collect the statements within it
        if (tryStmt.getCatchClauses().size() == 1) {
            // Convert catch clause into an assertThrows statement
            Optional<Statement> assertThrowsStmt = generateAssertThrowsFromTryStmt(junitVersion, tryStmt);
            if (assertThrowsStmt.isPresent()) {
                // Remove the method called added to the assertion
                statements.remove(statements.size() - 2);
                // Remove the fail() method call at the end of the try block
                statements.remove(statements.size() - 1);
                // Add the assertThrows statement to the flat try block
                statements.add(assertThrowsStmt.get());
                // Add the statements from the catch block to the flat try block
                statements.addAll(tryStmt.getCatchClauses().get(0).getBody().getStatements());
            }
        }
        // Check if the try statement contains a finally block, in which case collect the statements within it
        if (tryStmt.getFinallyBlock().isPresent()) {
            statements.addAll(tryStmt.getFinallyBlock().get().getStatements());
        }
        // Iterate over the statements in the try block
        for (Statement statement : statements) {
            // Add the statement to the flat try block
            flatTryStmt.addStatement(statement);
        }
        // Return the flat try block
        return flatTryStmt;
    }

    /**
     * Generate an assertThrows statement from a try statement. The assertThrows statement is created using the exception
     * class from the catch clause and the last method call in the try block. If the last method call is a fail() method
     * call, the message of the fail() method is used as the message of the assertThrows statement.
     *
     * @param junitVersion the version of JUnit used in the test class
     * @param tryStmt the try statement to generate the assertThrows statement from
     * @return the assertThrows statement, if the fail() method call is found in the try block. An empty optional otherwise.
     */
    private static Optional<Statement> generateAssertThrowsFromTryStmt(JUnitVersion junitVersion, TryStmt tryStmt) {
        // Get catch clause
        CatchClause catchClause = tryStmt.getCatchClauses().get(0);
        // Get the exception class from the catch clause
        com.github.javaparser.ast.type.Type exceptionClass = catchClause.getParameter().getType();
        // Find the position of the fail method call in the try-catch block
        Optional<BlockStatementsType> failAssertionPosition = findFailMethodCall(tryStmt);

        if (failAssertionPosition.isPresent() && failAssertionPosition.get() == BlockStatementsType.TRY) {
            if (tryStmt.getTryBlock().getStatements().size() < 2) {
                throw new IllegalStateException("Unexpected fail method call as unique statement in try block");
            }
            // Get the last method call in the try block it is considered the method that must throw the
            // exception. The last call is usually the fail() method (if the expected exception
            // is not thrown by the focal method it makes the test fail)
            int tryStmtSize = tryStmt.getTryBlock().getStatements().size();
            List<Statement> statements = tryStmt.getTryBlock().getStatements();
            Statement lastMethodCallStatement = statements.get(tryStmtSize - 2);
            Expression lastMethodCall = lastMethodCallStatement.asExpressionStmt().getExpression();
            Statement failMethodCallStatement = statements.get(tryStmtSize - 1);
            MethodCallExpr failMethodCall = failMethodCallStatement.asExpressionStmt().getExpression().asMethodCallExpr();
            String failMessage = "";
            // Check if the last method call (fail method) has a comment. If so, use it as the fail message
            if (failMethodCall.getArguments().size() > 0) {
                if (failMethodCall.getArguments().get(0).isStringLiteralExpr()) {
                    // The replaceAll method is used to escape double quotes in the fail message (not escaped by the asString() method)
                    failMessage = failMethodCall.getArguments().get(0).asStringLiteralExpr().asString().replaceAll("\"", "\\\\\"");
                }
            }
            // Check if the last method call is an assignment or a variable declaration
            if (lastMethodCall.isAssignExpr()) {
                lastMethodCall = lastMethodCall.asAssignExpr().getValue();
            } else if (lastMethodCall.isVariableDeclarationExpr()) {
                lastMethodCall = lastMethodCall.asVariableDeclarationExpr().getVariables().get(0).getInitializer().get();
            }
            LambdaExpr lambdaExpr;
            if (lastMethodCall.isMethodCallExpr() && JUnitAssertionType.isJUnitAssertion(lastMethodCall.asMethodCallExpr().getNameAsString()) && lastMethodCall.asMethodCallExpr().getNameAsString().equals("assertThrows")) {
                BlockStmt lambdaBody = new BlockStmt();
                ThrowStmt throwStmt = new ThrowStmt(lastMethodCall);
                lambdaBody.addStatement(throwStmt);
                lambdaExpr = new LambdaExpr(new NodeList<>(), lambdaBody, true);
            } else {
                lambdaExpr = new LambdaExpr(new NodeList<>(), new ExpressionStmt(lastMethodCall), true);
            }
            // Create the assertThrows statement
            MethodCallExpr assertThrowsCall = new MethodCallExpr(JUnitAssertionType.ASSERT_THROWS.getAssertionMethodName());
            // Add the arguments to the assertThrows statement (exception class and last method call)
            NodeList<Expression> arguments = new NodeList<>();
            arguments.add(new ClassExpr(exceptionClass));
            arguments.add(lambdaExpr);
            if (!failMessage.isEmpty()) {
                if (junitVersion == JUnitVersion.JUNIT4) {
                    arguments.addFirst(new StringLiteralExpr(failMessage));
                } else if (junitVersion == JUnitVersion.JUNIT5) {
                    arguments.add(new StringLiteralExpr(failMessage));
                }
            }
            assertThrowsCall.setArguments(arguments);
            VariableDeclarator variableDeclarator = new VariableDeclarator(
                    new ClassOrInterfaceType(null, exceptionClass.asString()),
                    "e" + variableID++,
                    assertThrowsCall
            );
            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
            // Create an expression statement for the assertThrows call
            Statement assertStmt = new ExpressionStmt(variableDeclarationExpr);
            return Optional.of(assertStmt);
        }
        return Optional.empty();
    }

    /**
     * Check if the statement is a fail method call.
     *
     * @param statement the statement to check
     * @return {@code true} if the statement is a fail method call, {@code false} otherwise
     */
    private static boolean isFailMethodCall(Statement statement) {
        if (statement.isExpressionStmt()) {
            Expression expr = statement.asExpressionStmt().getExpression();
            if (expr.isMethodCallExpr()) {
                MethodCallExpr methodCallExpr = expr.asMethodCallExpr();
                if (methodCallExpr.getNameAsString().equals("fail")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find the fail method call in the try statement. The method returns {@code BlockStatementsType.TRY} if the fail
     * method call is found in the try block, {@code BlockStatementsType.CATCH} if the fail method call is found in the
     * catch block. If the fail method is not found an {@code IllegalStateException} is thrown. The method assumes that
     * the try-catching block contains only one catch clause.
     *
     * @param tryStmt the try statement to check
     * @return the block statements type where the fail method call is found, if present. An empty optional otherwise.
     */
    private static Optional<BlockStatementsType> findFailMethodCall(TryStmt tryStmt) {
        List<Statement> tryStatements = tryStmt.getTryBlock().getStatements();
        boolean failFound = tryStatements.stream().anyMatch(s -> isFailMethodCall(s));
        if (failFound) {
            return Optional.of(BlockStatementsType.TRY);
        }
        for (CatchClause catchClause : tryStmt.getCatchClauses()) {
            List<Statement> catchStatements = catchClause.getBody().getStatements();
            failFound = catchStatements.stream().anyMatch(s -> isFailMethodCall(s));
            if (failFound) {
                return Optional.of(BlockStatementsType.CATCH);
            }
        }
        return Optional.empty();
    }

    /**
     * Process the test class of a given repository to generate all the oracles datapoints {@link TestClazzOracleDatapoints}
     * from its test cases.
     *
     * @param config the configuration of the oracles dataset
     * @param testFilePath the path to the test class to process
     * @param sourceFilePath the path to the corresponding source file, if available
     * @return the list of oracle datapoints generated from the test cases of the test class and a hashmap containing
     * the logs of the errors encountered when processing each test cases (which invoked methods cannot be processed
     * in each test case of the test class)
     */
    public static Pair<TestClazzOracleDatapoints, HashMap<String, HashMap<String, List<String>>>> processSplitTestClass(
            OraclesDatasetConfig config,
            Path testFilePath,
            Path sourceFilePath
    ) {
        // Create a list to store the oracle datapoints
        List<OracleDatapoint> oracleDatapoints = new ArrayList<>();
        // Create a map to store the processing errors of the test cases (which invoked methods cannot be processed in
        // each test case of the test class)
        HashMap<String, HashMap<String, List<String>>> testsProcessingErrors = new HashMap<>();
        // Instantiate a TestClazzOracleDatapoints builder
        TestClazzOracleDatapointsBuilder testClassOracleDatapointsBuilder = new TestClazzOracleDatapointsBuilder();
        // Process the test class and generate the corresponding oracles datapoints
        try {
            // Parse the test class and focal classes
            CompilationUnit cuTestClass = JavaParserUtils.getCompilationUnit(testFilePath);
            CompilationUnit cuFocalClass = JavaParserUtils.getCompilationUnit(sourceFilePath);
            // Get the primary type of the test and focal classes
            TypeDeclaration testClass = cuTestClass.getPrimaryType().orElseThrow(() -> new IllegalStateException("Primary type not found in test class: " + testFilePath));
            TypeDeclaration focalClass = cuFocalClass.getPrimaryType().orElseThrow(() -> new IllegalStateException("Primary type not found in focal class: " + sourceFilePath));
            // Get junit version used in the test class
            JUnitVersion junitVersion = TestUtils.getJunitVersion(cuTestClass.getImports());
            // Get the path of the source directory of the repository
            Path repoSourcePath = Path.of(sourceFilePath.toString().replace(FilesUtils.getFQNPath((String) focalClass.getFullyQualifiedName().get()).toString(), ""));
            // Distribute the methods of the class according to their meaning (auxiliary, setup & tear down, test cases)
            Triplet<List<MethodDeclaration>, HashMap<String, MethodDeclaration>, HashMap<String, MethodDeclaration>> distributedMethods = distributeMethods(testClass.getMethods());
            // Set the list of all the test cases defined in the given test class
            List<MethodDeclaration> testCases = distributedMethods.getValue0();
            // Set the list of all the setup & tear down methods defined in the given test class
            HashMap<String, MethodDeclaration> setupTearDownMethods = distributedMethods.getValue1();
            // Set the list of all the auxiliary methods defined in the given test class
            HashMap<String, MethodDeclaration> auxiliaryMethods = distributedMethods.getValue2();
            // Process test class
            TestClazz testClassTracto = processTestClass(testClass, testFilePath, distributedMethods);
            // Process focal class
            Clazz focalClazz = processFocalClass(focalClass, sourceFilePath);
            testClassOracleDatapointsBuilder.setJunitVersion(junitVersion.getVersion());
            testClassOracleDatapointsBuilder.setTestClass(testClassTracto);
            testClassOracleDatapointsBuilder.setFocalClass(focalClazz);
            // Process test cases
            for (MethodDeclaration testCase : testCases) {
                Statement tgtStatement;

                List<Comment> comments = testCase.getAllContainedComments();

                for (Comment comment : comments) {
                    if (comment.isBlockComment()) {
                        String commentContent = comment.getContent();
                        if (commentContent.equals(TestUtils.FAKE_ELEMENT_LABEL)) {
                            comment.getCommentedNode().get().remove();
                        }
                    }
                }

                if (config.splitStrategy() == SplitStrategyType.ASSERTION) {
                    // Get the assertion statement in the test case
                    Optional<Statement> lastAssertion = JavaParserUtils.getLastAssertionInMethodDeclaration(testCase);
                    if (lastAssertion.isEmpty()) {
                        logger.error("No assertion found in test case: " + testCase.getNameAsString());
                        continue;
                    }
                    tgtStatement = lastAssertion.get();
                } else {
                    List<Statement> statements = testCase.findAll(Statement.class);
                    tgtStatement = statements.get(statements.size() - 1);
                }

                // Remove all comments from the target statement
                tgtStatement.getAllContainedComments().forEach(Node::remove);
                tgtStatement.getOrphanComments().forEach(Node::remove);
                tgtStatement.getComment().ifPresent(Node::remove);

                String target = config.noAssertionTarget();

                if (config.targetStrategy() == TargetStrategyType.ASSERTION) {
                    if (config.splitStrategy() == SplitStrategyType.ASSERTION) {
                        target = tgtStatement.toString().replaceAll("(?<![a-zA-Z0-9])Assert\\.", "").replaceAll("(?<![a-zA-Z0-9])Assertions\\.", "");
                        if (tgtStatement.isAssertStmt()) {
                            AssertStmt assertStmt = tgtStatement.asAssertStmt();
                            if (assertStmt.getCheck().isStringLiteralExpr()) {
                                StringLiteralExpr stringLiteralExpr = assertStmt.getCheck().asStringLiteralExpr();
                                if (stringLiteralExpr.getValue().equals(TestUtils.FAKE_ELEMENT_LABEL)) {
                                    target = THROW_EXCEPTION_LABEL;
                                }
                            }
                        }
                    } else if (config.splitStrategy() == SplitStrategyType.STATEMENT) {
                        Optional<Statement> lastAssertion = JavaParserUtils.getLastAssertionInMethodDeclaration(testCase);
                        if (lastAssertion.isPresent()) {
                            if (lastAssertion.get().equals(tgtStatement)) {
                                target = tgtStatement.toString().replaceAll("(?<![a-zA-Z0-9])Assert\\.", "").replaceAll("(?<![a-zA-Z0-9])Assertions\\.", "");
                            }
                        }
                    }
                } else if (config.targetStrategy() == TargetStrategyType.STATEMENT) {
                    target = tgtStatement.toString();
                }
                // Get the parent block statement of the target statement
                Node parentNode = tgtStatement.getParentNode().orElseThrow(() -> new IllegalStateException("Parent statement not found"));
                if (config.splitStrategy() == SplitStrategyType.ASSERTION && config.targetStrategy() == TargetStrategyType.ASSERTION) {
                    if (!(config.assertionStrategy() == AssertionStrategyType.KEEP)) {
                        // Remove target statement from the test case
                        tgtStatement.remove();
                    }
                } else if (config.splitStrategy() == SplitStrategyType.STATEMENT && config.targetStrategy() == TargetStrategyType.STATEMENT) {
                    if (!(config.statementStrategy() == StatementStrategyType.KEEP)) {
                        // Remove target statement from the test case
                        tgtStatement.remove();
                    }
                    // TODO: MANAGE CONDITIONS
                } else if (config.splitStrategy() == SplitStrategyType.STATEMENT && config.targetStrategy() == TargetStrategyType.ASSERTION) {
                    if (!(config.assertionStrategy() == AssertionStrategyType.KEEP)) {
                        // Remove target statement from the test case
                        tgtStatement.remove();
                    }
                }
                Pair<Callable,List<Callable>> invokedMethodsPair = processInvokedMethods(repoSourcePath, focalClass, testClass, testCase, testsProcessingErrors, config);
                if (config.splitStrategy() == SplitStrategyType.ASSERTION && config.assertionStrategy() == AssertionStrategyType.MASK) {
                    // Mask the target statement
                    if (parentNode instanceof BlockStmt) {
                        parentNode.addOrphanComment(new BlockComment(config.mask()));
                    } else {
                        if (tgtStatement instanceof BlockStmt) {
                            tgtStatement.addOrphanComment(new BlockComment(config.mask()));
                        }
                    }
                }
                if (config.splitStrategy() == SplitStrategyType.STATEMENT && config.targetStrategy() == TargetStrategyType.STATEMENT && config.statementStrategy() == StatementStrategyType.MASK) {
                    // Mask the target statement
                    if (parentNode instanceof BlockStmt) {
                        parentNode.addOrphanComment(new BlockComment(config.mask()));
                    } else {
                        if (tgtStatement instanceof BlockStmt) {
                            tgtStatement.addOrphanComment(new BlockComment(config.mask()));
                        }
                    }
                } else if (config.splitStrategy() == SplitStrategyType.STATEMENT && config.targetStrategy() == TargetStrategyType.ASSERTION && config.assertionStrategy() == AssertionStrategyType.MASK) {
                    if (parentNode instanceof BlockStmt) {
                        parentNode.addOrphanComment(new BlockComment(config.mask()));
                    } else {
                        if (tgtStatement instanceof BlockStmt) {
                            tgtStatement.addOrphanComment(new BlockComment(config.mask()));
                        }
                    }
                }
                // TODO: Focal method excluded from the analysis for the moment
                Callable focalMethodTracto = invokedMethodsPair.getValue0();
                List<Callable> invokedMethods = invokedMethodsPair.getValue1();
                Callable testPrefix = processCallable(testClass, testCase, invokedMethods);
                // TODO: Check id the last statement in the test case is an assertion or not
                // Removed any occurrence of `Assert.` in the body of the method (not useful for the oracle generation)
                oracleDatapoints.add(new OracleDatapoint(testPrefix, target));
            }
        } catch (IOException e) {
            logger.error("Error reading file: " + testFilePath);
        } catch (IllegalStateException e) {
            logger.error(e.getMessage());
        }
        // Set the list of oracle datapoints generated from the test cases of the test class
        testClassOracleDatapointsBuilder.setDatapoints(oracleDatapoints);
        return new Pair<>(testClassOracleDatapointsBuilder.build(), testsProcessingErrors);
    }

    /**
     * Extract the information about the fields defined in the type declaration (class) passed as input.
     * @param typeDeclaration the type declaration (class) to analyze
     * @return the list of {@link Field}, providing information about each field defined in the compilation unit
     */
    private static List<Field> processFieldsFromTypeDeclaration(TypeDeclaration typeDeclaration) {
        List<Field> fields = new ArrayList<>();
        FieldBuilder fieldBuilder = new FieldBuilder();

        FieldDeclarationVisitor fieldDeclarationCollector = new FieldDeclarationVisitor();
        List<FieldDeclaration> fieldDeclarations = fieldDeclarationCollector.visit(typeDeclaration);

        for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
            // Get field modifiers
            List<String> modifiers = JavaParserUtils.getModifiersAsStrings(fieldDeclaration.getModifiers());
            // Analyze the single varibles within the given field declaration
            List<VariableDeclarator> variables = fieldDeclaration.getVariables();
            for (VariableDeclarator variable : variables) {
                // Set identifier
                fieldBuilder.setIdentifier(variable.getNameAsString());
                // Set signature
                String signature = JavaParserUtils.getSignatureFromVariableDeclarator(modifiers, variable);
                fieldBuilder.setSignature(signature);
                // Set declarator
                fieldBuilder.setDeclarator(variable.toString());
                // Set field modifiers
                fieldBuilder.setModifiers(modifiers);
                // Analyze the type of the variable
                com.github.javaparser.ast.type.Type varType = variable.getType();
                Type type = processType(typeDeclaration, varType, 0);
                fieldBuilder.setType(type);
                fields.add(fieldBuilder.build());
                fieldBuilder.reset();
            }
        }
        return fields;
    }

    /**
     * Handle a type (that can be primitive, reference or void) and generate the corresponding {@link Type}.
     * @param typeDeclaration the type declaration (class) to support the analysis of a type, (if reference type)
     * @param type the type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the type
     */
    private static Type processType(TypeDeclaration typeDeclaration, com.github.javaparser.ast.type.Type type, int arrayLevel) {
        if (type.isPrimitiveType()) {
            return processPrimitiveType(type.asPrimitiveType(), arrayLevel);
        } else if (type.isArrayType()) {
            com.github.javaparser.ast.type.Type arrayType = type.asArrayType().getComponentType();
            return processType(typeDeclaration, arrayType, arrayLevel + 1);
        } else if (type.isTypeParameter()) {
            return processTypeParameter(typeDeclaration, type.asTypeParameter(), arrayLevel);
        } else if (type.isReferenceType()) {
            return processReferenceType(typeDeclaration, type.asReferenceType(), arrayLevel);
        } else if (type.isVoidType()) {
            return processVoidType(type.asVoidType());
        }
        // TODO: Handle other types (like ClassOrInterfaceType, etc.)
        throw new RuntimeException("Type not supported: " + type.toString());
    }

    /**
     * Handle a type (that can be primitive, reference or void) and generate the corresponding {@link Type}.
     * The method is invoked when a constructor or a method cannot be resolved with a callable declaration, for example
     * when it refers to a class of an external library, whose source code is not accessible (only the bytecode is available).
     * Therefore, only the {@link ResolvedType} is available.
     *
     * @param type the type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the type
     */
    private static Type processType(ResolvedType type, int arrayLevel) {
        if (type.isPrimitive()) {
            return processPrimitiveType(type.asPrimitive(), arrayLevel);
        } else if (type.isArray()) {
            ResolvedType arrayType = type.asArrayType().getComponentType();
            return processType(arrayType, arrayLevel + 1);
        } else if (type.isReferenceType()) {
            return processReferenceType(type.asReferenceType(), arrayLevel);
        } else if (type.isTypeVariable()) {
            return processTypeVariable(type.asTypeVariable(), arrayLevel);
        } else if (type.isVoid()) {
            return processVoidType(type);
        }
        // TODO: Handle other types (like ResolvedClassDeclaration, etc.)
        throw new RuntimeException("Type not supported: " + type.toString());
    }

    /**
     * Process the primitive type and generate the corresponding {@link Type} object.
     *
     * @param primitiveType the primitive type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the primitive type
     */
    private static Type processPrimitiveType(PrimitiveType primitiveType, int arrayLevel) {
        // Instantiate a type builder to build a Type object from the primitive type
        TypeBuilder typeBuilder = new TypeBuilder();
        // Set the attributes of the type
        typeBuilder.setIdentifier(primitiveType.getType().asString().replace("Ljava", "java"));
        typeBuilder.setFullyQualifiedIdentifiers(new ArrayList<>());
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(true);
        typeBuilder.setIsGeneric(false);
        typeBuilder.setArrayLevel(arrayLevel);
        // A primitive type does not have any reference to classes. An empty list is set
        typeBuilder.setClazzes(new ArrayList<>());
        // Build the type and return it
        return typeBuilder.build();
    }

    /**
     * Process the primitive type and generate the corresponding {@link Type} object. The method
     * is invoked when the constructor or a method cannot be resolved with a callable declaration, for example when it refers to
     * a class of an external library, whose source code is not accessible (only the bytecode is available). Therefore,
     * only the {@link ResolvedPrimitiveType} is available.
     *
     *
     * @param resolvedPrimitiveType the primitive type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the primitive type
     */
    private static Type processPrimitiveType(ResolvedPrimitiveType resolvedPrimitiveType, int arrayLevel) {
        // Instantiate a type builder to build a Type object from the primitive type
        TypeBuilder typeBuilder = new TypeBuilder();
        // Set the attributes of the type
        typeBuilder.setIdentifier(resolvedPrimitiveType.describe().replace("Ljava", "java"));
        typeBuilder.setFullyQualifiedIdentifiers(new ArrayList<>());
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(true);
        typeBuilder.setIsGeneric(false);
        typeBuilder.setArrayLevel(arrayLevel);
        // A primitive type does not have any reference to classes. An empty list is set
        typeBuilder.setClazzes(new ArrayList<>());
        // Build the type and return it
        return typeBuilder.build();
    }

    /**
     * Check if the reference type is an array type.
     *
     * @param referenceType the reference type to check
     * @return {@code true} if the reference type is an array type, {@code false} otherwise
     */
    private static boolean isArrayType(ReferenceType referenceType) {
        if (referenceType.isArrayType()) {
            return true;
        } else if (referenceType instanceof ClassOrInterfaceType) {
            Optional<Node> parentNode = referenceType.getParentNode();
            if (parentNode.isPresent()) {
                Node parent = parentNode.get();
                if (parent instanceof ArrayType) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Process the reference type and generate the corresponding {@link Type} object.
     *
     * @param typeDeclaration the type declaration (class) to support the analysis of a type, (if reference type)
     * @param referenceType the reference type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the reference type
     */
    private static Type processReferenceType(TypeDeclaration typeDeclaration, ReferenceType referenceType, int arrayLevel) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(referenceType.isTypeParameter());
        typeBuilder.setArrayLevel(arrayLevel);
        typeBuilder.setIdentifier(referenceType.toString());
        // Try to find additional information about the reference type
        List<String> classNames = new ArrayList<>();
        try {
            // Set the identifier of the reference type
            classNames.add(referenceType.toDescriptor().replace("Ljava", "java"));
        } catch (UnsolvedSymbolException | IllegalStateException | UnsupportedOperationException e) {
            // TODO: Handle complex types like generics, List of reference types, List of Pair, etc.
            // Regex to match class names (starting with uppercase letters)
            Pattern pattern = Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b");
            Matcher matcher = pattern.matcher(referenceType.toString());
            while (matcher.find()) {
                String className = matcher.group();
                // If the reference type is not resolved, try to find the identifier in the imports of the compilation unit
                for (ImportDeclaration importDeclaration : typeDeclaration.findCompilationUnit().get().getImports()) {
                    if (importDeclaration.getNameAsString().endsWith(className)) {
                        classNames.add(importDeclaration.getNameAsString());
                        break;
                    }
                }
            }
        }
        typeBuilder.setFullyQualifiedIdentifiers(classNames);
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();

        // TODO: Implement the processing of the reference type (improved version of the dataset)
        /*Optional<ResolvedReferenceTypeDeclaration> resolvedType = referenceType.resolve().asReferenceType().getTypeDeclaration();

        if (resolvedType.isPresent()) {
            // Initialize a clazz builder to build a ClazzTracto object from the resolved type
            ClazzBuilder clazzBuilder = new ClazzBuilder();
            // Get methods defined and inherited by the resolved type
            Set<MethodUsage> methods = resolvedType.get().getAllMethods();
            // Get constructors defined and inherited by the resolved type
            List<ResolvedConstructorDeclaration> constructors = resolvedType.get().getConstructors();
            // Process the methods of the variable type
            for (MethodUsage method : methods) {
                // Try to resolve the method declaration from the method usage
                Optional<Node> resolvedMethodDeclaration = method.getDeclaration().toAst();
                if (resolvedMethodDeclaration.isPresent()) {
                    MethodDeclaration methodDeclaration = (MethodDeclaration) resolvedMethodDeclaration.get();
                    MethodTracto processedMethod = TestUtils.processCallable(methodDeclaration);
                } else {
                    // TODO: Analyze the method usage
                    throw new RuntimeException("Method declaration not found for method usage: " + method.getName());
                }
            }
        }*/
    }

    /**
     * Process the generic type and generate the corresponding {@link Type} object.
     *
     * @param typeDeclaration the type declaration (class) to support the analysis of a type, (if reference type)
     * @param typeParameter the generic type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the reference type
     */
    private static Type processTypeParameter(TypeDeclaration typeDeclaration, TypeParameter typeParameter, int arrayLevel) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(true);
        typeBuilder.setArrayLevel(arrayLevel);
        typeBuilder.setIdentifier(typeParameter.toString());
        // Try to find additional information about the reference type
        List<String> classNames = new ArrayList<>();
        try {
            // Set the identifier of the reference type
            classNames.add(typeParameter.toDescriptor().replace("Ljava", "java"));
        } catch (UnsolvedSymbolException e) {
            // TODO: Handle complex types like generics, List of reference types, List of Pair, etc.
            // Regex to match class names (starting with uppercase letters)
            Pattern pattern = Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b");
            Matcher matcher = pattern.matcher(typeParameter.toString());
            while (matcher.find()) {
                String className = matcher.group();
                // If the reference type is not resolved, try to find the identifier in the imports of the compilation unit
                for (ImportDeclaration importDeclaration : typeDeclaration.findCompilationUnit().get().getImports()) {
                    if (importDeclaration.getNameAsString().endsWith(className)) {
                        classNames.add(importDeclaration.getNameAsString());
                        break;
                    }
                }
            }
        }
        typeBuilder.setFullyQualifiedIdentifiers(classNames);
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();
    }

    /**
     * Process the reference type and generate the corresponding {@link Type} object. The method is invoked when a
     * constructor or a method cannot be resolved with a callable declaration, for example when it refers to a class of
     * an external library, whose source code is not accessible (only the bytecode is available). Therefore, only the
     * {@link ResolvedReferenceType} is available.
     *
     * @param referenceType the reference type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the reference type
     */
    private static Type processReferenceType(ResolvedReferenceType referenceType, int arrayLevel) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(false);
        typeBuilder.setArrayLevel(arrayLevel);
        typeBuilder.setIdentifier(referenceType.getQualifiedName().substring(referenceType.getQualifiedName().lastIndexOf(".") + 1));
        // Try to find additional information about the reference type
        List<String> classNames = new ArrayList<>();
        try {
            // Set the identifier of the reference type
            classNames.add(referenceType.getQualifiedName().replace("Ljava", "java"));
        } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException e) {}
        typeBuilder.setFullyQualifiedIdentifiers(classNames);
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();
    }

    /**
     * Process the generic type and generate the corresponding {@link Type} object. The method is invoked when a
     * constructor or a method cannot be resolved with a callable declaration, for example when it refers to a class of
     * an external library, whose source code is not accessible (only the bytecode is available). Therefore, only the
     * {@link ResolvedTypeVariable} is available.
     *
     * @param typeVariable the generic type to process
     * @param arrayLevel the array level of the type (0 if the type is not an array)
     * @return the {@link Type} object generated from the generic type
     */
    private static Type processTypeVariable(ResolvedTypeVariable typeVariable, int arrayLevel) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(false);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(true);
        typeBuilder.setArrayLevel(arrayLevel);
        typeBuilder.setIdentifier(typeVariable.describe());
        // Try to find additional information about the reference type
        List<String> classNames = new ArrayList<>();
        try {
            // Set the identifier of the reference type
            classNames.add(typeVariable.qualifiedName().replace("Ljava", "java"));
        } catch (UnsolvedSymbolException e) {}
        typeBuilder.setFullyQualifiedIdentifiers(classNames);
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();
    }
    
    /**
     * Process the void type and generate the corresponding {@link Type} object. The method is invoked when a
     * constructor or a method cannot be resolved with a callable declaration, for example when it refers to a class of
     * an external library, whose source code is not accessible (only the bytecode is available). Therefore, only the
     * {@link ResolvedType} is available.
     *
     * @param voidType the void type to process
     * @return the {@link Type} object generated from the resolved void type
     */
    private static Type processVoidType(ResolvedType voidType) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(true);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(false);
        typeBuilder.setArrayLevel(0);
        // Leave the detailed analysis of the reference type empty for the first version of the oracles dataset
        typeBuilder.setIdentifier(voidType.toString());
        typeBuilder.setFullyQualifiedIdentifiers(new ArrayList<>());
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();
    }

    /**
     * Process the void type and generate the corresponding {@link Type} object.
     *
     * @param voidType the void type to process
     * @return the {@link Type} object generated from the void type
     */
    private static Type processVoidType(VoidType voidType) {
        TypeBuilder typeBuilder = new TypeBuilder();
        typeBuilder.setIsVoid(true);
        typeBuilder.setIsPrimitive(false);
        typeBuilder.setIsGeneric(false);
        typeBuilder.setArrayLevel(0);
        // Leave the detailed analysis of the reference type empty for the first version of the oracles dataset
        typeBuilder.setIdentifier(voidType.toString());
        typeBuilder.setFullyQualifiedIdentifiers(new ArrayList<>());
        typeBuilder.setClazzes(new ArrayList<>());
        return typeBuilder.build();
    }

    /**
     * Process the test class in the form of {@link TypeDeclaration} and generate the corresponding
     * {@link TestClazz} object.
     *
     * @param testClass the test class to process
     * @param testFilePath the path to the test class file
     * @param distributedMethods the methods of the test class distributed according to their meaning
     *                           (auxiliary, setup & tear down, test cases)
     * @return the {@link TestClazz} object generated from the test class
     */
    private static TestClazz processTestClass(
            TypeDeclaration testClass,
            Path testFilePath,
            Triplet<List<MethodDeclaration>, HashMap<String, MethodDeclaration>, HashMap<String, MethodDeclaration>> distributedMethods
    ) {
        TestClazzBuilder testClazzBuilder = new TestClazzBuilder();
        // Set test class identifier (name)
        testClazzBuilder.setIdentifier(testClass.getNameAsString());
        // Set test class package
        testClazzBuilder.setPackageIdentifier(JavaParserUtils.getPackageNameFromTypeDeclaration(testClass).orElse(""));
        // Retrieve the superclasses extended and the interfaces implemented by the test class
        List<String> superClasses = new ArrayList<>();
        List<String> interfaces = new ArrayList<>();
        for(ResolvedReferenceType resolvedReferenceType : testClass.resolve().getAllAncestors()) {
            Optional<ResolvedReferenceTypeDeclaration> resolvedReferenceTypeDeclaration = resolvedReferenceType.getTypeDeclaration();
            if (resolvedReferenceTypeDeclaration.isPresent()) {
                if (resolvedReferenceTypeDeclaration.get().isClass()) {
                    superClasses.add(resolvedReferenceTypeDeclaration.get().getQualifiedName());
                } else if (resolvedReferenceTypeDeclaration.get().isInterface()) {
                    interfaces.add(resolvedReferenceTypeDeclaration.get().getQualifiedName());
                }
            }
            // TODO: We miss the reference type without a corresponding declaration in this way.
            //       We can evaluate if join interfaces and extended classes in a single array and
            //       extract all the information directly from the ResolvedReferenceType
        }
        // Set test class superclasses and interfaces
        testClazzBuilder.setSuperClasses(superClasses);
        testClazzBuilder.setInterfaces(interfaces);
        // Set file path of the test class
        testClazzBuilder.setFilePath(testFilePath.toString());
        // Set test class fields
        testClazzBuilder.setFields(processFieldsFromTypeDeclaration(testClass));
        // Set the list of all the test cases defined in the given test class
        List<MethodDeclaration> testCases = distributedMethods.getValue0();
        // TODO: Manage invoked methods recursion in the improved version of the dataset. In the current
        //       version the invoked methods are set to null.
        testClazzBuilder.setTestCases(testCases.stream().map(m -> processCallable(testClass, m, null)).collect(Collectors.toList()));
        // Set the list of all the setup & tear down methods defined in the given test class
        HashMap<String, MethodDeclaration> setupTearDownMethods = distributedMethods.getValue1();
        testClazzBuilder.setSetupTearDownMethods(setupTearDownMethods.values().stream().map(m -> processCallable(testClass, m, null)).collect(Collectors.toList()));
        // Set the list of all the auxiliary methods defined in the given test class
        HashMap<String, MethodDeclaration> auxiliaryMethods = distributedMethods.getValue2();
        testClazzBuilder.setAuxiliaryMethods(auxiliaryMethods.values().stream().map(m -> processCallable(testClass, m, null)).collect(Collectors.toList()));
        // Generate and return the test class
        return testClazzBuilder.build();
    }

    /**
     * Process the focal class in the form of {@link TypeDeclaration} and generate the corresponding
     * {@link Clazz} object.
     *
     * @param focalClass the test class to process
     * @param sourceFilePath the path to the source file representing the focal class
     * @return the {@link TestClazz} object generated from the test class
     */
    private static Clazz processFocalClass(TypeDeclaration focalClass, Path sourceFilePath) {
        ClazzBuilder focalClazzBuilder = new ClazzBuilder();
        // Set test class identifier (name)
        focalClazzBuilder.setIdentifier(focalClass.getNameAsString());
        // Set test class package
        focalClazzBuilder.setPackageIdentifier(JavaParserUtils.getPackageNameFromTypeDeclaration(focalClass).orElse(""));
        // Retrieve the superclasses extended and the interfaces implemented by the test class
        List<String> superClasses = new ArrayList<>();
        List<String> interfaces = new ArrayList<>();
        try {
            for (ResolvedReferenceType resolvedReferenceType : focalClass.resolve().getAllAncestors()) {
                Optional<ResolvedReferenceTypeDeclaration> resolvedReferenceTypeDeclaration = resolvedReferenceType.getTypeDeclaration();
                if (resolvedReferenceTypeDeclaration.isPresent()) {
                    if (resolvedReferenceTypeDeclaration.get().isClass()) {
                        superClasses.add(resolvedReferenceTypeDeclaration.get().getQualifiedName());
                    } else if (resolvedReferenceTypeDeclaration.get().isInterface()) {
                        interfaces.add(resolvedReferenceTypeDeclaration.get().getQualifiedName());
                    }
                }
                // TODO: We miss the reference type without a corresponding declaration in this way.
                //       We can evaluate if join interfaces and extended classes in a single array and
                //       extract all the information directly from the ResolvedReferenceType
            }
        } catch (UnsolvedSymbolException e) {
            logger.error("Error resolving ancestors of type declaration: " + focalClass.getNameAsString());
        }
        // Set test class superclasses and interfaces
        focalClazzBuilder.setSuperClasses(superClasses);
        focalClazzBuilder.setInterfaces(interfaces);
        // Set file path of the test class
        focalClazzBuilder.setFilePath(sourceFilePath.toString());
        // Set test class fields
        focalClazzBuilder.setFields(processFieldsFromTypeDeclaration(focalClass));
        // Set the list of all the constructors and methods defined in the given focal class
        List<ConstructorDeclaration> constructors = focalClass.getConstructors();
        // TODO: Manage invoked methods recursion in the improved version of the dataset. In the current
        //       version the invoked methods are set to null.
        focalClazzBuilder.setConstructors(constructors.stream().map(m -> processCallable(focalClass, m, null)).collect(Collectors.toList()));
        List<MethodDeclaration> methods = focalClass.getMethods();
        focalClazzBuilder.setMethods(methods.stream().map(m -> processCallable(focalClass, m, null)).collect(Collectors.toList()));
        return focalClazzBuilder.build();
    }

    /**
     * Process a type declaration to retrieve the list of related type declarations (extended and implemented classes), iteratively.
     *
     * @param referenceTypeDeclaration the reference type declaration to analyze
     * @param repoSourcePath the root path of the repository where the type declaration is located
     * @return the list of related type declarations (extended and implemented classes) of the reference type declaration
     */
    private static List<TypeDeclaration> getRelatedTypeDeclarations(TypeDeclaration referenceTypeDeclaration, Path repoSourcePath, Set<String> visited) {
        // Define the list of related type declarations to return. Initially empty.
        List<TypeDeclaration> relatedTypeDeclarations = new ArrayList<>();
        // Define the list of extended and implemented types of the reference type declaration
        List<ClassOrInterfaceType> extendedAndImplementedTypes = new ArrayList<>();
        // Check if the reference type declaration is a class or interface declaration. In this case, retrieve the
        // extended and implemented types.
        if (referenceTypeDeclaration instanceof ClassOrInterfaceDeclaration) {
            extendedAndImplementedTypes.addAll(((ClassOrInterfaceDeclaration) referenceTypeDeclaration).getExtendedTypes());
            extendedAndImplementedTypes.addAll(((ClassOrInterfaceDeclaration) referenceTypeDeclaration).getImplementedTypes());
        } else {
            // Not a class or interface declaration. No extended or implemented types to retrieve. Return an empty list.
            return relatedTypeDeclarations;
        }
        // Retrieve the compilation unit of the reference type declaration
        CompilationUnit referenceTypeCu = (CompilationUnit) referenceTypeDeclaration.getParentNode().orElse(null);
        // Retrieve the list of imports of the reference type compilation unit
        NodeList<ImportDeclaration> referenceTypeImports = referenceTypeCu != null ? referenceTypeCu.getImports() : new NodeList<>();
        // Retrieve the package of the reference type declaration
        PackageDeclaration referenceTypePackage = referenceTypeCu != null ? referenceTypeCu.getPackageDeclaration().orElse(null) : null;
        // Iterate over the extended and implemented types of the reference type declaration
        // to collect the related type declarations (iteratively)
        for (ClassOrInterfaceType extendedOrImplementedType : extendedAndImplementedTypes) {
            // Retrieve the name of the extended or implemented type
            String extendedOrImplementedTypeName = extendedOrImplementedType.getNameAsString();
            // Define the fully qualified name of the extended or implemented type to resolve. Initially null.
            String fqn = null;
            // Check if the extended or implemented type is imported in the reference type compilation unit
            for (ImportDeclaration referenceTypeImport : referenceTypeImports) {
                String importName = referenceTypeImport.getNameAsString();
                // Check if the import name ends with the name of the extended or implemented type
                if (importName.endsWith(extendedOrImplementedTypeName)) {
                    // Set the fully qualified name of the extended or implemented type and break the loop. The reference
                    // type declaration is found in the imports.
                    fqn = importName;
                    break;
                }
            }
            // Check if the fully qualified name of the extended or implemented type is still null. In this case, if
            // the package of the reference type declaration is not null, set the fully qualified name of the extended
            // or implemented type by concatenating the package name and the name of the extended or implemented type.
            // The reference type can lie in the same package of the reference type declaration.
            if (fqn == null && referenceTypePackage != null) {
                fqn = referenceTypePackage.getNameAsString().concat("." + extendedOrImplementedTypeName);
            }

            if (fqn != null && !visited.contains(fqn)) {
                visited.add(fqn);
                try {
                    Optional<TypeDeclaration> relatedTypeDeclaration = JavaParserUtils.retrieveTypeDeclarationFromFullyQualifiedName(fqn, repoSourcePath);
                    if (relatedTypeDeclaration.isPresent()) {
                        relatedTypeDeclarations.add(relatedTypeDeclaration.get());
                        relatedTypeDeclarations.addAll(getRelatedTypeDeclarations(relatedTypeDeclaration.get(), repoSourcePath, visited));
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving related type declaration: " + fqn);
                }
            }
        }
        return relatedTypeDeclarations;
    }

    private static Pair<Callable,List<Callable>> processInvokedMethods(Path repoSourcePath, TypeDeclaration focalClass, TypeDeclaration testClass, MethodDeclaration testCase, HashMap<String, HashMap<String, List<String>>> testsProcessingErrors, OraclesDatasetConfig config) {
        // Define list of methods invoked in the test case
        List<Callable> invokedMethods = new ArrayList<>();
        // Define the focal method to find. Initially null.
        Callable focalMethod = null;
        // Get the name of the test case
        String testName = testCase.getNameAsString();
        // Get the name of the test class
        String testClassName = testClass.getFullyQualifiedName().orElse(testClass.getNameAsString()).toString();
        // Setup error map for the test class
        testsProcessingErrors.putIfAbsent(testClassName, new HashMap<>());
        // Setup error map for the test case
        testsProcessingErrors.get(testClassName).putIfAbsent(testName, new ArrayList<>());
        try {
            // Get the list of all the statements within the test
            List<ExpressionStmt> statements = StmtVisitorHelper.getAllExpressionStmts(testCase.getBody().get());
            // Throw an exception if no statements are found (empty test)
            if (statements.size() <= 0 && config.targetStrategy() == TargetStrategyType.ASSERTION) {
                throw new IllegalStateException(String.format("No expression statements found in test %s.", testName));
            }
            // Get statement index
            int stmtIndex = statements.size() - 1;
            // Define an HashMap saving information about the variable progressively defined (and used) within the test case
            HashMap<String, TypeDeclaration> projectVariables = new HashMap<>();
            HashMap<String, ResolvedReferenceType> librariesVariables = new HashMap<>();

            for (FieldDeclaration testClassField : testClass.findAll(FieldDeclaration.class)) {
                for (VariableDeclarator variable : testClassField.getVariables()) {
                    com.github.javaparser.ast.type.Type variableType = variable.getType();
                    while (variableType.isArrayType()) {
                        variableType = variableType.asArrayType().getComponentType();
                    }
                    // TODO: Handle the case when the variable type is an array
                    try {
                        if (variableType.isReferenceType() && !variableType.resolve().isTypeVariable()) {
                            ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = variableType.resolve().asReferenceType().getTypeDeclaration().orElse(null);

                            if (resolvedReferenceTypeDeclaration != null) {
                                if (resolvedReferenceTypeDeclaration instanceof JavaParserClassDeclaration) {
                                    projectVariables.put(variable.getNameAsString(), ((JavaParserClassDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode());
                                    continue;
                                } else if (resolvedReferenceTypeDeclaration instanceof JavaParserInterfaceDeclaration) {
                                    projectVariables.put(variable.getNameAsString(), ((JavaParserInterfaceDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode());
                                    continue;
                                }
                            }
                            try {
                                ResolvedReferenceType resolvedReferenceType = variableType.resolve().asReferenceType();
                                Optional<TypeDeclaration> resolvedTypeDeclaration = JavaParserUtils.retrieveTypeDeclarationFromFullyQualifiedName(resolvedReferenceType.getQualifiedName(), repoSourcePath);
                                if (resolvedTypeDeclaration.isPresent()) {
                                    projectVariables.put(variable.getNameAsString(), resolvedTypeDeclaration.get());
                                } else {
                                    librariesVariables.put(variable.getNameAsString(), resolvedReferenceType);
                                }
                            } catch (Exception e) {
                                String errorMsg = "Unable to resolve variable type " + variableType.toString() + " in test class " + testClass.getNameAsString();
                                // logger.error(errorMsg);
                                testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        String errorMsg = "Unable to resolve variable type " + variableType.toString() + " in test class " + testClass.getNameAsString();
                        // logger.error(errorMsg);
                        testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                    }
                }
            }


            // Iterate over the statements of the test from the top to the bottom
            for (int i = 0; i <= stmtIndex; i++) {
                // Get current statement
                ExpressionStmt exprStmt = statements.get(i);
                Expression expr = null;
                CallableExprType exprType = null;
                // Get the expression from the current statement
                Expression unparsedExpr = exprStmt.getExpression();
                // Parse the expression to understand if it refers to a method call or construction invocation
                List<Pair<Expression,CallableExprType>> expressionAnalysisResult = JavaParserUtils.parseExpression(unparsedExpr, true);
                for (Pair<Expression,CallableExprType> exprPair : expressionAnalysisResult) {
                    try {
                        // Get the expression and its type
                        expr = exprPair.getValue0();
                        exprType = exprPair.getValue1();
                        boolean fromExternalLibrary = false;
                        // Initialize the invoked class to the reference class. The invoked class is the instance of the class
                        // that contains the invoked method.
                        TypeDeclaration invokedClass = focalClass;

                        if (exprType == CallableExprType.METHOD) {
                            if (expr.asMethodCallExpr().getScope().isPresent()) {
                                Expression scope = expr.asMethodCallExpr().getScope().get();
                                while (scope.isArrayAccessExpr()) {
                                    scope = scope.asArrayAccessExpr().getName();
                                }
                                if (scope.isNameExpr()) {
                                    String scopeName = scope.asNameExpr().getNameAsString();
                                    if (projectVariables.containsKey(scopeName)) {
                                        invokedClass = projectVariables.get(scopeName);
                                    } else if (librariesVariables.containsKey(scopeName)) {
                                        fromExternalLibrary = true;
                                        ResolvedReferenceType resolvedReferenceType = librariesVariables.get(scopeName);
                                        ResolvedMethodDeclaration resolvedMethodDeclaration = JavaParserUtils.searchCandidateResolvedMethodDeclaration(resolvedReferenceType, expr.asMethodCallExpr());
                                        if (resolvedMethodDeclaration == null) {
                                            throw new CandidateCallableDeclarationNotFoundException("The method in the expression " + expr.toString() + " cannot be resolved in the given focal class " + focalClass.getNameAsString());
                                        }
                                        invokedMethods.add(processMethodCallExpr(expr.asMethodCallExpr().resolve()));
                                    }
                                }
                            }
                        }
                        if (!fromExternalLibrary) {
                            // Get the invoked method. The invoked method must be a method declaration or a constructor declaration.
                            // Start searching the method or constructor in the test class
                            CallableDeclaration invokedMethod = JavaParserUtils.resolveCallExpression(testClass, expr, exprType);
                            // If the focal method is not found, look at the methods and constructors in the focal class
                            if (invokedMethod == null) {
                                invokedMethod = JavaParserUtils.resolveCallExpression(invokedClass, expr, exprType);
                            }
                            // If the focal method is still not found, search it within the inherited methods of the focal class
                            if (invokedMethod == null) {
                                List<TypeDeclaration> relatedTypeDeclarations = getRelatedTypeDeclarations(invokedClass, repoSourcePath, new HashSet<>());
                                relatedTypeDeclarations.add(0, invokedClass);
                                for (TypeDeclaration relatedTypeDeclaration : relatedTypeDeclarations) {
                                    invokedClass = relatedTypeDeclaration;
                                    if (exprType == CallableExprType.CONSTRUCTOR) {
                                        com.github.javaparser.ast.type.Type constructorType = expr.asObjectCreationExpr().getType();
                                        while (constructorType.isArrayType()) {
                                            constructorType = constructorType.asArrayType().getComponentType();
                                        }
                                        // TODO: Handle the case when the variable type is an array
                                        try {
                                            if (constructorType.isReferenceType() && !constructorType.resolve().isTypeVariable()) {
                                                ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = constructorType.resolve().asReferenceType().getTypeDeclaration().orElse(null);
                                                if (resolvedReferenceTypeDeclaration != null) {
                                                    if (resolvedReferenceTypeDeclaration instanceof JavaParserClassDeclaration) {
                                                        invokedClass = ((JavaParserClassDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode();
                                                        invokedMethod = JavaParserUtils.resolveCallExpression(invokedClass, expr, exprType);
                                                    } else if (resolvedReferenceTypeDeclaration instanceof JavaParserInterfaceDeclaration) {
                                                        invokedClass = ((JavaParserInterfaceDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode();
                                                        invokedMethod = JavaParserUtils.resolveCallExpression(invokedClass, expr, exprType);
                                                    }
                                                }
                                                if (invokedMethod == null) {
                                                    String candidateClassPath = constructorType.resolve().asReferenceType().getQualifiedName();
                                                    Optional<TypeDeclaration> resolvedTypeDeclaration = JavaParserUtils.retrieveTypeDeclarationFromFullyQualifiedName(candidateClassPath, repoSourcePath);
                                                    if (resolvedTypeDeclaration.isPresent()) {
                                                        invokedClass = resolvedTypeDeclaration.get();
                                                        invokedMethod = JavaParserUtils.resolveCallExpression(invokedClass, expr, exprType);
                                                        if (invokedMethod == null) {
                                                            if (expr.toString().equals("new " + invokedClass.getNameAsString() + "()")) {
                                                                invokedMethod = new ConstructorDeclaration();
                                                                invokedMethod.setName(invokedClass.getNameAsString());
                                                            } else {
                                                                throw new NoFocalMethodMatchingException("The constructor in the expression " + expr.toString() + " cannot be resolved in the given focal class " + invokedClass.getNameAsString());
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            String errorMsg = "Unable to resolve constructor type " + constructorType.toString() + " in test class " + testClass.getNameAsString();
                                            // logger.error(errorMsg);
                                            testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                                        }
                                    } else {
                                        MethodUsage invokedMethodUsage = JavaParserUtils.searchCandidateMethodUsage(invokedClass, expr);
                                        // If the focal method is still not found, raise an exception
                                        if (!(invokedMethodUsage == null)) {
                                            Optional<TypeDeclaration> resolvedTypeDeclaration = JavaParserUtils.retrieveTypeDeclarationFromFullyQualifiedName(invokedMethodUsage.declaringType().getQualifiedName(), repoSourcePath);
                                            if (resolvedTypeDeclaration.isPresent()) {
                                                invokedClass = resolvedTypeDeclaration.get();
                                                List<MethodDeclaration> candidateMethods = invokedClass.getMethods();
                                                invokedMethod = JavaParserUtils.matchMethodUsageWithMethodDeclaration(invokedMethodUsage, candidateMethods);
                                            }
                                        }
                                    }
                                    if(invokedMethod != null) {
                                        break;
                                    }
                                }
                            }
                            // If the invoked method is still not found, process the expression as a call from an external library
                            if (invokedMethod == null) {
                                fromExternalLibrary = true;
                                try {
                                    if (exprType == CallableExprType.METHOD) {
                                        invokedMethods.add(processMethodCallExpr(expr.asMethodCallExpr().resolve()));
                                    } else {
                                        invokedMethods.add(processObjectCreationExpr(expr.asObjectCreationExpr()));
                                    }
                                } catch (UnsolvedSymbolException e) {
                                    throw new NoFocalMethodMatchingException("The method in the expression " + expr.toString() + " cannot be resolved in the given focal class " + focalClass.getNameAsString());
                                }
                            } else {
                                // Create the focal method
                                // TODO: Manage invoked methods recursion in the improved version of the dataset. In the current
                                //       version the invoked methods are set to null.
                                invokedMethods.add(processCallable(invokedClass, invokedMethod, null));
                                // Update focal method
                                if (invokedClass.equals(focalClass)) {
                                    focalMethod = invokedMethods.get(invokedMethods.size() - 1);
                                }
                            }
                        }
                    } catch (
                            NoFocalMethodMatchingException | NoPrimaryTypeException | UnrecognizedExprException |
                            CandidateCallableDeclarationNotFoundException |
                            CandidateCallableMethodUsageNotFoundException |
                            MultipleCandidatesException | UnsolvedSymbolException | IllegalStateException e
                    ) {
                        String errorMsg = "Error processing statement " + exprStmt + " of test case " + testName;
                        // logger.error(errorMsg);
                        testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                    }
                }
                if (unparsedExpr.isVariableDeclarationExpr() ) {
                    VariableDeclarationExpr varExpr = unparsedExpr.asVariableDeclarationExpr();
                    for (VariableDeclarator var : varExpr.getVariables()) {
                        com.github.javaparser.ast.type.Type varType = var.getType();
                        while (varType.isArrayType()) {
                            varType = varType.asArrayType().getComponentType();
                        }
                        // TODO: Handle the case when the variable type is an array
                        try {
                            if (varType.isReferenceType() && !varType.resolve().isTypeVariable()) {
                                ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = varType.resolve().asReferenceType().getTypeDeclaration().orElse(null);

                                if (resolvedReferenceTypeDeclaration != null) {
                                    if (resolvedReferenceTypeDeclaration instanceof JavaParserClassDeclaration) {
                                        projectVariables.put(var.getNameAsString(), ((JavaParserClassDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode());
                                        continue;
                                    } else if (resolvedReferenceTypeDeclaration instanceof JavaParserInterfaceDeclaration) {
                                        projectVariables.put(var.getNameAsString(), ((JavaParserInterfaceDeclaration) resolvedReferenceTypeDeclaration).getWrappedNode());
                                        continue;
                                    }
                                }

                                try {
                                    String fqnReturnType = varType.resolve().asReferenceType().getQualifiedName();
                                    Optional<TypeDeclaration> resolvedTypeDeclaration = JavaParserUtils.retrieveTypeDeclarationFromFullyQualifiedName(fqnReturnType, repoSourcePath);
                                    if (resolvedTypeDeclaration.isPresent()) {
                                        projectVariables.put(var.getNameAsString(), resolvedTypeDeclaration.get());
                                    } else {
                                        librariesVariables.put(var.getNameAsString(), varType.resolve().asReferenceType());
                                    }
                                } catch (Exception e) {
                                    String errorMsg = "Unable to resolve variable type " + varType.toString() + " in test class " + testClass.getNameAsString();
                                    // logger.error(errorMsg);
                                    testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            String errorMsg = "Unable to resolve variable type " + varType.toString() + " in test class " + testClass.getNameAsString();
                            // logger.error(errorMsg);
                            testsProcessingErrors.get(testClassName).get(testName).add(errorMsg + " | " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            String errMsg = String.format(
                    "[ERROR] - Unexpected error while matching focal method of class %s for test %s (%s)",
                    focalClass.getNameAsString(),
                    testCase.getNameAsString(),
                    e.getClass().getName()
            );
            // logger.error(errMsg);
            testsProcessingErrors.get(testClassName).get(testName).add(errMsg + " | " + e.getMessage());
        }
        // Remove duplicates from the list of invoked methods and return the focal method and the set of invoked methods (as list)
        return new Pair<>(focalMethod, new ArrayList<>(new LinkedHashSet<>(invokedMethods)));
    }

    /**
     * Process the class or interface type and generate the corresponding {@link Clazz} object.
     *
     * @param classOrInterfaceType the class or interface type to process
     * @return the {@link Clazz} object generated from the class or interface type
     */
    private static Clazz processClassOrInterface(ClassOrInterfaceType classOrInterfaceType) {
        ClazzBuilder clazzBuilder = new ClazzBuilder();
        Optional<ResolvedReferenceTypeDeclaration> resolvedReferenceTypeDeclaration = classOrInterfaceType.resolve().asReferenceType().getTypeDeclaration();

        clazzBuilder.setIdentifier(classOrInterfaceType.getNameAsString());

        if (resolvedReferenceTypeDeclaration.isPresent()) {
            Optional<Node> classOrInterfaceDeclaration = resolvedReferenceTypeDeclaration.get().toAst();

            if (classOrInterfaceDeclaration.isPresent() && classOrInterfaceDeclaration.get() instanceof ClassOrInterfaceDeclaration) {
                // TODO: Check
                //return processClassOrInterface(((ClassOrInterfaceDeclaration) classOrInterfaceDeclaration.get());
            }
        }
        //TODO: manage the other cases
        return null;
    }

    /**
     * Process the method declaration and generate the corresponding {@link Callable} object.
     *
     * @param typeDeclaration the type declaration (class) to which the method belongs
     * @param callableDeclaration the method or constructor declaration to process
     * @param invokedMethods the list of invoked methods within the method or constructor declaration. It can be null.
     * @return the {@link Callable} object generated from the method declaration
     */
    private static Callable processCallable(TypeDeclaration typeDeclaration, CallableDeclaration callableDeclaration, List<Callable> invokedMethods) {
        // Instantiate a method builder to build a MethodTracto object from the method declaration
        CallableBuilder callableBuilder = new CallableBuilder();
        // Set the attributes of the method
        callableBuilder.setIdentifier(callableDeclaration.getNameAsString());
        callableBuilder.setAnnotations(JavaParserUtils.getAnnotationsAsStrings(callableDeclaration.getAnnotations()));
        callableBuilder.setModifiers(JavaParserUtils.getModifiersAsStrings(callableDeclaration.getModifiers()));
        callableBuilder.setSignature(JavaParserUtils.getCallableSignature(callableDeclaration));
        callableBuilder.setThrownExceptions(JavaParserUtils.getExceptionsAsString(callableDeclaration.getThrownExceptions()));
        callableBuilder.setJavadoc(JavaParserUtils.getCallableJavadoc(callableDeclaration));
        // Removed any occurrence of `Assert.` in the body of the method (not useful for the oracle generation)
        callableBuilder.setBody(JavaParserUtils.getCallableSourceCode(callableDeclaration).replaceAll("(?<![a-zA-Z0-9])Assert\\.", "").replaceAll("(?<![a-zA-Z0-9])Assertions\\.", ""));
        // TODO: Manage invoked methods recursion in the improved version of the dataset
        callableBuilder.setInvokedMethods(invokedMethods); // avoid recursion in the first version of the dataset
        if (callableDeclaration instanceof MethodDeclaration) {
            callableBuilder.setReturnType(processType(typeDeclaration, ((MethodDeclaration) callableDeclaration).getType(),0));
        } else {
            callableBuilder.setReturnType(null);
        }
        // Process the parameters of the method
        List<Pair<String, Type>> parameters = new ArrayList<>();
        for (Object obj : callableDeclaration.getParameters()) {
            Parameter parameter = (Parameter) obj;
            String parameterName = parameter.getNameAsString();
            Type parameterType = processType(typeDeclaration, parameter.getType(), 0);
            parameters.add(new Pair<>(parameterName, parameterType));
        }
        callableBuilder.setParameters(parameters);
        // Build the method and return it
        return callableBuilder.build();
    }

    /**
     * Process the object creation expression and generate the corresponding {@link Callable} object. The method
     * is invoked when the constructor cannot be resolved with a callable declaration, for example when it refers to
     * a class of an external library, whose source code is not accessible (only the bytecode is available). Therefore,
     * some information are missing or less accurate.
     *
     * @param objCreationExpr the object creation expression to process
     * @return the {@link Callable} object generated from the method declaration
     */
    private static Callable processObjectCreationExpr(ObjectCreationExpr objCreationExpr) {
        ResolvedConstructorDeclaration resolvedConstructor = objCreationExpr.resolve();
        // Instantiate a method builder to build a MethodTracto object from the method declaration
        CallableBuilder callableBuilder = new CallableBuilder();
        // Set the attributes of the method
        callableBuilder.setIdentifier(resolvedConstructor.getName());
        callableBuilder.setAnnotations(null);
        callableBuilder.setModifiers(null);
        callableBuilder.setSignature(resolvedConstructor.getSignature());
        callableBuilder.setThrownExceptions(null);
        callableBuilder.setJavadoc("");
        callableBuilder.setBody(null);
        callableBuilder.setInvokedMethods(null); // avoid recursion in the first version of the dataset
        callableBuilder.setReturnType(null);
        // Process the parameters of the method
        List<Pair<String, Type>> parameters = new ArrayList<>();
        for (int i = 0; i < resolvedConstructor.getNumberOfParams(); i++) {
            ResolvedParameterDeclaration resolvedParam = resolvedConstructor.getParam(i);
            Type parameterType = processType(resolvedParam.getType(), 0);
            parameters.add(new Pair<>(null, parameterType));
        }
        callableBuilder.setParameters(parameters);
        // Build the method and return it
        return callableBuilder.build();
    }

    /**
     * Process the resolved method declaration and generate the corresponding {@link Callable} object. The method
     * is invoked when the method call cannot be resolved with a callable declaration, for example when it refers to
     * a class of an external library, whose source code is not accessible (only the bytecode is available). Therefore,
     * some information are missing or less accurate.
     *
     * @param resolvedMethod the resolved method declaration to process
     * @return the {@link Callable} object generated from the method declaration
     */
    private static Callable processMethodCallExpr(ResolvedMethodDeclaration resolvedMethod) {
        // Instantiate a method builder to build a MethodTracto object from the method declaration
        CallableBuilder callableBuilder = new CallableBuilder();
        // Set the attributes of the method
        callableBuilder.setIdentifier(resolvedMethod.getName());
        callableBuilder.setAnnotations(null);
        callableBuilder.setModifiers(null);
        callableBuilder.setSignature(resolvedMethod.getSignature());
        callableBuilder.setThrownExceptions(null);
        callableBuilder.setJavadoc("");
        callableBuilder.setBody(null);
        callableBuilder.setInvokedMethods(null); // avoid recursion in the first version of the dataset
        callableBuilder.setReturnType(null);
        // Process the parameters of the method
        List<Pair<String, Type>> parameters = new ArrayList<>();
        for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
            ResolvedParameterDeclaration resolvedParam = resolvedMethod.getParam(i);
            Type parameterType = processType(resolvedParam.getType(), 0);
            parameters.add(new Pair<>(null, parameterType));
        }
        callableBuilder.setParameters(parameters);
        // Build the method and return it
        return callableBuilder.build();
    }


    /**
     * Distribute the methods of the class according to their meaning.
     * The methods are distributed into the following categories:
     * <ul>
     *     <li>Test cases: methods annotated with @Test</li>
     *     <li>Setup and tear down methods: methods annotated with @Before, @BeforeEach, @After, @AfterEach, etc.</li>
     *     <li>Auxiliary methods: methods not annotated with any JUnit annotation (can contain assertions or procedures
     *     shared by multiple methods)</li>
     * </ul>
     *
     * @param testClassMethods the list of methods in the test class
     * @return a triplet containing the test cases, the setup and tear down methods, and the auxiliary methods
     */
    private static Triplet<List<MethodDeclaration>,HashMap<String,MethodDeclaration>, HashMap<String,MethodDeclaration>> distributeMethods(List<MethodDeclaration> testClassMethods) {
        // Create a map to store the setup and tear down methods (@Before, @BeforeEach, @After, @AfterEach, etc.)
        // The key is the signature of the method while the value is the method declaration
        HashMap<String, MethodDeclaration> setUpTearDownMethods = new HashMap<>();
        // Create a map to store auxiliary methods defined within the test class.
        // The key is the signature of the method while the value is the method declaration
        HashMap<String, MethodDeclaration> auxiliaryMethods = new HashMap<>();
        // Create a list to store the test cases
        List<MethodDeclaration> testCases = new ArrayList<>();
        // Distribute the methods into the corresponding set of methods
        for (MethodDeclaration method : testClassMethods) {
            NodeList<AnnotationExpr> methodAnnotations = method.getAnnotations();
            if (methodAnnotations.size() > 0) {
                if (methodAnnotations.stream().anyMatch(a -> JUnitAnnotationType.isTestAnnotation(a.getNameAsString()))) {
                    if (method.getBody().isPresent()) {
                        // TODO: Check if the method contains method calls to auxiliary methods with assertions
                        //      instead of discard the test method
                        //if (getNumberOfAssertions(method.getBody().get().getStatements()) > 0) {
                        //    testCases.add(method);
                        //}
                        testCases.add(method);
                    }
                } else if (methodAnnotations.stream().anyMatch(a -> a.getNameAsString().equals("Override"))) {
                    if (method.getBody().isPresent()) {
                        //if (getNumberOfAssertions(method.getBody().get().getStatements()) > 0) {
                        if (method.getNameAsString().contains("test")) {
                            testCases.add(method);
                        } else {
                            auxiliaryMethods.put(method.getSignature().asString(), method);
                        }
                    } else {
                        auxiliaryMethods.put(method.getSignature().asString(), method);
                    }
                } else if (methodAnnotations.stream().anyMatch(a -> JUnitAnnotationType.isSetUpTearDownAnnotation(a.getNameAsString()))) {
                    setUpTearDownMethods.put(method.getSignature().asString(), method);
                } else {
                    auxiliaryMethods.put(method.getSignature().asString(), method);
                }
            } else {
                auxiliaryMethods.put(method.getSignature().asString(), method);
            }
        }
        return new Triplet<>(testCases, setUpTearDownMethods, auxiliaryMethods);
    }
}
