package com.vocamaster.common.exception;

/**
 * 409 — 같은 자원에 대한 다른 요청이 먼저/동시에 처리 중이라 지금은 답을 확정할 수 없을 때.
 *
 * <p>{@code code}는 클라이언트가 <b>메시지 문자열을 파싱하지 않고</b> 분기하기 위한 것이다.
 * 일괄 제출의 409는 두 종류이고 대응이 정반대다 —
 * 일시적 경합은 <i>같은</i> 제출 ID로 재시도해야 하고,
 * 답안 불일치({@code SUBMISSION_MISMATCH})는 <i>새</i> 제출 ID로 보내야 한다.</p>
 */
public class ConflictException extends RuntimeException {

    /** 같은 submissionId로 내용이 다른 답안이 왔다 — 이 ID는 이미 소비됐다 */
    public static final String SUBMISSION_MISMATCH = "SUBMISSION_MISMATCH";

    private final String code;

    public ConflictException(String message) {
        this(message, "CONFLICT");
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
