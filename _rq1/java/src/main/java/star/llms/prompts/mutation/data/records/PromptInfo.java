package star.llms.prompts.mutation.data.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a repository track, which maps a JSON object containing information
 * about a repository's tracking data. This includes the repository URL, the time
 * range of the tracking, commits retrieved, and test cases tracked.
 *
 * @param id The identifier of the repository.
 * @param src The prompt input
 * @param tgt The target prompt output (assertion to guess)
 * @param testClassPath The path to the test class.
 * @param testPrefixBody The body of the test case.
 * @param signature The method signature of the test case.
 * @param exceeded Indicates if the prompt exceeded the token limit.
 * @param numTokens The number of tokens in the prompt.
 *
 */
public record PromptInfo(
        @JsonProperty("id") String id,
        @JsonProperty("src") String src,
        @JsonProperty("tgt") String tgt,
        @JsonDeserialize(using = PathDeserializer.class)
        @JsonProperty("test_class_path") Path testClassPath,
        @JsonProperty("signature") String signature,
        @JsonProperty("tp_body") String testPrefixBody,
        @JsonProperty("exceeded") boolean exceeded,
        @JsonProperty("num_tokens") int numTokens
) {
    // Static nested deserializer inside the record
    public static class PathDeserializer extends JsonDeserializer<Path> {
        @Override
        public Path deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            // Get the raw path string
            String pathString = p.getValueAsString();

            // Remove the leading slash if present
            if (pathString.startsWith("/")) {
                pathString = pathString.substring(1);
            }

            // Return the cleaned path
            return Paths.get(pathString);
        }
    }

}