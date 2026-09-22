package com.memme.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 설정.
 *
 * <p>지금은 Spring Security가 없어서 {@link CorsFilter} 빈으로 직접 적용한다. 이후 로그인용
 * Spring Security를 추가할 때는 이 {@link #corsConfigurationSource()} 빈을 그대로
 * {@code http.cors(cors -> cors.configurationSource(corsConfigurationSource))}에 연결해서
 * 재사용하면 된다. Security 필터 체인이 요청을 먼저 가로채기 때문에, WebMvcConfigurer의
 * addCorsMappings만으로는 Security가 추가된 뒤 CORS가 다시 막힐 수 있다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }
}
