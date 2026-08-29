package com.vocamaster.folder.dto;

import com.vocamaster.folder.Folder;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FolderResponse {

    private Long id;
    private String name;

    public static FolderResponse from(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .build();
    }
}
