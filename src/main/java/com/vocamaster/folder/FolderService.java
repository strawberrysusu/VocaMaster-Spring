package com.vocamaster.folder;

import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.folder.dto.FolderRequest;
import com.vocamaster.folder.dto.FolderResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public List<FolderResponse> findAll(Long userId) {
        return folderRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(FolderResponse::from)
                .toList();
    }

    @Transactional
    public FolderResponse create(Long userId, FolderRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));
        Folder folder = folderRepository.save(Folder.builder()
                .user(user)
                .name(req.getName().trim())
                .build());
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse rename(Long folderId, Long userId, FolderRequest req) {
        Folder folder = ownFolderOrThrow(folderId, userId);
        folder.setName(req.getName().trim());
        return FolderResponse.from(folder);
    }

    // 폴더 삭제 — 소속 덱들의 folder_id는 FK ON DELETE SET NULL이 DB에서 정리 (미분류 복귀, V20)
    @Transactional
    public void remove(Long folderId, Long userId) {
        Folder folder = ownFolderOrThrow(folderId, userId);
        folderRepository.delete(folder);
    }

    /** 다른 서비스(덱 이동·원자 임포트)의 소유 검증 공용 지점 — 남의 폴더 = 없는 폴더와 같은 404 */
    public Folder ownFolderOrThrow(Long folderId, Long userId) {
        return folderRepository.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new NotFoundException("폴더를 찾을 수 없습니다"));
    }
}
