package star.llms.prompts.mutation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import star.llms.prompts.mutation.data.records.PromptInfo;
import star.llms.prompts.mutation.data.records.TestType;
import star.llms.prompts.mutation.utils.FilesUtils;

import java.io.IOException;
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
        TestType testType = TestType.valueOf(args[4].toUpperCase());



        FilesUtils.createDirectories(tempTestsClassesPath);

        // Project identifier
        List<PromptInfo> promptsInfos = Arrays.asList(FilesUtils.readJSON(promptInfoPath, PromptInfo[].class));
        List<List<String>> inference = FilesUtils.readCSV(inferenceFilePath);

        List<List<String>> testClassesPathsMap = new ArrayList<>();

        HashMap<String, CompilationUnit> testClassesTempPathsMap = new HashMap<>();

        CompilationUnit cu = null;
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
                                    if (testType == TestType.INFERENCE) {
                                        Pattern pattern = Pattern.compile("\\[oracle\\](.*?)\\[/oracle\\]", Pattern.DOTALL);
                                        Matcher matcher = pattern.matcher(inferenceLine.get(4));
                                        if (!matcher.find()) {
                                            throw new IllegalArgumentException("No oracle found in the inference line.");
                                        }
                                        String extracted = matcher.group(1);
                                        newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", extracted);
                                    } else if (testType == TestType.NO_ORACLE) {
                                        newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", "");
                                    }
                                    BlockStmt newBody = javaParser.parseBlock(newBodyStr).getResult().get();
                                    method.setBody(newBody);
                                    testClassesTempPathsMap.put(absoluteTestClassPath.toString(), cu);
                                } catch (Exception e) {
                                    System.out.println("Error parsing the new body: " + e.getMessage());
                                }
                            }
                        }
                        Path modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", "_" + testType.getTestType() + ".java"));
                        Path tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + "_" + testType.getTestType() + ".java");
                        List<String> mappingPaths = new ArrayList<>();
                        testClassesPathsMap.add(mappingPaths);
                        mappingPaths.add(tempTestClassPath.toString());
                        mappingPaths.add(modifiedTestClassPath.toString());
                        mappingPaths.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                        mappingPaths.add(inferenceLine.get(0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Write the modified test classes to the temporary path
        for (String absoluteTestClassPath : testClassesTempPathsMap.keySet()) {
            CompilationUnit finalCu = testClassesTempPathsMap.get(absoluteTestClassPath);
            TypeDeclaration finalTestClass = finalCu.getPrimaryType().get();
            // Save the modified test class
            Path tempTestClassPath = tempTestsClassesPath.resolve(finalTestClass.getNameAsString() + "_" + testType.getTestType() + ".java");
            finalTestClass.setName(finalTestClass.getNameAsString() + "_" + testType.getTestType());
            FilesUtils.writeJavaFile(tempTestClassPath, finalCu);
        }

        FilesUtils.writeCSV(repoRootPath.resolve("star_classes_mapping.csv"), testClassesPathsMap);
    }
}
