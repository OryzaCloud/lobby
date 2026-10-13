package com.oryza.lobby.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageBundle {
    private final String defaultLocale;
    private final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();

    public MessageBundle(String defaultLocale, String customDir) {
        this.defaultLocale = defaultLocale != null ? defaultLocale.toLowerCase(Locale.ROOT) : "pt_br";
        loadBuiltin("pt_br");
        loadBuiltin("en_us");

        if (customDir != null && !customDir.isEmpty()) {
            loadFromDirectory(new File(customDir));
        }

        validate();
    }

    private void loadBuiltin(String localeKey) {
        String resourcePath = "/messages/messages_" + localeKey + ".properties";
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, String> map = new HashMap<>();
                for (String name : props.stringPropertyNames()) {
                    map.put(name, props.getProperty(name));
                }
                bundles.put(localeKey, map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load builtin messages: " + resourcePath, e);
        }
    }

    private void loadFromDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("messages_") && name.endsWith(".properties"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            String localeKey = name.substring("messages_".length(), name.length() - ".properties".length()).toLowerCase(Locale.ROOT);
            try (InputStream in = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                Map<String, String> map = new HashMap<>(bundles.getOrDefault(localeKey, Map.of()));
                for (String propName : props.stringPropertyNames()) {
                    map.put(propName, props.getProperty(propName));
                }
                bundles.put(localeKey, map);
            } catch (Exception ignored) {
            }
        }
    }

    private void validate() {
        Map<String, String> defBundle = bundles.get(defaultLocale);
        if (defBundle == null) {
            throw new IllegalStateException("Default locale bundle not found: " + defaultLocale);
        }
        for (Map.Entry<String, Map<String, String>> entry : bundles.entrySet()) {
            for (String key : defBundle.keySet()) {
                if (!entry.getValue().containsKey(key)) {
                    entry.getValue().put(key, defBundle.get(key));
                }
            }
        }
    }

    public String get(String locale, String key, Map<String, String> placeholders) {
        String locKey = locale != null ? locale.replace('-', '_').toLowerCase(Locale.ROOT) : defaultLocale;
        Map<String, String> bundle = bundles.getOrDefault(locKey, bundles.get(defaultLocale));
        if (bundle == null) {
            return key;
        }
        String template = bundle.getOrDefault(key, key);
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String safeVal = escapeText(entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", safeVal);
        }
        return result;
    }

    public static String escapeText(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("§", "");
    }
}
