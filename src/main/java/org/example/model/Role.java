package org.example.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum Role {
    TOP("Top", "TopIcon.png"),
    JUNGLE("Jungle", "JungleIcon.png"),
    MID("Mid", "MiddleIcon.png"),
    BOTTOM("Bot", "BottomIcon.png"),
    SUPPORT("Support", "SupportIcon.png"),
    UNKNOWN("Flex", "SupportIcon.png");

    static {
        TOP.setPartners(EnumSet.of(JUNGLE), EnumSet.of(TOP));
        JUNGLE.setPartners(EnumSet.of(TOP, MID, BOTTOM), EnumSet.of(JUNGLE));
        MID.setPartners(EnumSet.of(JUNGLE), EnumSet.of(MID));
        BOTTOM.setPartners(EnumSet.of(SUPPORT), EnumSet.of(BOTTOM));
        SUPPORT.setPartners(EnumSet.of(BOTTOM, JUNGLE), EnumSet.of(SUPPORT));
        UNKNOWN.setPartners(EnumSet.allOf(Role.class), EnumSet.allOf(Role.class));
    }

    private final String label;
    private final String iconFile;
    private Set<Role> synergyPartners;
    private Set<Role> counterPartners;

    Role(String label, String iconFile) {
        this.label = label;
        this.iconFile = iconFile;
    }

    private void setPartners(Set<Role> synergy, Set<Role> counter) {
        this.synergyPartners = EnumSet.copyOf(synergy);
        this.counterPartners = EnumSet.copyOf(counter);
    }

    public String label() {
        return label;
    }

    public String iconFile() {
        return iconFile;
    }

    public boolean pairsWith(Role other) {
        if (this == UNKNOWN || other == null || other == UNKNOWN) return true;
        return synergyPartners.contains(other);
    }

    public boolean contests(Role other) {
        if (this == UNKNOWN || other == null || other == UNKNOWN) return true;
        return counterPartners.contains(other);
    }

    public Role next() {
        List<Role> ordered = List.of(TOP, JUNGLE, MID, BOTTOM, SUPPORT);
        if (!ordered.contains(this)) {
            return ordered.get(0);
        }
        int idx = ordered.indexOf(this);
        return ordered.get((idx + 1) % ordered.size());
    }
}
