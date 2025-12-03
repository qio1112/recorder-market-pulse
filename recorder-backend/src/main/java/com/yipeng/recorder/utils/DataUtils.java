package com.yipeng.recorder.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DataUtils {

    public static Map<String, String> cleanMapKeysAndValues(Map<String, String> map) {
        if (map == null) {
            return null;
        }

        Map<String, String> cleaned = new HashMap<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() == null || e.getKey().trim().isEmpty()) {
                continue ; // key should not be null or empty
            }
            String key = e.getKey().trim().toLowerCase(Locale.ROOT);
            String val;
            if (key.equals("symbol")) {
                val = e.getValue() == null ? null : e.getValue().trim().toUpperCase(Locale.ROOT);
            } else {
                val = e.getValue() == null ? null : e.getValue().trim().toLowerCase(Locale.ROOT);
            }
            cleaned.put(key, val);
        }

        return cleaned;
    }
}
