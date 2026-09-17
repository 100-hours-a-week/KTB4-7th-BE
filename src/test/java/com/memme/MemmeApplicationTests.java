package com.memme;

import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MemmeApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SignupDraftRepository signupDraftRepository;

    @Test
    void contextLoads() {
    }
}
