package com.kxh.aiagent.ops.history;

public record OpsHistoryRecord(
        String id,
        String serviceName,
        String errorClass,
        String severity,
        long occurredAtSec,
        String summary,
        String resolution
) {
    public String fullText() {
        return ("Service: " + nz(serviceName) + "\n"
                + "Error: " + nz(errorClass) + "\n"
                + "Summary: " + nz(summary) + "\n"
                + "Resolution: " + nz(resolution));
    }
    private static String nz(String s) { return s == null ? "" : s; }
}
