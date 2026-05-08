package me.efekt.items;

public enum EfektItemType {
    BALTA("balta"),
    KAZMA("kazma"),
    KUREK("kurek"),
    CUBUK("cubuk"),
    KOVA("kova");

    private final String key;

    EfektItemType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static EfektItemType fromKey(String raw) {
        if (raw == null) return null;
        String k = raw.toLowerCase();
        for (var t : values()) {
            if (t.key.equals(k)) return t;
        }
        return null;
    }
}

