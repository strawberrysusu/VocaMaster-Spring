package com.vocamaster.user;

import com.vocamaster.auth.RefreshTokenRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
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

        // 구글 가입자는 password가 null이다 (AuthService: .password(null).provider("google")).
        // 그냥 두면 encoder.matches(x, null)이 조용히 false가 되어 '비밀번호가 틀렸다'는
        // 엉뚱한 안내를 받는다 — 실제로는 애초에 비밀번호가 없는 계정이다
        if (user.getPassword() == null) {
            throw new BadRequestException("구글 로그인 계정은 비밀번호를 사용하지 않습니다");
        }

        // 400이어야 한다. 401로 던지면 프런트 공용 api()가 '토큰 만료'로 오인해
        // refresh 후 같은 요청을 한 번 더 쏜다 — 이미 로그인한 사용자의 '입력값 오류'이지
        // 인증 세션 문제가 아니다 (Codex 검산 2026-09-01)
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("현재 비밀번호가 올바르지 않습니다");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("새 비밀번호는 현재 비밀번호와 달라야 합니다");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // refresh token만 폐기된다. 이미 발급된 다른 기기의 access token은 만료(1시간)까지 살아 있으므로
        // '모든 기기에서 즉시 로그아웃'이 아니다 — 안내 문구가 이보다 세게 말하면 거짓말이 된다.
        // 즉시 차단이 필요해지면 tokenVersion/passwordChangedAt 도입 검토 (지금은 백로그)
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
