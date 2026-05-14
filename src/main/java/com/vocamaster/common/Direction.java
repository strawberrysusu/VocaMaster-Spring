package com.vocamaster.common;

import com.vocamaster.common.exception.BadRequestException;


public enum Direction {
    FRONT_TO_BACK("front_to_back"),
    BACK_TO_FRONT("back_to_front");

    private final String value;

    Direction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isFrontToBack() {
        return this == FRONT_TO_BACK;
    }

    public static Direction from(String value) {
        for (Direction d : values()) {
            if (d.value.equals(value)) {
                return d;
            }
        }
        throw new BadRequestException
                ("direction은 'front_to_back' 또는 'back_to_front'만 가능합니다. 입력값: " + value);
    }
}
