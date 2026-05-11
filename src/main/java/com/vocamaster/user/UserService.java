package com.vocamaster.user;

import com.vocamaster.auth.RefreshTokenRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.dto.ChangePasswordRequest;
import com.vocamaster.user.dto.UpdateMeRequest;
import com.vocamaster.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = findUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateMeRequest req) {
        User user = findUser(userId);
        user.setNickname(req.getNickname());
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * 비밀번호 변경 + **모든 활성 refresh token 폐기**.
     * 의도: 비번 바뀐 시점부터 기존 모든 세션은 즉시 강제 재로그인.
     * (탈취된 토큰이 있어도 비번 변경 후엔 무효화)
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new UnauthorizedException("현재 비밀번호가 올바르지 않습니다");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("새 비밀번호는 현재 비밀번호와 달라야 합니다");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
    }
    @Transactional
    public void deleteAccount(Long userId) {
        User user = findUser(userId);
        if (user.isDeleted()) {
            return;  //idempotent — 이미 탈퇴면 그냥 무시
        }
        LocalDateTime now = LocalDateTime.now();
        user.setDeletedAt(now);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId, now);
    }

}
