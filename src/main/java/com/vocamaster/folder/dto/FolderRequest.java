package com.vocamaster.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FolderRequest {

    @NotBlank
    @Size(max = 100)          // folders.name varchar(100)
    private String name;
}
