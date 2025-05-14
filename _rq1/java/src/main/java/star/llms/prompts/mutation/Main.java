package star.llms.prompts.mutation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.javatuples.Pair;
import star.llms.prompts.mutation.data.records.PromptInfo;
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

        FilesUtils.createDirectories(tempTestsClassesPath);

        // Project identifier
        List<PromptInfo> promptsInfos = Arrays.asList(FilesUtils.readJSON(promptInfoPath, PromptInfo[].class));
        List<List<String>> inference = FilesUtils.readCSV(inferenceFilePath);

        List<List<String>> testClassesPathsMap = new ArrayList<>();

        for (List<String> inferenceLine : inference) {
            for (PromptInfo promptInfo : promptsInfos) {
                // Check if the inference line matches the prompt info
                if (inferenceLine.get(0).equals(promptInfo.id())) {
                    Path absoluteTestClassPath = repoRootPath.resolve(promptInfo.testClassPath());
                    JavaParser javaParser = new JavaParser();
                    try {
                        // Parse the test class
                        CompilationUnit cu = javaParser.parse(absoluteTestClassPath).getResult().orElseThrow();
                        TypeDeclaration testClass = cu.getPrimaryType().get();
                        // Get the list of all the methods defined within the test class
                        List<MethodDeclaration> testClassMethods = cu.findAll(MethodDeclaration.class);

                        for (MethodDeclaration method : testClassMethods) {
                            // Check if the method name matches the prompt info
                            if (promptInfo.signature().contains(method.getSignature().toString())) {
                                try {
                                    Pattern pattern = Pattern.compile("\\[oracle\\](.*?)\\[/oracle\\]", Pattern.DOTALL);
                                    Matcher matcher = pattern.matcher(inferenceLine.get(4));
                                    if (!matcher.find()) {
                                        throw new IllegalArgumentException("No oracle found in the inference line.");
                                    }
                                    String extracted = matcher.group(1);
                                    String newBodyStr = promptInfo.testPrefixBody().replace("/*<MASK_PLACEHOLDER>*/", extracted);
                                    BlockStmt newBody = javaParser.parseBlock(newBodyStr).getResult().get();
                                    method.setBody(newBody);
                                } catch (Exception e) {
                                    System.out.println("Error parsing the new body: " + e.getMessage());
                                }
                            }
                        }
                        // Save the modified test class
                        Path modifiedTestClassPath = Path.of(absoluteTestClassPath.toString().replace(".java", "_inference.java"));
                        Path tempTestClassPath = tempTestsClassesPath.resolve(testClass.getNameAsString() + "_inference.java");
                        List<String> mappingPaths = new ArrayList<>();
                        testClassesPathsMap.add(mappingPaths);
                        testClass.setName(testClass.getNameAsString() + "_inference");
                        FilesUtils.writeJavaFile(tempTestClassPath, cu);
                        mappingPaths.add(tempTestClassPath.toString());
                        mappingPaths.add(modifiedTestClassPath.toString());
                        mappingPaths.add(cu.getPackageDeclaration().get().getNameAsString() + "." + testClass.getNameAsString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        FilesUtils.writeCSV(repoRootPath.resolve("star_classes_mapping.csv"), testClassesPathsMap);
    }
}
