package com.kxh.aiagent.tools.finance;

import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/**
 * 远程估值数据读取工具 —— 获取 PE/PB/PS/PEG/市值等估值指标
 */
public class RemoteValuationReader {

    private final FinanceApiClient client = new FinanceApiClient();

    @Tool(description = """
            获取A股上市公司的估值指标，包括PE-TTM(滚动市盈率)、PB(市净率)、PS-TTM(市销率)、PEG、总市值、
            以及分析师预测的未来PE等。输入股票代码如'600519'，返回结构化估值数据JSON。""")
    public String readValuationData(
            @ToolParam(description = "股票代码，如600519") String stockCode,
            @ToolParam(description = "查询最近几年的数据，默认5年") int years) {
        Map<String, Object> result = client.callApi("/valuation/" + stockCode + "?years=" + years);
        return JSONUtil.toJsonStr(result);
    }
}
