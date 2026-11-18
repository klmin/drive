package com.thinkfree.drive.mcp.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MCP 툴 공용 반환 봉투 — 모든 툴은 성공 시 {data}, 예상된 실패(검증·권한·비즈니스) 시 {error[, errorCode]}를
 * 반환한다. LLM이 error 필드를 읽고 다음 행동을 판단할 수 있도록 인밴드로 전달하며,
 * 예상 못한 예외는 잡지 않고 전파시켜 프레임워크의 isError 처리에 맡긴다.
 * 예외적으로 DataAccessException(SQL 예외)은 프레임워크 isError 경로가 SQL 전문·매퍼 파일 경로까지 클라이언트에 노출하므로
 * 여기서 잡아 서버 로그에만 남기고 일반 메시지로 인밴드 반환한다.
 * 어떤 예외가 인밴드로 변환되는지(3단 에러 규약)는 execute에 응집돼 있다.
 */
@Slf4j
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolResult<T>
(
    T data,
    String error,
    String errorCode
)
{
    private static final String DATA_ACCESS_ERROR_MESSAGE = "요청 처리 중 서버 오류가 발생했습니다. 입력값의 형식·길이를 확인하고 다시 시도하세요.";

    public static <T> McpToolResult<T> success(T data) {
        return new McpToolResult<>(data, null, null);
    }

    public static <T> McpToolResult<T> error(String message) {
        return new McpToolResult<>(null, message, null);
    }

    public static <T> McpToolResult<T> error(ApiRuntimeException e) {
        return new McpToolResult<>(null, e.getCode().getMessage(), e.getCode().getErrorCode());
    }

    /**
     * 툴 본문 공용 실행 래퍼 — 본문은 성공 결과만 반환하고, 예상된 실패(검증 IllegalArgumentException,
     * 비즈니스 ApiRuntimeException)는 예외로 던진다. 성공은 success로 감싸고 예상된 실패는 error 인밴드
     * 반환으로 변환하며, 그 외 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
     */
    public static <T> McpToolResult<T> execute(Supplier<T> body) {
        try {
            return success(body.get());
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (ApiRuntimeException e) {
            return error(e);
        } catch (DataAccessException e) {
            // SQL 예외 원문(SQL 전문·매퍼 절대경로)이 isError로 새는 것을 차단 — 상세는 서버 로그로
            log.error("[MCP] tool data access failure", e);
            return error(DATA_ACCESS_ERROR_MESSAGE);
        }
    }

    /**
     * 인증 사용자를 전제하는 툴 본문용 execute 변형 — 모든 툴이 반복하던
     * requireUserDetails 보일러플레이트를 관문으로 끌어올려, 인증 조회 없이 실행되는 툴이
     * 구조적으로 불가능하게 한다. 미인증은 시큐리티 체인이 401로 이미 차단한 경로라
     * 여기 도달 시 컨텍스트가 비어 있으면 NPE로 크게 실패한다(전파 → isError).
     */
    public static <T> McpToolResult<T> executeAuthed(Function<CustomUserDetails, T> body) {
        return execute(() -> body.apply(SecurityUtil.requireUserDetails()));
    }
}
