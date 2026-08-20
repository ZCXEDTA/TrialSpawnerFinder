package cn.trialfinder.sim.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON DOM 解析器——替代 gson，消除第三方运行时依赖。
 * 足够解析试炼密室 datapack 数据（模板池 pool JSON 与 structure JSON）：对象、数组、字符串（含转义）、
 * 整数、布尔、null。只读，不处理数字精度之外的需求。
 */
public final class Json {
    private Json() {
    }

    public static JsonValue parse(String text) {
        Parser parser = new Parser(text);
        JsonValue value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < text.length()) {
            throw new JsonParseException("JSON 尾随内容 @" + parser.pos);
        }
        return value;
    }

    public sealed interface JsonValue
            permits Object, Array, Str, Num, Bool, Null {

        default Object asObject() {
            return (Object) this;
        }

        default Array asArray() {
            return (Array) this;
        }

        default String stringValue() {
            return ((Str) this).value();
        }

        default int intValue() {
            return ((Num) this).intValue();
        }
    }

    public record Object(Map<String, JsonValue> members) implements JsonValue {
        public Object {
            members = Map.copyOf(members);
        }

        public boolean has(String key) {
            return members.containsKey(key);
        }

        public JsonValue get(String key) {
            return members.get(key);
        }

        public String getString(String key) {
            JsonValue v = members.get(key);
            return v instanceof Str s ? s.value() : "";
        }

        public int getInt(String key) {
            JsonValue v = members.get(key);
            return v instanceof Num n ? n.intValue() : 0;
        }

        public Array getArray(String key) {
            JsonValue v = members.get(key);
            return v instanceof Array a ? a : new Array(List.of());
        }
    }

    public record Array(List<JsonValue> elements) implements JsonValue {
        public Array {
            elements = List.copyOf(elements);
        }

        public int size() {
            return elements.size();
        }

        public JsonValue get(int index) {
            return elements.get(index);
        }
    }

    public record Str(String value) implements JsonValue {
    }

    public record Num(double value) implements JsonValue {
        public int intValue() {
            return (int) this.value;
        }

        public double doubleValue() {
            return this.value;
        }
    }

    public record Bool(boolean value) implements JsonValue {
    }

    public record Null() implements JsonValue {
    }

    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            return atEnd() ? '\0' : text.charAt(pos);
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        JsonValue parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonParseException("JSON 意外结束");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new Str(parseString());
                case 't' -> {
                    expect("true");
                    yield new Bool(true);
                }
                case 'f' -> {
                    expect("false");
                    yield new Bool(false);
                }
                case 'n' -> {
                    expect("null");
                    yield new Null();
                }
                default -> parseNumber();
            };
        }

        Object parseObject() {
            pos++; // '{'
            Map<String, JsonValue> members = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return new Object(members);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new JsonParseException("期望字符串键 @" + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(":");
                JsonValue value = parseValue();
                members.put(key, value);
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonParseException("对象未结束");
                }
                char c = text.charAt(pos++);
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw new JsonParseException("期望 , 或 } @" + (pos - 1));
                }
            }
            return new Object(members);
        }

        Array parseArray() {
            pos++; // '['
            List<JsonValue> elements = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return new Array(elements);
            }
            while (true) {
                elements.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonParseException("数组未结束");
                }
                char c = text.charAt(pos++);
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw new JsonParseException("期望 , 或 ] @" + (pos - 1));
                }
            }
            return new Array(elements);
        }

        String parseString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonParseException("字符串未结束");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonParseException("转义未结束");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new JsonParseException("\\u 转义不完整");
                            }
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new JsonParseException("未知转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Num parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            boolean floating = false;
            if (peek() == '.') {
                floating = true;
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                floating = true;
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            String num = text.substring(start, pos);
            if (num.isEmpty() || num.equals("-")) {
                throw new JsonParseException("无效数字 @" + start);
            }
            // 注意：统一按 double 解析。原实现对浮点分支强转 int，会把 -0.5037500262260437 这类
            // 噪声/样条参数截断成 0；样条 JSON 与 noise JSON 依赖完整的 double 精度。
            return new Num(Double.parseDouble(num));
        }

        void expect(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw new JsonParseException("期望 " + literal + " @" + pos);
            }
            pos += literal.length();
        }
    }
}
