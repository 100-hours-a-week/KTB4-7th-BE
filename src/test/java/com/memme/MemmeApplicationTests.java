package com.memme;

import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.BusinessVerificationRepository;
import com.memme.service.store.BusinessStatusClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MemmeApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SignupDraftRepository signupDraftRepository;

    @MockitoBean
    private BusinessVerificationRepository businessVerificationRepository;

    @MockitoBean
    private BusinessStatusClient businessStatusClient;

    @Test
    void contextLoads() {
    }
}
