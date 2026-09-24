package com.memme.service.auth;

import com.memme.dto.auth.UserProfileResponse;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    public UserProfileService(UserRepository userRepository, StoreRepository storeRepository) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        return new UserProfileResponse(new UserProfileResponse.User(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                store.getStoreName()
        ));
    }
}
