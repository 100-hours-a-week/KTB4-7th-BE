package com.memme.service.auth;

import com.memme.dto.auth.LoginRequest;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.InvalidLoginException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(
            UserRepository userRepository,
            StoreRepository storeRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidLoginException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidLoginException();
        }
        Store store = storeRepository.findByOwnerId(user.getId())
                .orElseThrow(InvalidLoginException::new);

        return new LoginResult(user.getId(), user.getEmail(), store.getId());
    }

    public record LoginResult(Long userId, String email, Long storeId) {
    }
}
