package com.vocamaster.review;


import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckService;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private static final int MAX_BOX = 6;

    // 박스별 복습 간격 (ADR-029 확정값). boxLevel N → BOX_INTERVALS[N - 1]
    private static final Duration[] BOX_INTERVALS = {
            Duration.ofMinutes(10), // box 1
            Duration.ofDays(1),     // box 2
            Duration.ofDays(3),     // box 3
            Duration.ofDays(7),     // box 4
            Duration.ofDays(14),    // box 5
            Duration.ofDays(30),    // box 6
    };

    private final CardProgressRepository cardProgressRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;

    public ReviewAnswerResponse recordAnswer(Long userId, Long cardId, boolean correct) {
        // ① 카드 실존 확인
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("카드를 찾을 수 없습니다"));

        // ② 검문소 — 남의 덱 카드면 403
        deckService.verifyOwner(card.getDeck().getId(), userId);

        // ③ 성적표 꺼내기 — 처음 만난 카드면 box 1로 생성
        CardProgress progress = cardProgressRepository.findByUserIdAndCardId(userId, cardId)
                .orElseGet(() -> newProgress(userId, card));

        // ④ 상자 옮기기 — 맞으면 한 칸 위로(천장 6), 틀리면 box 1 풀 리셋 (ADR-029)
        if (correct) {
            progress.setBoxLevel(Math.min(progress.getBoxLevel() + 1, MAX_BOX));
            progress.setCorrectStreak(progress.getCorrectStreak() + 1);
        } else {
            progress.setBoxLevel(1);
            progress.setCorrectStreak(0);
            progress.setWrongCount(progress.getWrongCount() + 1);
        }

        // ⑤ 새 박스의 간격만큼 뒤로 다음 복습 시각 도장
        LocalDateTime now = LocalDateTime.now();
        progress.setLastReviewedAt(now);
        progress.setNextReviewAt(now.plus(BOX_INTERVALS[progress.getBoxLevel() - 1]));

        // ⑥ 저장 — 처음 만난 카드는 INSERT, 기존 카드는 더티체킹으로도 저장되지만 패턴 통일
        return ReviewAnswerResponse.from(cardProgressRepository.save(progress));
    }

    // 처음 만난 카드의 성적표 생성 (box 1, 즉시 복습 대상)
    private CardProgress newProgress(Long userId, Card card) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
        return CardProgress.builder()
                .user(user)
                .card(card)
                .boxLevel(1)
                .correctStreak(0)
                .wrongCount(0)
                .nextReviewAt(LocalDateTime.now())
                .build();
    }
}
