package org.example.util;

import org.example.model.Role;

public enum RoleFilter {
    FLEX("Flex", null),
    TOP("Top", Role.TOP),
    JUNGLE("Jungle", Role.JUNGLE),
    MID("Mid", Role.MID),
    ADC("ADC", Role.BOTTOM),
    SUPPORT("Support", Role.SUPPORT);

    private final String label;
    private final Role mappedRole;

    RoleFilter(String label, Role mappedRole) {
        this.label = label;
        this.mappedRole = mappedRole;
    }

    public String label() {
        return label;
    }

    public Role mappedRole() {
        return mappedRole;
    }
}
