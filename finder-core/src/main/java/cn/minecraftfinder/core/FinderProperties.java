package cn.minecraftfinder.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class FinderProperties {
    private final Properties values;

    private FinderProperties(Properties values) {
        this.values = values;
    }

    public static FinderProperties load(Path path) throws IOException {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        return new FinderProperties(values);
    }

    public String required(String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: " + key);
        }
        return value.trim();
    }

    public boolean contains(String key) {
        String value = values.getProperty(key);
        return value != null && !value.isBlank();
    }

    public String optional(String key, String fallback) {
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public long requiredLong(String key) {
        String value = required(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置项不是有效整数: " + key + "=" + value, e);
        }
    }

    public int requiredInt(String key) {
        return Math.toIntExact(requiredLong(key));
    }

    public int optionalInt(String key, int fallback) {
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? fallback : requiredInt(key);
    }

    public boolean optionalBoolean(String key, boolean fallback) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        if (value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("配置项只能是 true 或 false: " + key + "=" + value);
    }
}
