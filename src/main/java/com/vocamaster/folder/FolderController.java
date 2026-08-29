package com.vocamaster.folder;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.folder.dto.FolderRequest;
import com.vocamaster.folder.dto.FolderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Folders - 덱 분류 폴더")
@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping
    @Operation(summary = "내 폴더 목록")
    public List<FolderResponse> findAll() {
        return folderService.findAll(CurrentUser.getId());
    }

    @PostMapping
    @Operation(summary = "폴더 생성")
    public FolderResponse create(@Valid @RequestBody FolderRequest req) {
        return folderService.create(CurrentUser.getId(), req);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "폴더 이름 변경")
    public FolderResponse rename(@PathVariable Long id, @Valid @RequestBody FolderRequest req) {
        return folderService.rename(id, CurrentUser.getId(), req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "폴더 삭제 — 소속 덱은 미분류로 (SET NULL, 덱·카드는 삭제되지 않음)")
    public Map<String, String> remove(@PathVariable Long id) {
        folderService.remove(id, CurrentUser.getId());
        return Map.of("message", "폴더가 삭제되었습니다 (덱은 미분류로 이동)");
    }
}
