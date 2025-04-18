package star.llms.prompts.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import star.llms.prompts.dataset.data.enums.NamingConvention;
import star.llms.prompts.dataset.preprocessing.OraclesDataset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // Process arguments
        Path repoRootPath = Path.of(args[0]);
        Path repoTrackPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        String classpath = initializeJavaClassPath(Path.of(args[3]));
        Path configPath = Path.of(args[4]);
        // Project identifier
        String projectIdentifier = args[0].substring(args[0].lastIndexOf("/") + 1);
        try {
            OraclesDataset.generate(repoRootPath, repoTrackPath, configPath, outputPath, projectIdentifier, classpath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String initializeJavaClassPath(Path basePath) {
        String classpath = "";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Map<String, List<String>> javaVersions = objectMapper.readValue(
                    basePath.resolve(NamingConvention.JAVA_CLASSPATHS_JSON.getConventionName()).toFile(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, List.class)
            );
            for (String javaVersion : javaVersions.keySet()) {
                Path javaVersionPath = basePath.resolve("lib/java" + javaVersion);
                for (String javaLib : javaVersions.get(javaVersion)) {
                    Path javaLibPath = javaVersionPath.resolve(javaLib);
                    classpath += classpath.isEmpty() ? javaLibPath : ":" + javaLibPath;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classpath;
    }
}
