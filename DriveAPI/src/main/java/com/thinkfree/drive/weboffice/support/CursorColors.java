package com.thinkfree.drive.weboffice.support;

import java.util.List;

public final class CursorColors {

    private static final List<String> COLORS = List.of(
            "#3B82F6", "#EC4899", "#9333EA", "#7FC416", "#F97316",
            "#E11D48", "#15803D", "#7A4030", "#EAB308", "#78716C",
            "#00A6BB", "#374D7B", "#8F72BE", "#C96EAD", "#C44000",
            "#F59E0B", "#4D7C0F", "#163CB1", "#6366F1", "#BE185D",
            "#4B5563", "#459E90", "#9F8080", "#C35858", "#DBB700",
            "#82B400", "#4489AD", "#AC76E2", "#F472B6", "#E84C66"
    );

    public static int randomIndex() {
        return (int) (Math.random() * COLORS.size());
    }

    public static String getByIndex(int index) {
        return COLORS.get(index);
    }

    public static String random() {
        return getByIndex(randomIndex());
    }

    private CursorColors() {}
}
