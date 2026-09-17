package com.memme.controller.auth;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
import com.memme.dto.auth.*; import com.memme.service.auth.SignupService; import java.time.OffsetDateTime; import org.junit.jupiter.api.Test;
class SignupControllerTest { @Test void 가입요청은_201을_반환한다(){SignupService service=mock(SignupService.class); SignupController controller=new SignupController(service); SignupAccountRequest request=new SignupAccountRequest("a@b.com","Password1!","Password1!","01012345678",new SignupAccountRequest.Agreements(true,"v",true,"v")); when(service.signupAccount(request)).thenReturn(new SignupAccountResponse("signup_t",OffsetDateTime.now())); assertEquals(201,controller.signupAccount(request).getStatusCode().value());}}
