package com.memme.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class MultipartUploadConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void 매출_업로드는_파일당_10MB와_요청당_11MB까지_허용한다() {
        assertEquals("10MB", environment.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("11MB", environment.getProperty("spring.servlet.multipart.max-request-size"));
    }
}
