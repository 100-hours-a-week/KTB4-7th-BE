package com.memme.service.auth;

import com.memme.dto.auth.LoginRequest;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.auth.InvalidLoginException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordHashConcurrencyGate passwordHashConcurrencyGate;
    private final LoginRateLimiter loginRateLimiter;

    public LoginService(
            UserRepository userRepository,
            StoreRepository storeRepository,
            PasswordHashConcurrencyGate passwordHashConcurrencyGate,
            LoginRateLimiter loginRateLimiter
    ) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordHashConcurrencyGate = passwordHashConcurrencyGate;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResult login(LoginRequest request, String ipAddress) {
        loginRateLimiter.checkAllowed(request.email(), ipAddress);
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email()).orElse(null);
        if (user == null) {
            return rejectLogin(request.email(), ipAddress);
        }
        if (!passwordHashConcurrencyGate.matches(request.password(), user.getPasswordHash())) {
            return rejectLogin(request.email(), ipAddress);
        }
        Store store = storeRepository.findByOwnerId(user.getId()).orElse(null);
        if (store == null) {
            return rejectLogin(request.email(), ipAddress);
        }

        loginRateLimiter.clearAccountFailures(request.email(), ipAddress);

        return new LoginResult(user.getId(), user.getEmail(), store.getId());
    }

    private LoginResult rejectLogin(String email, String ipAddress) {
        loginRateLimiter.recordFailure(email, ipAddress);
        throw new InvalidLoginException();
    }

    public record LoginResult(Long userId, String email, Long storeId) {
    }
}
