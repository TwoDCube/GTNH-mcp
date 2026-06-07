package com.twodcube.gtnhmcp.mcp.json;

import java.util.List;
import java.util.Map;

/**
 * Serializes the plain-Java JSON model produced/consumed by {@link JsonParser} back into compact JSON text.
 *
 * <p>
 * Accepted input types mirror {@link JsonParser}'s output:
 * <ul>
 * <li>{@link Map}&lt;String, ?&gt; &rarr; object (keys are stringified via {@link String#valueOf})</li>
 * <li>{@link List}&lt;?&gt; or {@code Object[]} &rarr; array</li>
 * <li>{@link CharSequence} &rarr; quoted, escaped string</li>
 * <li>{@link Boolean} &rarr; {@code true}/{@code false}</li>
 * <li>{@link Number} &rarr; numeric literal (integers without a decimal point; non-finite doubles rejected)</li>
 * <li>{@code null} &rarr; {@code null}</li>
 * </ul>
 *
 * <p>
 * Output is compact (no insignificant whitespace) because it is consumed by a protocol client, not a human. Object key
 * order follows the map's iteration order, which is why the protocol layer uses {@link java.util.LinkedHashMap} for
 * deterministic, test-friendly output.
 */
public final class JsonWriter {

    private JsonWriter() {}

    /**
     * Serialize a value to compact JSON.
     *
     * @param value any value from the accepted type set (see class docs); {@code null} produces the literal
     *              {@code null}.
     * @return the JSON text.
     * @throws IllegalArgumentException if {@code value} (or a nested value) is of an unsupported type, or is a
     *                                  non-finite
     *                                  double ({@code NaN}/{@code Infinity}), which JSON cannot represent.
     */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof CharSequence) {
            writeString(sb, value.toString());
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value).booleanValue() ? "true" : "false");
        } else if (value instanceof Number) {
            writeNumber(sb, (Number) value);
        } else if (value instanceof Map) {
            writeObject(sb, (Map<?, ?>) value);
        } else if (value instanceof List) {
            writeArray(sb, (List<?>) value);
        } else if (value instanceof Object[]) {
            writeArray(sb, java.util.Arrays.asList((Object[]) value));
        } else {
            throw new IllegalArgumentException(
                "cannot serialize value of type " + value.getClass()
                    .getName());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(sb, item);
        }
        sb.append(']');
    }

    private static void writeNumber(StringBuilder sb, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON cannot represent non-finite number: " + d);
            }
            // Render whole-valued doubles without a trailing ".0" so ids/counts stay clean.
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(Double.toString(d));
            }
        } else {
            // Long, Integer, Short, Byte, BigInteger — exact integral rendering.
            sb.append(number.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
