package com.memme.service.auth;

import com.memme.entity.auth.User;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.repository.auth.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {

    private final UserRepository userRepository;
    private final Clock clock;

    public WithdrawalService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        user.withdraw(LocalDateTime.now(clock));
    }
}
