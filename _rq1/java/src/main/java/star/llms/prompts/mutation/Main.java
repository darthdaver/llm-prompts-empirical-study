package star.llms.prompts.mutation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import star.llms.prompts.mutation.data.records.PromptInfo;
import star.llms.prompts.mutation.data.records.TestType;
import star.llms.prompts.mutation.utils.FilesUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {
        // Process arguments
        Path repoRootPath = Path.of(args[0]);
        Path promptInfoPath = Path.of(args[1]);
        Path inferenceFilePath = Path.of(args[2]);
        Path tempTestsClassesPath = Path.of(args[3]);
        Path javaHome = Path.of(args[4]);
        Path mvnHome = Path.of(args[5]);
        TestType testType = TestType.valueOf(args[6].toUpperCase());
        String classPath = args[7];

        FilesUtils.createDirectories(tempTestsClassesPath);

        // Project identifier
        List<PromptInfo> promptsInfos = Arrays.asList(FilesUtils.readJSON(promptInfoPath, PromptInfo[].class));
        List<List<String>> inference = parseInputFile(inferenceFilePath);
        List<List<String>> successCompiled = new ArrayList<>();
        List<List<String>> failCompiled = new ArrayList<>();
        List<List<String>> successTested = new ArrayList<>();
        List<List<String>> failTested = new ArrayList<>();
        List<String> testClassesProcessed = new ArrayList<>();

        List<List<String>> testClassesPathsMap = new ArrayList<>();

        HashMap<String, CompilationUnit> testClassesTempPathsMap = new HashMap<>();

        CompilationUnit cu;
        TypeDeclaration testClass = null;

        for (List<String> inferenceLine : inference) {
            for (PromptInfo promptInfo : promptsInfos) {
                // Check if the inference line matches the prompt info
                if (inferenceLine.get(0).equals(promptInfo.id())) {
                    Path absoluteTestClassPath = repoRootPath.resolve(promptInfo.testClassPath());
                    JavaParser javaParser = new JavaParser();
                    try {
                        if (testClassesTempPathsMap.containsKey(absoluteTestClassPath.toString())) {
                            cu = testClassesTempPathsMap.get(absoluteTestClassPath.toString());
                        } else {
                            cu = javaParser.parse(absoluteTestClassPath).getResult().orElseThrow();
                            testClass = cu.getPrimaryType().get();
                        }
                        // Get the list of all the methods defined within the test class
                        List<MethodDeclaration> testClassMethods = cu.findAll(MethodDeclaration.class);

                        for (MethodDeclaration method : testClassMethods) {
                            // Check if the method name matches the prompt info
                            if (promptInfo.signature().contains(method.getSignature().toString())) {
                                try {
                                    String newBodyStr = "";
                                    List<String> record = new ArrayList<>();
                                    record.add(absoluteTestClassPath.toString());
                                    record.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                                    record.add(method.getSignature().toString());
                                    if (testType == TestType.INFERENCE || testType == TestType.BOTH) {
                                        Pattern pattern = Pattern.compile("\\[oracle\\](.*?)\\[/oracle\\]", Pattern.DOTALL);
                                        Matcher matcher = pattern.matcher(inferenceLine.get(4));
                                        if (!matcher.find()) {
                                            throw new IllegalArgumentException("No oracle found in the inference line.");
                                        }
                                        String extracted = matcher.group(1);
                                        String replacer = testType == TestType.INFERENCE ? extracted : extracted + "\n    " + promptInfo.tgt();
                                        newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", replacer);
                                        record.add(extracted);
                                    } else if (testType == TestType.NO_ORACLE) {
                                        newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", "");
                                        record.add("");
                                    }
                                    BlockStmt oldBody = method.getBody().orElseThrow(() -> new IllegalArgumentException("Method " + method.getNameAsString() + " does not have a body.")).clone();
                                    BlockStmt newBody = javaParser.parseBlock(newBodyStr).getResult().get();
                                    method.setBody(newBody);
                                    Path tempTestClassPath = null;
                                    Path modifiedTestClassPath = null;
                                    if (!testClass.getNameAsString().endsWith(testType.getTestTypeTestLabel())) {
                                        tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + testType.getTestTypeTestLabel() + ".java");
                                        modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", testType.getTestTypeTestLabel() + ".java"));
                                        testClass.setName(testClass.getNameAsString() + testType.getTestTypeTestLabel());
                                    } else {
                                        tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + ".java");
                                        modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", testType.getTestTypeTestLabel() + ".java"));
                                    }

                                    if (!testClassesProcessed.contains(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString())) {
                                        testClassesProcessed.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                                    }

                                    FilesUtils.writeJavaFile(tempTestClassPath, cu);
                                    FilesUtils.writeJavaFile(modifiedTestClassPath, cu);
                                    boolean compilationResult = bashCall(new String[]{
                                        javaHome + "/bin/javac",
                                        "-cp",
                                        classPath,
                                        "-d",
                                        repoRootPath.toString() + "/target/test-classes",
                                        tempTestClassPath.toString()
                                    }, repoRootPath, mvnHome, javaHome);
                                    boolean runTestResult = false;
                                    if (compilationResult) {
                                        System.out.println("mvn surefire:test -Dtest=" + cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString() + "#" + method.getNameAsString());
                                        successCompiled.add(record);
                                        runTestResult = bashCall(new String[]{
                                                mvnHome + "/bin/mvn",
                                                "surefire:test",
                                                "-Dtest=" + cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString() + "#" + method.getNameAsString(),
                                        }, repoRootPath, mvnHome, javaHome);
                                        if (runTestResult) {
                                            successTested.add(record);
                                        } else {
                                            failTested.add(record);
                                        }
                                    } else {
                                        failCompiled.add(record);
                                    }

                                    if (!compilationResult || !runTestResult) {
                                        System.err.println("Compilation or test execution failed for: " + record);
                                        String noOracleBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", "");
                                        BlockStmt noOracleBody = javaParser.parseBlock(noOracleBodyStr).getResult().get();
                                        method.setBody(noOracleBody);
                                        FilesUtils.writeJavaFile(tempTestClassPath, cu);
                                        FilesUtils.writeJavaFile(modifiedTestClassPath, cu);
                                        bashCall(new String[]{
                                                javaHome + "/bin/javac",
                                                "-cp",
                                                classPath,
                                                "-d",
                                                repoRootPath.toString() + "/target/test-classes",
                                                tempTestClassPath.toString()
                                        }, repoRootPath, mvnHome, javaHome);
                                    }
                                    FilesUtils.writeJavaFile(tempTestClassPath, cu);
                                    FilesUtils.writeJavaFile(modifiedTestClassPath, cu);
                                    testClassesTempPathsMap.put(absoluteTestClassPath.toString(), cu);
                                } catch (Exception e) {
                                    System.err.println("Error parsing the new body: " + e.getMessage());
                                }
                            }
                        }
                        Path modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", testType.getTestTypeTestLabel() + ".java"));
                        Path tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + testType.getTestTypeTestLabel() + ".java");
                        List<String> mappingPaths = new ArrayList<>();
                        mappingPaths.add(tempTestClassPath.toString());
                        mappingPaths.add(modifiedTestClassPath.toString());
                        mappingPaths.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                        mappingPaths.add(inferenceLine.get(0));
                        boolean exists = false;
                        for (List<String> el: testClassesPathsMap) {
                            if (el.get(0).equals(mappingPaths.get(0))) {
                                if (el.get(1).equals(mappingPaths.get(1))) {
                                    if (el.get(2).equals(mappingPaths.get(2))) {
                                        if (el.get(3).equals(mappingPaths.get(3))) {
                                            // Already exists, skip adding
                                            exists = true;
                                        }
                                    }
                                }
                            }
                        }
                        if (!exists) {
                            testClassesPathsMap.add(mappingPaths);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // Write the modified test classes to the temporary path
//        for (String absoluteTestClassPath : testClassesTempPathsMap.keySet()) {
//            CompilationUnit finalCu = testClassesTempPathsMap.get(absoluteTestClassPath);
//            TypeDeclaration finalTestClass = finalCu.getPrimaryType().get();
//            // Save the modified test class
//            Path tempTestClassPath = tempTestsClassesPath.resolve(finalTestClass.getNameAsString() + testType.getTestTypeTestLabel() + ".java");
//            finalTestClass.setName(finalTestClass.getNameAsString() + testType.getTestTypeTestLabel());
//            FilesUtils.writeJavaFile(tempTestClassPath, finalCu);
//        }
        FilesUtils.writeCSV(repoRootPath.resolve("star_classes_mapping.csv"), testClassesPathsMap);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "_success_compiled.csv"), successCompiled);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "_success_tested.csv"), successTested);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "_fail_compiled.csv"), failCompiled);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "_fail_tested.csv"), failTested);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "_classes_processed.csv"), List.of(testClassesProcessed));
    }

    private static List<List<String>> parseInputFile(Path inputFilePath) { // Update with your file path
        List<List<String>> records = new ArrayList<>();
        StringBuilder contentBuilder = new StringBuilder();
        // Step 1: Read the file content
        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n"); // Append each line with a newline
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Step 2: Split the content
        String content = contentBuilder.toString();
        String[] rows = content.split("\\n---ROW END---\\n");
        // Step 3: Print the results
        for (String row : rows) {
            String [] row_columns = row.split("\\n---COLUMN END---\\n");
            List<String> record = new ArrayList<>();
            for (String column : row_columns) {
                record.add(column);
            }
            records.add(record);
        }
        return records;
    }

    private static boolean bashCall(String[] args, Path repoRootPath, Path mvnHome, Path javaHome) {
        try {
            ProcessBuilder builder = new ProcessBuilder(args);
            builder.directory(new java.io.File(repoRootPath.toString()));
            Map<String, String> env = builder.environment();
            String currentPath = System.getenv("PATH");
            String javaBin = javaHome.resolve("bin").toString();
            String mvnBin = mvnHome.resolve("bin").toString();
            // Safely combine them
            String newPath = javaBin + ":" + mvnBin + ":" + currentPath;
            env.put("PATH", newPath);
            // Optionally set JAVA_HOME if needed
            env.put("JAVA_HOME", javaHome.toString());
            env.put("MAVEN_HOME", mvnHome.toString());
            Process process = builder.start();
            // Capture standard output
            BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // Capture error output
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            boolean hasError = false;
            while ((line = stdError.readLine()) != null) {
                System.err.println(line);
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }
}
