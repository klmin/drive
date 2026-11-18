package com.thinkfree.drive.accessrestriction.component;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CountryCodeRegistry {

    private Map<String, String> codeToName;

    @PostConstruct
    public void init() throws Exception {
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("geoip/GeoLite2-Country-Locations-en.csv");

        if (csv == null) {
            throw new IllegalStateException("GeoIP country locations CSV not found in classpath!");
        }

        Map<String, String> map = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            reader.lines()
                    .skip(1) // 헤더 skip
                    .map(CountryCodeRegistry::splitCsvLine)
                    .filter(cols -> cols.size() >= 6 && !cols.get(4).isBlank())
                    .forEach(cols -> map.put(cols.get(4).trim(), cols.get(5).trim()));
        }

        this.codeToName = Collections.unmodifiableMap(map);
    }

    /** CSV 한 줄 → 필드 — 따옴표로 감싼 필드(쉼표 포함 가능, "" 는 따옴표 하나)는 따옴표를 벗긴다 */
    private static List<String> splitCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cols.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        cols.add(field.toString());
        return cols;
    }

    public boolean isValid(String code) {
        return code != null && codeToName.containsKey(code);
    }

    public String getName(String code) {
        return codeToName.get(code);
    }

    public Map<String, String> getAll() {
        return codeToName;
    }
}
