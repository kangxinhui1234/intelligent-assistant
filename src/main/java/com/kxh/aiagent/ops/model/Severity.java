package com.kxh.aiagent.ops.model;

public enum Severity {
    P0, P1, P2;

    public static Severity fromString(String s) {
        if (s == null) return P1;
        try {
            return Severity.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return P1;
        }
    }
}
