package com.kxh.aiagent.tools.financeTool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.entity.Finance;
import com.kxh.aiagent.mapper.FinanceMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务指标插叙
 */
@Component
public class financeIndexQuery {
    @Autowired
    FinanceMapper financeMapper;

    @Tool(description = "Query a company's financial indicators for the past N years." +
                    " Returns a complete set of metrics including  ROE, ROA, Gross Profit Margin (毛利率), " +
                    "Net Profit Margin (净利率),  Debt-to-Asset Ratio (资产负债率), and Cash Flow (现金流等)."
    )
    public String queryCompanyFinanceInfo(@ToolParam(description = "公司证券代码")String marketCode,
                                          @ToolParam(description = " Number of past years to query, e.g., 5 for the last 5 years.")
                                              int pastYear) throws JsonProcessingException {
        // 创建查询条件
        QueryWrapper<Finance> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("Symbol",marketCode)
                        .orderByDesc("EndDate")
                                .last("LIMIT "+pastYear);
        List<Finance> topNList = financeMapper.selectList(queryWrapper);
       List<Map> maps = new ArrayList<>();
        for (Finance finance:topNList){
            Map finMap = new HashMap();
            finMap.put("year",finance.getEndDate().substring(0,4));
            finMap.put("totalAssets",finance.getTotalAssets());
            finMap.put("totalLiabilities",finance.getTotalLiabilities());
            finMap.put("operatingRevenue",finance.getOperatingRevenue());
            finMap.put("OperatingProfit",finance.getOperatingProfit());
            finMap.put("netProfit",finance.getNetProfit());
            finMap.put("EPS",finance.getEps());
            finMap.put("经营活动现金流量净额_OperatingNetCashFlow",finance.getOperatingNetCashFlow());
            finMap.put("Depreciation",finance.getDepreciation());
            finMap.put("AmorOfIntangibleAssets",finance.getAmorOfIntangibleAssets());
            finMap.put("资产负债率_AssetLiabilityRatio",finance.getAssetLiabilityRatio());
            finMap.put("总资产报酬率_roa",finance.getRoaa());
            finMap.put("净资产收益率_roe",finance.getRoea());
            finMap.put("每股净资产_bvps",finance.getNavps());
            maps.add(finMap);
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(maps);


    }
}
