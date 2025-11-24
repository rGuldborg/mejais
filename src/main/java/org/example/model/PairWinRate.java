package org.example.model;

public record PairWinRate(String champion, double winRate) {
    public double delta() {
        return winRate - 0.5;
    }

    public boolean isPositive() {
        return delta() >= 0;
    }
}
