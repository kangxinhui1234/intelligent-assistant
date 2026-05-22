package com.kxh.aiagent.tools.finance;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * AKShare 数据服务 HTTP 客户端
 */
public class FinanceApiClient {

    private static final String BASE_URL = "http://localhost:8899/api/v1";

    public Map<String, Object> callApi(String path) {
        String url = BASE_URL + path;
        String response = HttpUtil.get(url, 120000);
        return JSONUtil.toBean(response,
                new TypeReference<Map<String, Object>>() {}, false);
    }
}
