package com.memme.service.auth;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.memme.dto.auth.*;
import com.memme.exception.*;
import com.memme.repository.auth.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@ExtendWith(MockitoExtension.class) class SignupServiceTest {
 @Mock UserRepository users; @Mock SignupDraftRepository drafts; SignupService service;
 @BeforeEach void setUp(){ service=new SignupService(users,drafts,new BCryptPasswordEncoder(),Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"),ZoneId.of("Asia/Seoul"))); }
 SignupAccountRequest request(){return new SignupAccountRequest("owner@memme.com","Password1!","Password1!","01012345678",new SignupAccountRequest.Agreements(true,"2026-09",true,"2026-09"));}
 @Test void 임시_저장하고_한시간_토큰을_발급한다(){when(users.existsByEmail(any())).thenReturn(false);when(users.existsByPhone(any())).thenReturn(false); SignupAccountResponse response=service.signupAccount(request()); assertTrue(response.signupToken().startsWith("signup_")); assertEquals(OffsetDateTime.parse("2026-09-17T11:00:00+09:00"),response.expiresAt()); verify(drafts).save(any());}
 @Test void 이메일_중복이면_예외를_던진다(){when(users.existsByEmail(any())).thenReturn(true); assertThrows(DuplicateSignupException.class,()->service.signupAccount(request())); verify(drafts,never()).save(any());}
 @Test void 잘못된_입력은_예외를_던진다(){assertThrows(InvalidSignupRequestException.class,()->service.signupAccount(new SignupAccountRequest("wrong","password","password","011",null)));}
}
