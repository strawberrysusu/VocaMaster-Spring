package com.vocamaster.cardimport;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
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

            // 2칸 = 단어 | 뜻, 3칸 = 단어 | 읽기 | 뜻 (읽기는 요미가나, V14). 4칸 이상은 3번째 이후를 뜻으로 합치지 않고 실패 처리
            String[] parts = line.split(Pattern.quote(sep), 3);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                cards.add(Map.of("front", parts[0].trim(), "back", parts[1].trim()));
            } else if (parts.length == 3 && !parts[0].trim().isEmpty() && !parts[2].trim().isEmpty()) {
                Map<String, String> card = new HashMap<>();
                card.put("front", parts[0].trim());
                card.put("back", parts[2].trim());
                if (!parts[1].trim().isEmpty()) card.put("reading", parts[1].trim());
                cards.add(card);
            } else {
                failed.add(Map.of("line", i + 1, "content", line));
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
