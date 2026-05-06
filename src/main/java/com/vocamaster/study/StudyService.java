package com.vocamaster.study;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.common.Direction;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.ForbiddenException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import com.vocamaster.quiz.QuizAttemptRepository;
import com.vocamaster.study.dto.*;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final StudySessionRepository sessionRepository;
    private final StudyRecordRepository recordRepository;
    private final CardRepository cardRepository;
    private final DeckService deckService;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    // 학습 세션 시작
    public StudySessionResponse startSession(Long deckId, Long userId, StartStudyRequest req) {
        Direction.from(req.getDirection()); // direction 유효성 검증
        Deck deck = deckService.verifyOwner(deckId, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        List<Card> cards;
        if (Boolean.TRUE.equals(req.getStarredOnly())) {
            cards = cardRepository.findByDeckIdAndStarredTrue(deckId);
        } else {
            cards = cardRepository.findByDeckId(deckId);
        }

        if (cards.isEmpty()) {
            throw new NotFoundException("학습할 카드가 없습니다");
        }

        StudySession session = StudySession.builder()
                .deck(deck)
                .user(user)
                .direction(req.getDirection())
                .starredOnly(req.getStarredOnly() != null ? req.getStarredOnly() : false)
                .build();

        sessionRepository.save(session);

        return StudySessionResponse.builder()
                .sessionId(session.getId())
                .direction(session.getDirection())
                .cards(cards.stream().map(CardResponse::from).toList())
                .totalCards(cards.size())
                .build();
    }

    // 카드별 안다/모른다 기록
    public StudyRecordResponse recordAnswer(Long sessionId, Long userId, RecordStudyRequest req) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학습 세션을 찾을 수 없습니다"));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }

        Card card = cardRepository.findById(req.getCardId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다"));

        // 이 카드가 세션의 deck에 속하는지 검증
        if (!card.getDeck().getId().equals(session.getDeck().getId())) {
            throw new BadRequestException("이 학습 세션의 단어장에 속하지 않는 카드입니다");
        }

        StudyRecord record = StudyRecord.builder()
                .session(session)
                .card(card)
                .known(req.getKnown())
                .build();

        recordRepository.save(record);
        return StudyRecordResponse.from(record);
    }

    // 학습 세션 결과 요약 (모르는 카드 목록 포함)
    public StudySummaryResponse getSessionSummary(Long sessionId, Long userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ForbiddenException("학습 세션을 찾을 수 없습니다"));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }

        List<StudyRecord> records = session.getRecords();
        long total = records.size();
        long known = records.stream().filter(StudyRecord::getKnown).count();

        List<CardResponse> unknownCards = records.stream()
                .filter(r -> !r.getKnown())
                .map(r -> CardResponse.from(r.getCard()))
                .toList();

        return StudySummaryResponse.builder()
                .sessionId(session.getId())
                .deckTitle(session.getDeck().getTitle())
                .direction(session.getDirection())
                .total(total)
                .known(known)
                .unknown(total - known)
                .accuracy(total > 0 ? Math.round((double) known / total * 100) : 0)
                .unknownCards(unknownCards)
                .build();
    }

    // 덱별 학습 통계 (플래시카드 + 퀴즈 통합)
    public DeckStatsResponse getDeckStats(Long deckId, Long userId) {
        deckService.verifyOwner(deckId, userId);

        long totalCards = cardRepository.countByDeckId(deckId);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        // 플래시카드 학습 통계
        var sessions = sessionRepository.findByDeckIdAndUserIdOrderByCreatedAtDesc(deckId, userId);
        long studyTotal = 0, studyKnown = 0;
        for (var s : sessions) {
            for (var r : s.getRecords()) {
                studyTotal++;
                if (r.getKnown()) studyKnown++;
            }
        }

        // 퀴즈 통계
        long quizTotal = quizAttemptRepository.countByDeckIdAndUserId(deckId, userId);
        long quizCorrect = quizAttemptRepository.countByDeckIdAndUserIdAndIsCorrectTrue(deckId, userId);

        // 최근 7일
        long recentStudy = sessionRepository.countByDeckIdAndUserIdAndCreatedAtAfter(deckId, userId, sevenDaysAgo);
        long recentQuiz = quizAttemptRepository.countByDeckIdAndUserIdAndCreatedAtAfter(deckId, userId, sevenDaysAgo);

        return DeckStatsResponse.builder()
                .deckId(deckId)
                .totalCards(totalCards)
                .study(DeckStatsResponse.StudyStat.builder()
                        .totalSessions(sessions.size())
                        .totalRecords(studyTotal)
                        .known(studyKnown)
                        .unknown(studyTotal - studyKnown)
                        .accuracy(studyTotal > 0 ? Math.round((double) studyKnown / studyTotal * 100) : 0)
                        .build())
                .quiz(DeckStatsResponse.QuizStat.builder()
                        .totalAttempts(quizTotal)
                        .correct(quizCorrect)
                        .wrong(quizTotal - quizCorrect)
                        .accuracy(quizTotal > 0 ? Math.round((double) quizCorrect / quizTotal * 100) : 0)
                        .build())
                .recent7Days(DeckStatsResponse.RecentStat.builder()
                        .studySessions(recentStudy)
                        .quizAttempts(recentQuiz)
                        .build())
                .build();
    }
}
