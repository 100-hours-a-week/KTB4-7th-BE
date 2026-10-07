package com.memme.service.sales.profit;

import com.memme.dto.sales.ProfitAnalysisResponse;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfitInsightService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일");

    public ProfitAnalysisResponse.AiInsight describe(long netProfit, Long previousNetProfit,
            List<ProfitAnalysisResponse.DailyProfit> dailyProfits) {
        List<String> insights = new ArrayList<>();
        if (netProfit < 0) {
            insights.add("선택 기간의 예상 순손실은 " + absolute(netProfit) + "원이에요.");
        } else {
            insights.add("선택 기간의 예상 순이익은 " + netProfit + "원이에요.");
        }
        if (previousNetProfit != null) {
            BigDecimal difference = BigDecimal.valueOf(netProfit).subtract(BigDecimal.valueOf(previousNetProfit));
            if (difference.signum() > 0) {
                insights.add("이전 기간보다 순이익이 " + difference + "원 늘었어요.");
            } else if (difference.signum() < 0) {
                insights.add("이전 기간보다 순이익이 " + difference.abs() + "원 줄었어요.");
            } else {
                insights.add("이전 기간과 순이익이 같아요.");
            }
        }
        if (dailyProfits.size() > 1) {
            ProfitAnalysisResponse.DailyProfit highestDay = dailyProfits.stream()
                    .max(Comparator.comparingLong(ProfitAnalysisResponse.DailyProfit::netProfit)
                            .thenComparing(ProfitAnalysisResponse.DailyProfit::date,
                                    Comparator.reverseOrder()))
                    .orElseThrow();
            insights.add(highestDay.date().format(DATE_FORMAT) + "의 예상 순이익이 가장 높아요.");
        }
        return new ProfitAnalysisResponse.AiInsight("COMPLETED", List.copyOf(insights));
    }

    private BigDecimal absolute(long amount) {
        return BigDecimal.valueOf(amount).abs();
    }
}
