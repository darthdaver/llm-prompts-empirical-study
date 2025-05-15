package star.llms.prompts.mutation.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.CompilationUnit;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides file input and output utilities, such as: creating, copying, moving, writing, reading, etc.
 */
public class FilesUtils {
    private static final Logger log = LoggerFactory.getLogger(FilesUtils.class);

    /** Do not instantiate this class. */
    private FilesUtils() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * Creates an empty directory. Creates parent directories if necessary. If
     * the directory already exists, then this method does nothing. <br> This
     * method is a wrapper method of {@link Files#createDirectories(Path, FileAttribute[])}
     * to substitute {@link IOException} with {@link Error} and avoid
     * superfluous try/catch blocks.
     *
     * @param path a path
     * @throws Error if an error occurs while creating the directory
     */
    public static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new Error("Error when creating directory " + path, e);
        }
    }

    /**
     * Creates an empty file. Creates parent directories if necessary. If the
     * file already exists, then this method does nothing.
     *
     * @param path a file
     * @throws Error if an error occurs while creating the parent directories
     * or new file
     */
    public static void createFile(Path path) {
        try {
            createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new Error("Error when creating file " + path, e);
        }
    }

    /**
     * Converts a fully qualified class name into a relative file Path. For
     * example,
     *     {@code com.example.MyClass}    -&gt;
     *     {@code com/example/MyClass.java}
     *
     * @param fullyQualifiedName a fully qualified class name
     * @return the path corresponding to the fully qualified class name
     */
    public static Path getFQNPath(String fullyQualifiedName) {
        return Paths.get(fullyQualifiedName.replaceAll("[.]", "/") + ".java");
    }

    /**
     * Get the absolute path of the SymbolicModule project root.
     */
    public static Path getProjectRootAbsolutePath() {
        // Get the absolute path to the current class:
        // [path_to_root]/target/classes/star/tracto/utils/FilesUtils.class
        Path absPathToCurrentClass = Path.of(FilesUtils.class.getResource("FilesUtils.class").getPath());
        // Return the path to the root of the SymbolicModule project (6 levels up from the current class)
        return absPathToCurrentClass.getParent().getParent().getParent().getParent().getParent().getParent();
    }

    /**
     * Reads a JSON file and maps it to a Java record.
     *
     * @param path the path to the file
     * @param clazz the class of the objects to read
     * @return the Java record representing the JSON file
     * @throws Error if an error occurs while reading the file
     */
    public static <T> T readJSON(Path path, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new Error("Error when reading file " + path, e);
        }
    }

    public static void writeCSV(Path path, List<List<String>> rows) {
        try (CSVWriter writer = new CSVWriter(
                new FileWriter(path.toFile()),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)
        ) {
            for (List<String> row : rows) {
                writer.writeNext(row.toArray(new String[0]));
            }
        } catch (IOException e) {
            throw new Error("Error when writing to file " + path, e);
        }
    }

    public static List<List<String>> readCSV(Path path) {
        try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
            List<List<String>> records = new ArrayList<>();
            String[] line;
            while ((line = reader.readNext()) != null) {
                records.add(List.of(line));
            }
            return records;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
        return new ArrayList<>();
    }

    /**
     * Writes an object representing a compilation unit to a Java file.
     *
     * @param path the path to the file
     * @throws Error if an error occurs while writing the file
     */
    public static void writeJavaFile(Path path, CompilationUnit cu) {
        createFile(path);
        try {
            Files.write(path, cu.toString().getBytes());
        } catch (IOException e) {
            throw new Error("Error when writing to file " + path, e);
        }
    }

    /**
     * Writes a JSON string to a file.
     *
     * @param path the path to the file
     * @param json the JSON string to write
     * @throws Error if an error occurs while writing the file
     */
    public static void writeJSONFile(Path path, String json) {
        createFile(path);
        try {
            Files.write(path, json.getBytes());
        } catch (IOException e) {
            throw new Error("Error when writing to file " + path, e);
        }
    }

    /**
     * Recursively deletes a directory and its contents. If the file does not
     * exist, then this method does nothing.
     *
     * @param dirPath a directory
     * @throws Error if an error occurs while traversing or deleting files
     */
    public static void deleteDirectory(Path dirPath) {
        if (!Files.exists(dirPath)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dirPath)) {
            walk
                    .filter(p -> !p.equals(dirPath))
                    .forEach(p -> {
                        if (Files.isDirectory(p)) {
                            deleteDirectory(p);
                        } else {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new Error("Error when trying to delete the file " + p, e);
                            }
                        }
                    });
            // delete root directory last
            Files.delete(dirPath);
        } catch (IOException e) {
            throw new Error("Error when trying to delete the directory " + dirPath, e);
        }
    }

    /**
     * List the directories in the given path.
     * @param path the path to list directories from
     * @return the list of directories in the given path. An empty list if the path does not exist or is not a directory.
     */
    public static List<Path> listDirectories(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(path)) {  // Ensure the stream is closed
            return stream.filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error when trying to list directories in path " + path, e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if the given path has a child directory with the given name.
     * @param parent the parent path
     * @param child the name of the child directory
     * @return true if the parent path has a child directory with the given name, false otherwise.
     */
    public static boolean hasChildDirectory(Path parent, String child) {
        return listDirectories(parent).stream().anyMatch(p -> p.getFileName().toString().equals(child));
    }
}
