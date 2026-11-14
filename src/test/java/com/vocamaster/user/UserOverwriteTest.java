package com.vocamaster.user;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.user.dto.ChangePasswordRequest;
import com.vocamaster.user.dto.UpdateMeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 닉네임 저장 vs 비밀번호 변경·탈퇴 (Codex 검산 2026-11-14, P1).
 * User 저장이 전체 컬럼 UPDATE면 닉네임 저장이 '읽었을 때의 password·deleted_at'까지 같이 써서,
 * 그 사이 커밋된 비밀번호 변경이 옛 비밀번호로, 탈퇴가 탈퇴 전으로 되돌아간다 → @DynamicUpdate.
 * 구조는 DeckCounterOverwriteTest와 같다 — 바깥 트랜잭션이 읽고, 안쪽 새 트랜잭션이 끼어들어 커밋, 바깥이 나중에 커밋.
 * NOT_SUPPORTED: 트랜잭션 두 개가 진짜로 겹쳐야 해서 커밋 데이터 사용.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserOverwriteTest extends AbstractIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager txManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("uo_" + System.nanoTime() + "@test.com")
                .password(passwordEncoder.encode("old-password-1"))
                .nickname("원래 닉네임").build());
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("닉네임 저장 도중 비밀번호 변경이 커밋 → 닉네임도 바뀌고 새 비밀번호도 남는다")
    void nicknameSave_doesNotRevertPasswordChange() {
        Long id = user.getId();

        newTx().execute(s1 -> {
            userService.updateMe(id, nickname("새 닉네임"));   // 사용자를 읽음 (password = 옛 해시) — UPDATE는 커밋 때
            newTx().execute(s2 -> {                             // 그 사이 비밀번호 변경이 끝까지 커밋
                userService.changePassword(id, passwordChange("old-password-1", "new-password-2"));
                return null;
            });
            return null;
        });                                                     // 전체 컬럼 UPDATE였다면 여기서 password = 옛 해시로 덮어씀

        User after = userRepository.findById(id).orElseThrow();
        assertEquals("새 닉네임", after.getNickname());
        assertTrue(passwordEncoder.matches("new-password-2", after.getPassword()),
                "@DynamicUpdate — 닉네임 저장은 nickname만 UPDATE, 바뀐 비밀번호 보존");
    }

    @Test
    @DisplayName("닉네임 저장 도중 탈퇴가 커밋 → 탈퇴 상태가 그대로 남는다")
    void nicknameSave_doesNotUndoWithdrawal() {
        Long id = user.getId();

        newTx().execute(s1 -> {
            userService.updateMe(id, nickname("새 닉네임"));   // deleted_at = null을 읽음
            newTx().execute(s2 -> {
                userService.deleteAccount(id);                  // 그 사이 탈퇴 커밋
                return null;
            });
            return null;
        });                                                     // 전체 컬럼 UPDATE였다면 deleted_at = null로 되살림

        assertTrue(userRepository.findById(id).orElseThrow().isDeleted(),
                "@DynamicUpdate — 닉네임 저장이 deleted_at을 건드리지 않는다");
    }

    private TransactionTemplate newTx() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private static UpdateMeRequest nickname(String nickname) {
        UpdateMeRequest req = new UpdateMeRequest();
        req.setNickname(nickname);
        return req;
    }

    private static ChangePasswordRequest passwordChange(String current, String next) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(next);
        return req;
    }

    @Test
    @DisplayName("비밀번호 변경 도중 탈퇴가 커밋 → 탈퇴가 그대로 남는다 (설정 탭 두 개로 가능한 경합)")
    void passwordChange_doesNotUndoWithdrawal() {
        Long id = user.getId();

        newTx().execute(s1 -> {
            userRepository.findById(id).orElseThrow();   // 비밀번호 변경 트랜잭션이 먼저 읽음 (deleted_at = null) — BCrypt 세 번 도는 사이
            newTx().execute(s2 -> {
                userService.deleteAccount(id);           // 다른 탭에서 탈퇴 커밋
                return null;
            });
            userService.changePassword(id, passwordChange("old-password-1", "new-password-2"));   // 읽어 둔 사용자를 고쳐 저장
            return null;
        });   // 전체 컬럼 UPDATE였다면 deleted_at = null로 되살림

        assertTrue(userRepository.findById(id).orElseThrow().isDeleted(),
                "@DynamicUpdate — 비밀번호 변경은 password만 UPDATE, 그 사이 탈퇴 보존");
    }
}
