package com.kxh.aiagent.tools.finance;

import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 远程财务数据读取工具 —— 调用 AKShare 微服务获取财务三表及核心指标
 */
public class RemoteFinanceDataReader {

    private final FinanceApiClient client = new FinanceApiClient();

    @Tool(description = """
            获取A股上市公司的财务数据，包括利润表、资产负债表、现金流量表和核心财务指标(ROE、ROA、毛利率、净利率等)。
            输入股票代码如'600519'(贵州茅台)或'000858'(五粮液)，返回最近N年的结构化财务数据JSON。""")
    public String readFinanceData(
            @ToolParam(description = "股票代码，如600519") String stockCode,
            @ToolParam(description = "查询最近几年的数据，默认5年") int years) {
        if (!com.kxh.aiagent.tools.InputValidator.isValidStockCode(stockCode)) {
            return "无效的股票代码，请输入6位数字代码如600519";
        }
        if (years < 1 || years > 10) years = 5;
        Map<String, Object> result = client.callApi("/finance/" + stockCode.trim() + "?years=" + years);
        return JSONUtil.toJsonStr(result);
    }
}
