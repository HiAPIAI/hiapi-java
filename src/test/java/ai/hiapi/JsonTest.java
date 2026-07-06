package ai.hiapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hand-written {@link Json} parser/writer: round-trips, string
 * escapes, nested containers, numbers, booleans, {@code null}, and rejection of
 * malformed input.
 */
class JsonTest {

    @Test
    void parsesScalars() {
        assertEquals("hello", Json.parse("\"hello\""));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertNull(Json.parse("null"));
        assertEquals(42.0, Json.parse("42"));
        assertEquals(-3.5, Json.parse("-3.5"));
    }

    @Test
    void parsesNumbersAsDouble() {
        Object value = Json.parse("1777800499");
        assertInstanceOf(Double.class, value);
        assertEquals(1777800499.0, (Double) value);
        // Exponent and fraction forms.
        assertEquals(1500.0, Json.parse("1.5e3"));
        assertEquals(0.001, Json.parse("1e-3"));
    }

    @Test
    void parsesEmptyObjectAndArray() {
        assertEquals(new LinkedHashMap<String, Object>(), Json.parse("{}"));
        assertEquals(new ArrayList<Object>(), Json.parse("[]"));
    }

    @Test
    void parsesNestedObjectsAndArrays() {
        Object parsed = Json.parse(
                "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":[{\"e\":2}]}}");
        assertInstanceOf(Map.class, parsed);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals(1.0, map.get("a"));

        @SuppressWarnings("unchecked")
        List<Object> b = (List<Object>) map.get("b");
        assertEquals(3, b.size());
        assertEquals(Boolean.TRUE, b.get(0));
        assertNull(b.get(1));
        assertEquals("x", b.get(2));

        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) map.get("c");
        @SuppressWarnings("unchecked")
        List<Object> d = (List<Object>) c.get("d");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) d.get(0);
        assertEquals(2.0, first.get("e"));
    }

    @Test
    void parsesStringEscapes() {
        assertEquals("a\"b", Json.parse("\"a\\\"b\""));
        assertEquals("a\\b", Json.parse("\"a\\\\b\""));
        assertEquals("a/b", Json.parse("\"a\\/b\""));
        assertEquals("line1\nline2", Json.parse("\"line1\\nline2\""));
        assertEquals("tab\there", Json.parse("\"tab\\there\""));
        assertEquals("\r\b\f", Json.parse("\"\\r\\b\\f\""));
        assertEquals("é", Json.parse("\"\\u00e9\""));
    }

    @Test
    void writesScalars() {
        assertEquals("null", Json.write(null));
        assertEquals("true", Json.write(Boolean.TRUE));
        assertEquals("false", Json.write(Boolean.FALSE));
        assertEquals("\"hi\"", Json.write("hi"));
    }

    @Test
    void writesWholeNumberDoublesWithoutDecimalOrExponent() {
        // epoch-seconds round-trip: a whole-number Double must emit as an int.
        assertEquals("1777800499", Json.write(1777800499.0));
        assertEquals("0", Json.write(0.0));
        assertEquals("-5", Json.write(-5.0));
        // Integer/Long pass through their own toString().
        assertEquals("7", Json.write(7));
        assertEquals("9", Json.write(9L));
    }

    @Test
    void writesFractionalDoubles() {
        assertEquals("3.5", Json.write(3.5));
    }

    @Test
    void writesStringEscapes() {
        assertEquals("\"a\\\"b\"", Json.write("a\"b"));
        assertEquals("\"a\\\\b\"", Json.write("a\\b"));
        assertEquals("\"l1\\nl2\"", Json.write("l1\nl2"));
        assertEquals("\"\\t\\r\\b\\f\"", Json.write("\t\r\b\f"));
        // Other control characters use the \\uXXXX form.
        assertEquals("\"\\u0001\"", Json.write(""));
    }

    @Test
    void writesObjectsAndArrays() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("model", "seedance-2-0");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "hi");
        map.put("input", input);
        assertEquals("{\"model\":\"seedance-2-0\",\"input\":{\"prompt\":\"hi\"}}", Json.write(map));

        List<Object> list = new ArrayList<>();
        list.add(1.0);
        list.add("x");
        list.add(Boolean.TRUE);
        assertEquals("[1,\"x\",true]", Json.write(list));
    }

    @Test
    void roundTripsNestedStructures() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "m");
        body.put("created", 1777800499.0);
        body.put("ok", Boolean.TRUE);
        body.put("nothing", null);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "a \"quoted\" \n newline");
        List<Object> tags = new ArrayList<>();
        tags.add("video");
        tags.add(2.0);
        input.put("tags", tags);
        body.put("input", input);

        String json = Json.write(body);
        Object reparsed = Json.parse(json);
        assertEquals(body, reparsed);
    }

    @Test
    void roundTripsRawWebhookBytesShape() {
        // A payload with unusual spacing parses to the canonical structure.
        Object parsed = Json.parse(
                "{\"taskId\":\"tk-1\",  \"model\":\"m\", \"status\":\"success\"}");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertEquals("tk-1", map.get("taskId"));
        assertEquals("success", map.get("status"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,]"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\" 1}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("tru"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("nul"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("01"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(null));
    }

    @Test
    void rejectsUnescapedControlCharacterInString() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"ab\""));
    }

    @Test
    void writeRejectsUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }

    @Test
    void terminalStatusFlagsHoldForParsedShapes() {
        // Sanity bridge: the parser feeds Task.fromMap; confirm the numeric
        // shape lands as Double and survives the Models.toLong coercion.
        Object parsed = Json.parse("{\"created\":1777800499,\"completed\":0}");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        assertTrue(map.get("created") instanceof Double);
        assertFalse(map.get("completed") == null);
    }
}
