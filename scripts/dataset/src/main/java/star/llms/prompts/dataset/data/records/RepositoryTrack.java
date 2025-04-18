package star.llms.prompts.dataset.data.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Represents a repository track, which maps a JSON object containing information
 * about a repository's tracking data. This includes the repository URL, the time
 * range of the tracking, commits retrieved, and test cases tracked.
 *
 * @param url     The URL of the repository being tracked.
 * @param since   The start date of the tracking period (ISO 8601 format).
 * @param until   The end date of the tracking period (ISO 8601 format).
 * @param commits A map of commit identifiers to lists of commit details. The key represents the name of the files
 *                tracked, and the value is a list containing the details of the commits where the file was added or modified.
 * @param track   A map of test cases added or modified in the repository, during the tracking period. The key represents
 *                the name of the files tracked, and the value is a list containing the details of the test cases added
 *                or modified in the file, during the tracking period.
 *
 */
public record RepositoryTrack(
        @JsonProperty("url") String url,
        @JsonProperty("since") String since,
        @JsonProperty("until") String until,
        @JsonProperty("commits") HashMap<String, List<Commit>> commits,
        @JsonProperty("track") HashMap<String, List<TestCase>> track
) {

    /**
     * Represents a commit in the repository, including metadata and changes.
     *
     * @param idx                  The custom index of the commit in the tracking sequence.
     * @param sha                  The SHA hash of the commit.
     * @param date                 The date of the commit (ISO 8601 format).
     * @param oldPath              The file path before the commit.
     * @param newPath              The file path after the commit.
     * @param addedLines           The number of lines added in the commit.
     * @param deletedLines         The number of lines deleted in the commit.
     * @param diffParsed           The parsed diff of the commit, showing added and deleted lines.
     * @param srcCodeBefore        The source code before the commit.
     * @param srcCodeAfter         The source code after the commit.
     * @param methodsBeforeCommit  The test methods present before the commit (name, signature, body, full-test).
     * @param methodsAfterCommit   The test methods present after the commit (name, signature, body, full-test).
     * @param deletedMethods        The test methods deleted in the commit (name, signature, body, full-test).
     * @param addedMethods           The test methods added in the commit (name, signature, body, full-test).
     * @param changedMethods       The test methods changed in the commit (name, signature, body, full-test).
     */
    public record Commit(
            @JsonProperty("idx") int idx,
            @JsonProperty("commit_sha") String sha,
            @JsonProperty("commit_date") String date,
            @JsonProperty("old_path") String oldPath,
            @JsonProperty("new_path") String newPath,
            @JsonProperty("added_lines") int addedLines,
            @JsonProperty("deleted_lines") int deletedLines,
            @JsonProperty("diff_parsed") DiffParsed diffParsed,
            @JsonProperty("src_code_before") String srcCodeBefore,
            @JsonProperty("src_code_after") String srcCodeAfter,
            @JsonProperty("methods_before_commit") List<TestCase> methodsBeforeCommit,
            @JsonProperty("methods_after_commit") List<TestCase> methodsAfterCommit,
            @JsonProperty("deleted_methods") List<TestCase> deletedMethods,
            @JsonProperty("added_methods") List<TestCase> addedMethods,
            @JsonProperty("changed_methods") List<TestCase> changedMethods
    ) {

        /**
         * Represents the parsed diff of a commit, showing added and deleted lines.
         *
         * @param added   A list of added lines, represented as pairs of line numbers and content.
         * @param deleted A list of deleted lines, represented as pairs of line numbers and content.
         */
        public record DiffParsed(
                @JsonProperty("added") List<Line> added,
                @JsonProperty("deleted") List<Line> deleted
        ) {}

        /**
         * Represents a single line in a diff.
         *
         * @param lineNumber The line number in the file.
         * @param content    The content of the line.
         */
        @JsonDeserialize(using = Line.LineDeserializer.class)
        public record Line(
                @JsonProperty("lineNumber") int lineNumber,
                @JsonProperty("content") String content
        ) {
            public static class LineDeserializer extends JsonDeserializer<Line> {
                @Override
                public Line deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    JsonNode node = p.getCodec().readTree(p);
                    if (!node.isArray() || node.size() != 2) {
                        throw new IOException("Expected array of two elements for Line");
                    }
                    int lineNumber = node.get(0).asInt();
                    String content = node.get(1).asText();
                    return new Line(lineNumber, content);
                }
            }
        }
    }

    /**
     * Represents a test case in a test class of the repository.
     *
     * @param name       The name of the test case.
     * @param signature  The method signature of the test case.
     * @param body       The body of the test case.
     * @param fullMethod the full test case.
     */
    public record TestCase(
            @JsonProperty("name") String name,
            @JsonProperty("signature") String signature,
            @JsonProperty("body") String body,
            @JsonProperty("full_method") String fullMethod
    ) {}
}