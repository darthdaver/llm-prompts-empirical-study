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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) {
        // Process arguments
        Path repoRootPath = Path.of(args[0]);
        Path promptInfoPath = Path.of(args[1]);
        Path inferenceFilePath = Path.of(args[2]);
        Path tempTestsClassesPath = Path.of(args[3]);
        Path javac = Path.of(args[4]);
        TestType testType = TestType.valueOf(args[5].toUpperCase());
        String classPath = args[6];

        FilesUtils.createDirectories(tempTestsClassesPath);

        // Project identifier
        List<PromptInfo> promptsInfos = Arrays.asList(FilesUtils.readJSON(promptInfoPath, PromptInfo[].class));
        List<List<String>> inference = FilesUtils.readCSV(inferenceFilePath);
        List<List<String>> successCompiled = new ArrayList<>();
        List<List<String>> failCompiled = new ArrayList<>();
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
                                    if (testType == TestType.INFERENCE) {
                                        Pattern pattern = Pattern.compile("\\[oracle\\](.*?)\\[/oracle\\]", Pattern.DOTALL);
                                        Matcher matcher = pattern.matcher(inferenceLine.get(4));
                                        if (!matcher.find()) {
                                            throw new IllegalArgumentException("No oracle found in the inference line.");
                                        }
                                        String extracted = matcher.group(1);
                                        newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", extracted);
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
                                    if (!testClass.getNameAsString().endsWith("_" + testType.getTestType())) {
                                        tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + "_" + testType.getTestType() + ".java");
                                        modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", "_" + testType.getTestType() + ".java"));
                                        testClass.setName(testClass.getNameAsString() + "_" + testType.getTestType());
                                    } else {
                                        tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + ".java");
                                        modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", "_" + testType.getTestType() + ".java"));
                                    }

                                    if (!testClassesProcessed.contains(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString())) {
                                        testClassesProcessed.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                                    }

                                    FilesUtils.writeJavaFile(tempTestClassPath, cu);
                                    FilesUtils.writeJavaFile(modifiedTestClassPath, cu);
                                    boolean compilationResult = bashCall(new String[]{
                                        javac.toString(),
                                        "-cp",
                                        classPath,
                                        "-d",
                                        "target/classes",
                                        tempTestClassPath.toString()
                                    });
                                    if (!compilationResult) {
                                        method.setBody(oldBody);
                                        failCompiled.add(record);
                                    } else {
                                        successCompiled.add(record);
                                    }
                                    FilesUtils.writeJavaFile(tempTestClassPath, cu);
                                    FilesUtils.writeJavaFile(modifiedTestClassPath, cu);
                                    testClassesTempPathsMap.put(absoluteTestClassPath.toString(), cu);
                                } catch (Exception e) {
                                    System.err.println("Error parsing the new body: " + e.getMessage());
                                }
                            }
                        }
                        Path modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", "_" + testType.getTestType() + ".java"));
                        Path tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + "_" + testType.getTestType() + ".java");
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
//            Path tempTestClassPath = tempTestsClassesPath.resolve(finalTestClass.getNameAsString() + "_" + testType.getTestType() + ".java");
//            finalTestClass.setName(finalTestClass.getNameAsString() + "_" + testType.getTestType());
//            FilesUtils.writeJavaFile(tempTestClassPath, finalCu);
//        }
        FilesUtils.writeCSV(repoRootPath.resolve("star_classes_mapping.csv"), testClassesPathsMap);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "successCompiled.csv"), successCompiled);
        FilesUtils.writeCSV(repoRootPath.resolve(testType + "failCompiled.csv"), failCompiled);
        String result = "";
        for (int i = 0; i < testClassesProcessed.size(); i++) {
            if (i == testClassesProcessed.size() - 1) {
                result += testClassesProcessed.get(i);
            } else {
                result += testClassesProcessed.get(i) + ",";
            }
        }
        System.out.println(result);
    }

    private static boolean bashCall(String[] args) {
        try {
            ProcessBuilder builder = new ProcessBuilder(args);
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
