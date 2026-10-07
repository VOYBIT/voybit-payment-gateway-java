package com.voybit.paymentgateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {
    }

    static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("response was not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) map;
        return object;
    }

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.done()) {
            throw new IllegalArgumentException("response was not JSON");
        }
        return value;
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String text) {
            writeString(out, text);
            return;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            out.append(value);
            return;
        }
        if (value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, key);
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
            return;
        }
        throw new IllegalArgumentException("unsupported JSON value");
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private boolean done() {
            return index >= text.length();
        }

        private Object parseValue() {
            skipWhitespace();
            if (done()) {
                throw new IllegalArgumentException("response was not JSON");
            }
            return switch (text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            index++;
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (done() || text.charAt(index) != '"') {
                    throw new IllegalArgumentException("response was not JSON");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            index++;
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return values;
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return values;
                }
                expect(',');
            }
        }

        private String parseString() {
            index++;
            StringBuilder out = new StringBuilder();
            while (!done()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return out.toString();
                }
                if (character == '\\') {
                    if (done()) {
                        throw new IllegalArgumentException("response was not JSON");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> out.append(escaped);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> out.append(parseHex());
                        default -> throw new IllegalArgumentException("response was not JSON");
                    }
                } else if (character < 0x20) {
                    throw new IllegalArgumentException("response was not JSON");
                } else {
                    out.append(character);
                }
            }
            throw new IllegalArgumentException("response was not JSON");
        }

        private char parseHex() {
            if (index + 4 > text.length()) {
                throw new IllegalArgumentException("response was not JSON");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw new IllegalArgumentException("response was not JSON");
                }
                code = (code << 4) + digit;
            }
            return (char) code;
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw new IllegalArgumentException("response was not JSON");
            }
            index += word.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (consume('-')) {
                // sign consumed
            }
            if (done() || !isDigit(text.charAt(index))) {
                throw new IllegalArgumentException("response was not JSON");
            }
            if (text.charAt(index) == '0') {
                index++;
            } else {
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            boolean fractional = false;
            if (!done() && text.charAt(index) == '.') {
                fractional = true;
                index++;
                if (done() || !isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException("response was not JSON");
                }
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!done() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                fractional = true;
                index++;
                if (!done() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                if (done() || !isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException("response was not JSON");
                }
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String token = text.substring(start, index);
            try {
                if (fractional) {
                    return Double.valueOf(token);
                }
                return Long.valueOf(token);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("response was not JSON");
            }
        }

        private void skipWhitespace() {
            while (!done()) {
                char character = text.charAt(index);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                    return;
                }
                index++;
            }
        }

        private boolean consume(char expected) {
            if (done() || text.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw new IllegalArgumentException("response was not JSON");
            }
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }
    }
}
