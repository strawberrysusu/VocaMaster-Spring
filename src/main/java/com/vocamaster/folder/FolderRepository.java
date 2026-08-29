package com.vocamaster.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByUserIdOrderByCreatedAtAsc(Long userId);

    // 소유 검증을 조회에 접합 — "남의 폴더"와 "없는 폴더"가 같은 빈 결과 (존재 숨김 404, ADR-030 관례)
    Optional<Folder> findByIdAndUserId(Long id, Long userId);
}
