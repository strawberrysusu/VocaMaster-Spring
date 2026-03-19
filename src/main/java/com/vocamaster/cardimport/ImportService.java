package com.vocamaster.cardimport;

import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final CardRepository cardRepository;
    private final DeckService deckService;

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
    public ImportResponse importCards(Long deckId, Long userId, ImportRequest req) {
        Deck deck = deckService.verifyOwner(deckId, userId);
        var parsed = parse(req.getText(), req.getSeparator());

        int imported = 0;
        for (var c : parsed.cards) {
            Card card = Card.builder()
                    .front(c.get("front"))
                    .back(c.get("back"))
                    .deck(deck)
                    .build();
            cardRepository.save(card);
            imported++;
        }

        return ImportResponse.builder()
                .imported(imported)
                .failed(parsed.failed)
                .failedCount(parsed.failed.size())
                .build();
    }

    private ParseResult parse(String text, String separator) {
        List<Map<String, String>> cards = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        String[] lines = text.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(separator.replace("|", "\\|"), 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                cards.add(Map.of("front", parts[0].trim(), "back", parts[1].trim()));
            } else {
                failed.add(Map.of("line", i + 1, "content", line));
            }
        }

        return new ParseResult(cards, failed);
    }

    private record ParseResult(List<Map<String, String>> cards, List<Map<String, Object>> failed) {}
}
