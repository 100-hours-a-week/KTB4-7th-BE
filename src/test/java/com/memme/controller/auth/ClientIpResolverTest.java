package com.memme.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void forwarded_for를_신뢰하지_않으면_소켓_remote_address를_사용한다() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.10");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void 신뢰된_forwarded_for에서는_ALB가_끝에_추가한_가장_오른쪽_IP를_사용한다() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.10");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void 잘못된_forwarded_for는_remote_address로_대체한다() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "attacker.example");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
    }

    @Test
    void IPv6_주소의_괄호를_제거한다() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "[2001:db8::1]:443");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
