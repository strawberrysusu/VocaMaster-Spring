package com.vocamaster.review.dto;

import com.vocamaster.review.ReviewSubmission;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BatchAnswerResponse {

    private String submissionId;
    private int total;
    private int known;
    private int unknown;

    /** true면 이번 요청으로 진행도가 다시 움직이지 않았다는 뜻 (재전송) */
    private boolean alreadySubmitted;

    /**
     * 카드별 반영 결과. <b>재전송이면 null</b> —
     * StudyRecord에 '그 답변 직후의 박스'를 저장하지 않으므로, 지금 CardProgress를 읽어 봐야
     * 그 사이 다른 학습으로 바뀐 값일 수 있다. 없는 값을 그럴듯하게 지어내지 않는다.
     */
    private List<ReviewAnswerResponse> results;

    public static BatchAnswerResponse alreadySubmitted(ReviewSubmission done) {
        return BatchAnswerResponse.builder()
                .submissionId(done.getSubmissionId())
                .total(done.getAnswerCount())
                .known(done.getKnownCount())
                .unknown(done.getAnswerCount() - done.getKnownCount())
                .alreadySubmitted(true)
                .results(null)
                .build();
    }
}
