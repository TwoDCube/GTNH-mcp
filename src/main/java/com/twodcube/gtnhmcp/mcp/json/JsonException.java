package com.twodcube.gtnhmcp.mcp.json;

/**
 * Thrown when {@link JsonParser} encounters malformed JSON.
 *
 * <p>
 * The message always includes the character offset at which parsing failed so that protocol-level error responses can
 * point an operator at the exact location of the problem, rather than surfacing an opaque {@link NullPointerException}
 * or {@link IndexOutOfBoundsException} from deep inside the parser.
 */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Character offset (0-based) into the source text at which the error was detected. */
    private final int offset;

    /**
     * @param message human-readable description of what was expected versus found.
     * @param offset  0-based character offset into the parsed text where the error occurred.
     */
    public JsonException(String message, int offset) {
        super(message + " (at offset " + offset + ")");
        this.offset = offset;
    }

    /** @return the 0-based character offset into the source text at which parsing failed. */
    public int offset() {
        return offset;
    }
}
