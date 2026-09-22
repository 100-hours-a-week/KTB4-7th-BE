package com.memme.service.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발/테스트 전용 사업자 상태 조회 스텁.
 *
 * <p>실제 국세청 API({@link NtsBusinessStatusClient})를 호출하지 않고, 형식만 유효하면(숫자 10자리,
 * {@code SignupBusinessRequest}의 패턴 검증을 이미 통과한 값) 무조건 "영업 중"으로 취급한다. 실제
 * 등록된 사업자번호가 없는 팀원도 회원가입 플로우를 끝까지 테스트할 수 있도록 하기 위함이다.
 *
 * <p>{@code local} 프로필이 활성화된 경우에만 등록되며, 운영/기본 프로필에서는 절대 사용되지 않는다.
 * 로컬에서 켜려면 백엔드 {@code .env}에 {@code SPRING_PROFILES_ACTIVE=local}을 추가한다.
 */
@Component
@Profile("local")
public class MockBusinessStatusClient implements BusinessStatusClient {

    private static final Logger log = LoggerFactory.getLogger(MockBusinessStatusClient.class);

    @Override
    public boolean isActive(String businessRegNumber) {
        log.warn(
                "[local profile] 국세청 조회를 생략하고 사업자번호를 무조건 통과시킵니다: {}",
                businessRegNumber
        );
        return true;
    }
}
