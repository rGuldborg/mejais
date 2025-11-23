package org.example.model;

public enum Tier {
    S_PLUS("S+", 10.0),
    S("S", 9.5),
    S_MINUS("S-", 9.0),
    A_PLUS("A+", 8.5),
    A("A", 8.0),
    A_MINUS("A-", 7.5),
    B_PLUS("B+", 7.0),
    B("B", 6.5),
    B_MINUS("B-", 6.0),
    C_PLUS("C+", 5.5),
    C("C", 5.0),
    C_MINUS("C-", 4.5),
    D_PLUS("D+", 4.0),
    D("D", 3.5),
    D_MINUS("D-", 3.0),
    NA("-", 0.0);

    private final String label;
    private final double score;

    Tier(String label, double score) {
        this.label = label;
        this.score = score;
    }

    public String label() {
        return label;
    }

    public double score() {
        return score;
    }

    public static Tier fromWinRate(double winRate) {
        if (winRate >= 0.58) return S_PLUS;
        if (winRate >= 0.56) return S;
        if (winRate >= 0.54) return S_MINUS;
        if (winRate >= 0.52) return A_PLUS;
        if (winRate >= 0.51) return A;
        if (winRate >= 0.50) return A_MINUS;
        if (winRate >= 0.495) return B_PLUS;
        if (winRate >= 0.49) return B;
        if (winRate >= 0.485) return B_MINUS;
        if (winRate >= 0.48) return C_PLUS;
        if (winRate >= 0.475) return C;
        if (winRate >= 0.47) return C_MINUS;
        if (winRate >= 0.465) return D_PLUS;
        if (winRate >= 0.46) return D;
        return D_MINUS;
    }

    public static Tier fromWinRate(Double winRate, boolean allowNa) {
        if (winRate == null || winRate.isNaN()) {
            return allowNa ? NA : D_MINUS;
        }
        return fromWinRate(winRate);
    }
}
