package com.thinkfree.drive.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LocaleUtil {

    private static final Pattern LOCALE_PATTERN = Pattern.compile("^[a-z]{2}_[A-Z]{2}$");
    
    public static final Set<Locale> SUPPORTED = Set.of(
            Locale.KOREA,
            Locale.US,
            Locale.GERMANY,
            Locale.FRANCE,
            new Locale("es", "ES"),
            Locale.ITALY,
            Locale.JAPAN,
            new Locale("nl", "NL"),
            new Locale("pt", "BR"),
            new Locale("ru", "RU"),
            new Locale("th", "TH"),
            Locale.SIMPLIFIED_CHINESE,
            new Locale("cs", "CZ"),
            new Locale("pl", "PL"),
            new Locale("tr", "TR")
    );
    
    private static final Map<String, Locale> SUPPORTED_MAP;

    static {
        Map<String, Locale> m = new HashMap<>();
        for (Locale loc : SUPPORTED) {
            m.put(loc.getLanguage().toLowerCase(Locale.ROOT) + "_" + loc.getCountry().toUpperCase(Locale.ROOT), loc);
        }
        SUPPORTED_MAP = Collections.unmodifiableMap(m);
    }

    
    public static String normalize(String lang) {
        if (!StringUtils.hasText(lang)){
            return null;
        }
        String v = lang.trim().replace('-', '_');
        String[] parts = v.split("_", -1);
        
        if (parts.length == 2) {
            return parts[0].toLowerCase() + "_" + parts[1].toUpperCase();
        }
        
        return null;
    }
    
    public static boolean isFormatValid(String normalized) {
        return normalized != null && LOCALE_PATTERN.matcher(normalized).matches();
    }
    
    public static Locale toLocale(String normalized) {
        String[] parts = normalized.split("_", -1);
        return new Locale(parts[0], parts[1]);
    }
    
    public static Locale toLocaleOrNull(String normalized) {
        if (!isFormatValid(normalized)){
            return null;
        }
        int pos = normalized.indexOf('_');
        if (pos <= 0 || pos >= normalized.length() - 1){
            return null;
        }
        String lang = normalized.substring(0, pos).toLowerCase(Locale.ROOT);
        String country = normalized.substring(pos + 1).toUpperCase(Locale.ROOT);
        return new Locale(lang, country);
    }
    
    public static Locale resolveOrDefault(String lang, Locale defaultLocale) {
        String normalized = normalize(lang);
        Locale loc = (normalized != null) ? SUPPORTED_MAP.get(normalized) : null;
        return (loc != null) ? loc : defaultLocale;
    }
    public static Locale resolveOrNull(String lang) {
        return resolveOrDefault(lang, null);
    }
  
    
}
