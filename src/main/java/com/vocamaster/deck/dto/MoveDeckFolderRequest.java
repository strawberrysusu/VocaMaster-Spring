package com.vocamaster.deck.dto;

import lombok.Getter;
import lombok.Setter;

/** 덱을 폴더로 이동 — folderId null이면 미분류로 */
@Getter @Setter
public class MoveDeckFolderRequest {
    private Long folderId;
}
