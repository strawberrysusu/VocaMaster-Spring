package com.vocamaster.cardimport;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportFileRequest;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import com.vocamaster.deck.dto.CreateDeckRequest;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final CardRepository cardRepository;
    private final DeckService deckService;
    private static final int MAX_LINES= 1000;
    private static final int MAX_TEXT = 255;      // cards.front/back VARCHAR(255)
    private static final int MAX_READING = 200;   // cards.reading VARCHAR(200)
    private static final List<String> DEFAULT_SEPARATOR_CANDIDATES = List.of("\t", "|", ":", ",", "-");
    private static final String DEFAULT_SEPARATOR = "-";

    // 텍스트 파싱 → 미리보기
    public PreviewResponse preview(ImportRequest req) {
        var parsed = parse(req.getText(), req.getSeparator());
        return PreviewResponse.builder()
                .cards(parsed.cards)
                .failed(parsed.failed)
                .totalParsed(parsed.cards.size())
                .failedCount(parsed.failed.size())
                .build();
    }

    // 파일 한 개 = 덱 생성 + 카드 등록을 "한" 트랜잭션으로 (Codex 검산 2026-08-29).
    // 예전 프론트는 덱 생성과 import를 두 요청으로 보내, 두 번째가 실패하면 빈 덱이 남았다.
    // importCards()는 내부 호출(self-invocation)이라 그 @Transactional 프록시는 안 타지만,
    // 이 메서드의 트랜잭션이 이미 열려 있어 전체가 하나의 경계 — 파싱 초과·DB 오류 시 덱 생성까지 롤백.
    @Transactional
    public ImportResponse createDeckAndImport(Long userId, ImportFileRequest req) {
        CreateDeckRequest createReq = new CreateDeckRequest();
        createReq.setTitle(req.getTitle());
        Long deckId = deckService.create(userId, createReq).getId();
        if (req.getFolderId() != null) {
            deckService.moveToFolder(deckId, userId, req.getFolderId());   // 같은 트랜잭션 — 남의 폴더면 덱 생성까지 롤백
        }

        ImportRequest importReq = new ImportRequest();
        importReq.setText(req.getText());
        importReq.setSeparator(req.getSeparator() == null ? "" : req.getSeparator());
        ImportResponse result = importCards(deckId, userId, importReq);
        return ImportResponse.builder()                 // ImportResponse는 @Builder 불변 — deckId만 얹어 재조립
                .deckId(deckId)
                .imported(result.getImported())
                .skipped(result.getSkipped())
                .failed(result.getFailed())
                .failedCount(result.getFailedCount())
                .build();
    }

    // 텍스트 파싱 → 실제 import
    // 전체가 한 트랜잭션 — 중간에 DB 오류가 나면 앞서 저장된 카드까지 전부 취소 (부분 커밋 방지, P1-6)
    @Transactional
    public ImportResponse importCards(Long deckId, Long userId, ImportRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);
        var parsed = parse(req.getText(), req.getSeparator());

        //기존 카드 front 수집(중복 검사용)
        Set<String> existingFronts = cardRepository.findByDeckId(deckId).stream()
                .map(Card::getFront)
                .collect(Collectors.toSet());

        int imported = 0;
        int skipped = 0;
        for (var c : parsed.cards) {
            String front = c.get("front");
            if (existingFronts.contains(front)) {
                skipped++;
                continue;                       // 이미 있음 → 건너뜀
            }
            Card card = Card.builder()
                    .front(front)
                    .back(c.get("back"))
                    .reading(c.get("reading"))          // 3칸 포맷일 때만 존재, 아니면 null
                    .deck(deck)
                    .build();
            cardRepository.save(card);
            existingFronts.add(front);          // 같은 import 내 중복도 막음
            imported++;
        }

        return ImportResponse.builder()
                .imported(imported)
                .skipped(skipped)
                .failed(parsed.failed)
                .failedCount(parsed.failed.size())
                .build();
    }

    private ParseResult parse(String text, String separator) {
        List<Map<String, String>> cards = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        String[] lines = text.split("\\n");
        if (lines.length > MAX_LINES){
            throw new BadRequestException(
                    "한 번에 최대  " + MAX_LINES + "줄까지 등록할 수 있습니다. 현재 " + lines.length + "줄입니다.");
        }
        String sep = (separator == null || separator.isBlank())
                ? detectSeparator(lines)
                : separator;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // 2칸 = 단어 | 뜻, 3칸 = 단어 | 읽기 | 뜻 (읽기는 요미가나, V14).
            // limit 없이 split — 예전 split(…, 3)은 a|b|c|d를 [a, b, "c|d"]로 합쳐 '4칸 실패' 약속을 안 지켰다 (Codex 감사)
            String[] parts = line.split(Pattern.quote(sep), -1);
            String front = parts[0].trim();
            String back = parts.length >= 2 ? parts[parts.length - 1].trim() : "";
            String reading = parts.length == 3 ? parts[1].trim() : "";
            boolean shapeOk = (parts.length == 2 || parts.length == 3) && !front.isEmpty() && !back.isEmpty();
            if (!shapeOk) {
                failed.add(Map.of("line", i + 1, "content", line));
            } else if (front.length() > MAX_TEXT || back.length() > MAX_TEXT || reading.length() > MAX_READING) {
                // DB 컬럼(255/200)보다 길면 등록 시 500·전체 롤백 — 미리보기 단계에서 실패 줄로 (Codex 감사)
                failed.add(Map.of("line", i + 1, "content", line + "  (너무 김: 단어·뜻 " + MAX_TEXT + "자, 읽기 " + MAX_READING + "자 이내)"));
            } else {
                Map<String, String> card = new HashMap<>();
                card.put("front", front);
                card.put("back", back);
                if (!reading.isEmpty()) card.put("reading", reading);
                cards.add(card);
            }
        }
        return new ParseResult(cards, failed);
    }

    private String detectSeparator(String[] lines) {
        for (String candidate : DEFAULT_SEPARATOR_CANDIDATES) {
            int checked = 0;
            int twoParts = 0;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (checked >= 5) break;
                checked++;
                int pieces = line.split(Pattern.quote(candidate)).length;   // limit 없음 = 정확한 조각 수
                if (pieces == 2 || pieces == 3) {                            // 2칸(단어|뜻) 또는 3칸(단어|읽기|뜻)
                    twoParts++;
                }
            }
            if (checked > 0 && twoParts * 2 > checked) {
                return candidate;
            }
        }
        return DEFAULT_SEPARATOR;
    }

    private record ParseResult(List<Map<String, String>> cards, List<Map<String, Object>> failed) {}
}
