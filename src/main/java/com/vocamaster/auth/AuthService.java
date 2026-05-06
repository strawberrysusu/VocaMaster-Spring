package com.vocamaster.auth;

import com.vocamaster.auth.dto.LoginRequest;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.UnauthorizedException;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public TokenResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("이미 사용 중인 이메일입니다");
        }

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname())
                .build();

        userRepository.save(user);

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        return new TokenResponse(token);
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        return new TokenResponse(token);
    }
}
