package com.kxh.aiagent.tools.financeTool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kxh.aiagent.entity.Finance;
import com.kxh.aiagent.entity.Valuation;
import com.kxh.aiagent.mapper.FinanceMapper;
import com.kxh.aiagent.mapper.ValuationMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 估值查询
 */
@Component
public class ValuationQuery {

    @Autowired
    ValuationMapper valuationMapper;
    @Tool(
            description = "Query a company's valuation metrics for the past N years (annual data). " +
                    "Returns key valuation indicators such as PE (Price-to-Earnings), PB (Price-to-Book), " +
                    "PS (Price-to-Sales), Market Capitalization (市值), and Tobin's Q (托宾Q值) when available."
    )
    public String queryCompanyValuation(
            @ToolParam(description = "The company's market code, e.g., '600519'.") String marketCode,
            @ToolParam(description = "Number of past years to query, e.g., 5 for the last 5 years.") int pastYear
    ) throws JsonProcessingException {
        // 创建查询条件
        QueryWrapper<Valuation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("Stkcd",marketCode)
                .orderByDesc("Accper")
                .last("LIMIT "+pastYear);

        List<Valuation> topNList = valuationMapper.selectList(queryWrapper);

        List<Map> maps = new ArrayList<>();
        for (Valuation valuation:topNList){
            Map finMap = new HashMap();
            finMap.put("year",valuation.getAccper().substring(0,4));
            finMap.put("PE_TTM",valuation.getF100103c());
            finMap.put("PB",valuation.getF100701a());
            finMap.put("PS_TTM",valuation.getF100203c());
            finMap.put("PCF_TTM",valuation.getF100303c());
            finMap.put("市值_marketCap",valuation.getF100802a());
            finMap.put("企业价值倍数_EV_EBITDA",valuation.getF101302c());
            finMap.put("TobinQ_A",valuation.getF100901a());
            finMap.put("Depreciation",valuation.getF101001a());
            maps.add(finMap);
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(maps);


    }
}
