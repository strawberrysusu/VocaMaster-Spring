package com.vocamaster.cardimport;

import com.vocamaster.cardimport.dto.ImportFileRequest;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Import - 카드 일괄 등록")
@RestController
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/import/preview")
    @Operation(summary = "텍스트 파싱 미리보기")
    public PreviewResponse preview(@Valid @RequestBody ImportRequest req) {
        return importService.preview(req);
    }

    @PostMapping("/decks/import-file")
    @Operation(summary = "파일 한 개 = 덱 생성 + 카드 등록 — 단일 트랜잭션 (실패 시 빈 덱도 안 남음)")
    public ImportResponse importFile(@Valid @RequestBody ImportFileRequest req) {
        return importService.createDeckAndImport(CurrentUser.getId(), req);
    }

    @PostMapping("/decks/{deckId}/import")
    @Operation(summary = "텍스트 파싱 후 카드 일괄 등록")
    public ImportResponse importCards(
            @PathVariable Long deckId,
            @Valid @RequestBody ImportRequest req) {
        return importService.importCards(deckId, CurrentUser.getId(), req);
    }
}
