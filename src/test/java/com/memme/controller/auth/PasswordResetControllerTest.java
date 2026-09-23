package com.memme.controller.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.auth.PasswordResetRequest;
import com.memme.dto.common.FieldError;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.InvalidPasswordResetRequestException;
import com.memme.exception.PasswordResetTokenExpiredException;
import com.memme.service.auth.PasswordResetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PasswordResetControllerTest {

    @Test
    void 이메일_요청은_가입여부와_관계없이_202_응답을_반환한다() throws Exception {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        MockMvc mockMvc = mockMvc(passwordResetService);

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/password-reset/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"owner@memme.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("입력한 이메일로 비밀번호 재설정 안내를 보냈습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(passwordResetService).requestResetEmail(new PasswordResetEmailRequest("owner@memme.com"));
    }

    @Test
    void 유효한_토큰으로_비밀번호를_재설정하면_200_응답을_반환한다() throws Exception {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        MockMvc mockMvc = mockMvc(passwordResetService);

        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "raw-token",
                                  "newPassword": "NewPassword1!",
                                  "confirmPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(passwordResetService).resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "NewPassword1!"
        ));
    }

    @Test
    void 비밀번호_확인이_일치하지_않으면_400_fieldErrors를_반환한다() throws Exception {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        doThrow(new InvalidPasswordResetRequestException(List.of(
                new FieldError("confirmPassword", "비밀번호와 일치하지 않습니다.")
        ))).when(passwordResetService).resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "DifferentPassword1!"
        ));
        MockMvc mockMvc = mockMvc(passwordResetService);

        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "raw-token",
                                  "newPassword": "NewPassword1!",
                                  "confirmPassword": "DifferentPassword1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("confirmPassword"));
    }

    @Test
    void 만료된_토큰이면_410_응답을_반환한다() throws Exception {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        doThrow(new PasswordResetTokenExpiredException()).when(passwordResetService).resetPassword(new PasswordResetRequest(
                "expired-token", "NewPassword1!", "NewPassword1!"
        ));
        MockMvc mockMvc = mockMvc(passwordResetService);

        mockMvc.perform(MockMvcRequestBuilders.patch("/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-token",
                                  "newPassword": "NewPassword1!",
                                  "confirmPassword": "NewPassword1!"
                                }
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("비밀번호 재설정 링크가 만료되었거나 이미 사용되었습니다."));
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400_공통_응답을_반환한다() throws Exception {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        MockMvc mockMvc = mockMvc(passwordResetService);

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/auth/password-reset/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"invalid-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(passwordResetService);
    }

    private MockMvc mockMvc(PasswordResetService passwordResetService) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new PasswordResetController(passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
