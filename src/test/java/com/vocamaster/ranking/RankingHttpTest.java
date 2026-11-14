package com.vocamaster.ranking;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.JwtProvider;
import com.vocamaster.stats.DailyUserStat;
import com.vocamaster.stats.DailyUserStatRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 학습 랭킹 API — 가린 닉네임은 응답 어디에도 진짜 값이 실리지 않는다, 가리기 설정 API (ADR-059) */
@AutoConfigureMockMvc
class RankingHttpTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private DailyUserStatRepository statRepository;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("rank_http_" + System.nanoTime() + "@test.com")
                .password("x").nickname("비밀닉네임123").build());
        statRepository.save(DailyUserStat.builder().user(user).statDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
                .studyCount(8_000_000).streak(1).build());
        token = jwtProvider.createAccessToken(user.getId(), user.getEmail());
    }

    @Test
    @DisplayName("닉네임 가리기를 켜면 랭킹 응답 어디에도 진짜 닉네임이 없다 — 화면이 아니라 서버가 가린다")
    void masked_nicknameNeverLeaves() throws Exception {
        mvc.perform(patch("/users/me/ranking").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"masked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingMasked").value(true));

        mvc.perform(get("/rankings/study").param("period", "today").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.name").value("비밀닉***"))
                .andExpect(jsonPath("$.me.me").value(true))
                .andExpect(content().string(not(containsString("비밀닉네임123"))));
    }

    @Test
    @DisplayName("가리기를 끄면 닉네임 그대로, 기간이 틀리면 400, 로그인 안 하면 401")
    void plainAndErrors() throws Exception {
        mvc.perform(get("/rankings/study").param("period", "all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.name").value("비밀닉네임123"));
        mvc.perform(get("/rankings/study").param("period", "week").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/rankings/study"))
                .andExpect(status().isUnauthorized());
    }
}
