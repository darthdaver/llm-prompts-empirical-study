package star.llms.prompts.dataset.data.exceptions;

public class MultipleCandidatesException extends RuntimeException {
    public MultipleCandidatesException(String message) {
        super(message);
    }
}
