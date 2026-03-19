package com.vocamaster.common;

import com.vocamaster.user.User;
import org.springframework.security.core.context.SecurityContextHolder;

// 현재 로그인한 유저를 가져오는 유틸 (Spring의 @CurrentUser 대용)
public class CurrentUser {

    public static User get() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    public static Long getId() {
        return get().getId();
    }
}
