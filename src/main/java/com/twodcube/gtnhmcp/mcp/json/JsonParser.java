package com.twodcube.gtnhmcp.mcp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free, single-pass JSON parser.
 *
 * <p>
 * This module exists so the MCP/JSON-RPC protocol layer can be implemented and unit-tested with <em>no</em> dependency
 * on Minecraft, Forge, or a third-party JSON library. The Minecraft 1.7.10 runtime does bundle Gson, but pulling it
 * onto
 * the test classpath couples our protocol tests to the game runtime; a few hundred lines of audited parser/writer keep
 * the protocol independently testable, which is the bar this project holds itself to.
 *
 * <p>
 * The parser maps JSON onto plain Java types so callers never have to learn a bespoke node API:
 * <ul>
 * <li>object &rarr; {@link LinkedHashMap}&lt;String, Object&gt; (insertion order preserved for stable output)</li>
 * <li>array &rarr; {@link ArrayList}&lt;Object&gt;</li>
 * <li>string &rarr; {@link String}</li>
 * <li>number &rarr; {@link Long} when it is integral and fits in a long, otherwise {@link Double}</li>
 * <li>true / false &rarr; {@link Boolean}</li>
 * <li>null &rarr; Java {@code null}</li>
 * </ul>
 *
 * <p>
 * It implements RFC 8259 grammar for values. Trailing content after a top-level value is rejected. All failures raise
 * {@link JsonException} carrying the offset of the offending character.
 */
public final class JsonParser {

    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
    }

    /**
     * Parse a complete JSON document.
     *
     * @param text the JSON text; must contain exactly one top-level value (optionally surrounded by whitespace).
     * @return the parsed value mapped onto plain Java types (see class docs).
     * @throws JsonException if {@code text} is null, empty, malformed, or has trailing non-whitespace content.
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("JSON text is null", 0);
        }
        JsonParser p = new JsonParser(text);
        p.skipWhitespace();
        if (p.pos >= p.src.length()) {
            throw new JsonException("empty JSON document", 0);
        }
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos != p.src.length()) {
            throw new JsonException("unexpected trailing content", p.pos);
        }
        return value;
    }

    private Object readValue() {
        char c = peek();
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
            case 'f':
                return readBoolean();
            case 'n':
                return readNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return readNumber();
                }
                throw new JsonException("unexpected character '" + c + "'", pos);
        }
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("expected object key string", pos);
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}' in object", pos - 1);
            }
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']' in array", pos - 1);
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new JsonException("unterminated string", pos);
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new JsonException("unterminated escape sequence", pos);
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(readUnicodeEscape());
                        break;
                    default:
                        throw new JsonException("invalid escape '\\" + esc + "'", pos - 1);
                }
            } else if (c < 0x20) {
                throw new JsonException("unescaped control character in string", pos - 1);
            } else {
                sb.append(c);
            }
        }
    }

    private char readUnicodeEscape() {
        if (pos + 4 > src.length()) {
            throw new JsonException("truncated \\u escape", pos);
        }
        int code = 0;
        for (int i = 0; i < 4; i++) {
            char h = src.charAt(pos++);
            int d = Character.digit(h, 16);
            if (d < 0) {
                throw new JsonException("invalid hex digit '" + h + "' in \\u escape", pos - 1);
            }
            code = (code << 4) | d;
        }
        return (char) code;
    }

    private Object readNumber() {
        int start = pos;
        boolean integral = true;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length() && isDigit(src.charAt(pos))) {
            pos++;
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            integral = false;
            pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            integral = false;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String token = src.substring(start, pos);
        if (token.isEmpty() || "-".equals(token)) {
            throw new JsonException("invalid number", start);
        }
        if (integral) {
            try {
                return Long.valueOf(Long.parseLong(token));
            } catch (NumberFormatException overflow) {
                // Falls through to double for integers that exceed long range.
                return Double.valueOf(Double.parseDouble(token));
            }
        }
        return Double.valueOf(Double.parseDouble(token));
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("invalid literal", pos);
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonException("invalid literal", pos);
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new JsonException("unexpected end of input", pos);
        }
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw new JsonException("unexpected end of input", pos);
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        char actual = next();
        if (actual != c) {
            throw new JsonException("expected '" + c + "' but found '" + actual + "'", pos - 1);
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
