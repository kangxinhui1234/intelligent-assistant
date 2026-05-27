package com.kxh.aiagent.tools;

import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern STOCK_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern SAFE_URL = Pattern.compile("^https?://[\\w./-]+.*$");

    private InputValidator() {}

    public static boolean isValidStockCode(String code) {
        return code != null && STOCK_CODE.matcher(code.trim()).matches();
    }

    public static boolean isValidUrl(String url) {
        return url != null && SAFE_URL.matcher(url.trim()).matches();
    }
}
