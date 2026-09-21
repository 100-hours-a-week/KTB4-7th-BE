package com.memme.config;

import java.util.List;
import java.util.Map;

import com.memme.service.sales.SalesItemType;
import com.memme.service.sales.TossPosItemTypeRules;
import com.memme.service.sales.TossPosWorkbookParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SalesParserConfiguration {

    @Bean
    TossPosWorkbookParser tossPosWorkbookParser() {
        TossPosItemTypeRules rules = new TossPosItemTypeRules(Map.of(
                SalesItemType.PARKING, List.of(
                        "주차권", "주차권 30분", "주차권 1시간", "주차권 4시간", "주차권 24시간"
                ),
                SalesItemType.PREPAID_CARD, List.of(
                        "선불카드", "선불카드 (오만원)", "선불카드(만원)",
                        "선불카드(오만원)", "선불카드(오천원)", "선불카드(천원)"
                ),
                SalesItemType.DELIVERY_FEE, List.of("배달료", "배달비"),
                SalesItemType.PLATFORM_PLACEHOLDER, List.of("배민1", "쿠팡이츠"),
                SalesItemType.EVENT, List.of("블로그 인스타 체험단", "영수증 포토리뷰 이벤트"),
                SalesItemType.GOODS, List.of(
                        "루미코르 코스터", "루미코르 행주타월", "루미코르 행주타월 (걸이형)"
                )
        ));
        return new TossPosWorkbookParser(rules);
    }
}
