package ai.hiapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON utility.
 *
 * <p>{@link #parse(String)} performs recursive-descent parsing into plain Java
 * containers ({@link Map}, {@link List}) and scalars ({@link String},
 * {@link Double}, {@link Boolean}, {@code null}). {@link #write(Object)} emits
 * compact JSON from the same value shapes.
 *
 * <p>Numbers always parse to {@link Double}; the wire models cast whole-number
 * doubles (epoch seconds) to {@link Long} via {@code doubleValue()}, and
 * {@link #write(Object)} renders whole-number doubles without a decimal point or
 * scientific notation so they round-trip cleanly.
 */
class Json {

    private Json() {
    }

    // ---------------------------------------------------------------------
    // Parsing (recursive descent)
    // ---------------------------------------------------------------------

    /**
     * Parses a JSON document.
     *
     * @param s the JSON text; must not be {@code null}
     * @return a {@link Map}, {@link List}, {@link String}, {@link Double},
     *         {@link Boolean}, or {@code null}
     * @throws IllegalArgumentException if the input is malformed or has trailing
     *                                  content
     */
    static Object parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("JSON input is null");
        }
        Parser p = new Parser(s);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new IllegalArgumentException(
                    "Unexpected trailing content at position " + p.pos);
        }
        return value;
    }

    /** Internal mutable cursor over the source string. */
    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.pos = 0;
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
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
                throw new IllegalArgumentException("Unexpected end of input");
            }
            return src.charAt(pos);
        }

        Object parseValue() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char c = src.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new IllegalArgumentException(
                            "Unexpected character '" + c + "' at position " + pos);
            }
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new IllegalArgumentException(
                            "Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return result;
                }
                throw new IllegalArgumentException(
                        "Expected ',' or '}' at position " + pos);
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<Object>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return result;
                }
                throw new IllegalArgumentException(
                        "Expected ',' or ']' at position " + pos);
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException(
                            "Unterminated string literal");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IllegalArgumentException(
                                "Unterminated escape sequence");
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
                            sb.append(parseUnicodeEscape());
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Invalid escape '\\" + esc + "' at position "
                                            + (pos - 1));
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException(
                            "Unescaped control character in string at position "
                                    + (pos - 1));
                } else {
                    sb.append(c);
                }
            }
        }

        private char parseUnicodeEscape() {
            if (pos + 4 > src.length()) {
                throw new IllegalArgumentException(
                        "Incomplete \\u escape at position " + pos);
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                char hc = src.charAt(pos++);
                int digit = hexDigit(hc);
                if (digit < 0) {
                    throw new IllegalArgumentException(
                            "Invalid hex digit '" + hc + "' in \\u escape");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private static int hexDigit(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            return -1;
        }

        private Double parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            // integer part
            if (atEnd()) {
                throw new IllegalArgumentException("Invalid number at position " + start);
            }
            char first = src.charAt(pos);
            if (first == '0') {
                pos++;
            } else if (first >= '1' && first <= '9') {
                pos++;
                consumeDigits();
            } else {
                throw new IllegalArgumentException("Invalid number at position " + start);
            }
            // fraction
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                if (pos >= src.length() || !isDigit(src.charAt(pos))) {
                    throw new IllegalArgumentException(
                            "Invalid fraction in number at position " + pos);
                }
                consumeDigits();
            }
            // exponent
            if (pos < src.length()
                    && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length()
                        && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= src.length() || !isDigit(src.charAt(pos))) {
                    throw new IllegalArgumentException(
                            "Invalid exponent in number at position " + pos);
                }
                consumeDigits();
            }
            String literal = src.substring(start, pos);
            try {
                return Double.valueOf(literal);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid number literal '" + literal + "'", e);
            }
        }

        private void consumeDigits() {
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException(
                    "Invalid literal at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException(
                    "Invalid literal at position " + pos);
        }

        private void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new IllegalArgumentException(
                        "Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }

    // ---------------------------------------------------------------------
    // Writing (compact)
    // ---------------------------------------------------------------------

    /**
     * Serializes a value to compact JSON.
     *
     * @param o a {@link Map}, {@link List}, {@link String}, {@link Number},
     *          {@link Boolean}, or {@code null}
     * @return the compact JSON text
     * @throws IllegalArgumentException if a value of an unsupported type is
     *                                  encountered
     */
    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, o);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Boolean) {
            sb.append(((Boolean) o).booleanValue() ? "true" : "false");
        } else if (o instanceof Number) {
            writeNumber(sb, (Number) o);
        } else if (o instanceof Map) {
            writeObject(sb, (Map<?, ?>) o);
        } else if (o instanceof List) {
            writeArray(sb, (List<?>) o);
        } else {
            throw new IllegalArgumentException(
                    "Cannot serialize value of type " + o.getClass().getName());
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
            Object key = entry.getKey();
            writeString(sb, key == null ? "null" : key.toString());
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

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException(
                        "Cannot serialize non-finite number: " + d);
            }
            // Whole-number doubles (e.g. epoch seconds) emit without a decimal
            // point or scientific notation so they round-trip as integers.
            if (d == Math.rint(d) && !Double.isInfinite(d)
                    && Math.abs(d) < 1e15) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(Double.toString(d));
            }
        } else {
            // Long / Integer / Short / Byte / BigInteger / BigDecimal: their
            // toString() is already valid, decimal-notation JSON.
            sb.append(n.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        int len = s.length();
        for (int i = 0; i < len; i++) {
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
                        sb.append(HEX[(c >> 12) & 0xF]);
                        sb.append(HEX[(c >> 8) & 0xF]);
                        sb.append(HEX[(c >> 4) & 0xF]);
                        sb.append(HEX[c & 0xF]);
                    } else {
                        // Non-ASCII is emitted raw (UTF-8 on the wire).
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    private static final char[] HEX = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
}
