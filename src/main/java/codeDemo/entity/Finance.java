package entity;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author kxh
 * @since 2025-09-12
 */
@Getter
@Setter
public class Finance implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;

    private String endDate;

    private String shortName;

    private String cash;

    private String accountsReceivable;

    private String nonCurrentAssetsInYear;

    private String totalCurrentAssets;

    private String inventory;

    private String otherCurrentAssets;

    private String fixedAssets;

    private String disposalOfFixedAssets;

    private String intangibleAssets;

    private String totalAssets;

    private String totalCurrentliabilities;

    private String totalLiabilities;

    private String shortTermLoan;

    private String accountsPayable;

    private String taxePayable;

    private String stockDividendPayable;

    private String longLiabInYearChange;

    private String totalEquity;

    private String capitalStock;

    private String totalRevenue;

    private String operatingRevenue;

    private String totalOperatingCost;

    private String operatingCost;

    private String businessTaxAndSurcharge;

    private String sellingExpenses;

    private String managementExpense;

    private String rDExpenses;

    private String financeExpense;

    private String operatingProfit;

    private String nonOperatingIncome;

    private String nonOperatingExpenses;

    private String totalProfit;

    private String incomeTax;

    private String netProfit;

    private String depreciation;

    private String amorOfIntangibleAssets;

    private String amorOfDeferredExpenses;

    private String operatingNetCashFlow;

    private String assetLiabilityRatio;

    private String rotaa;

    private String rotab;

    private String rotac;

    private String roaa;

    private String roab;

    private String roac;

    private String roea;

    private String roeb;

    private String roec;

    private String marketValueA;

    private String marketValueB;

    private String valueBookRatioA;

    private String valueBookRatioB;

    private String eps;

    private String navps;

    private String industryCode;

    private String industryName;
}
