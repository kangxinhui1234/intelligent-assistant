package com.kxh.aiagent.tools.finance;

import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 行业分析与公司基本信息读取工具
 */
public class RemoteIndustryReader {

    private final FinanceApiClient client = new FinanceApiClient();

    @Tool(description = """
            获取A股上市公司的基本信息：公司全称、主营业务、所属行业、上市日期、总股本、流通股本等。""")
    public String readStockInfo(
            @ToolParam(description = "股票代码，如600519") String stockCode) {
        if (!com.kxh.aiagent.tools.InputValidator.isValidStockCode(stockCode)) {
            return "无效的股票代码，请输入6位数字代码如600519";
        }
        Map<String, Object> result = client.callApi("/stock_info/" + stockCode.trim());
        return JSONUtil.toJsonStr(result);
    }

    @Tool(description = """
            获取同行业可比公司列表。输入股票代码，先自动识别其所属行业，再返回该行业的所有上市公司，
            用于行业对比分析。""")
    public String readIndustryPeers(
            @ToolParam(description = "股票代码，如600519") String stockCode) {
        if (!com.kxh.aiagent.tools.InputValidator.isValidStockCode(stockCode)) {
            return "无效的股票代码，请输入6位数字代码如600519";
        }
        Map<String, Object> result = client.callApi("/industry/peers/" + stockCode.trim());
        return JSONUtil.toJsonStr(result);
    }
}
