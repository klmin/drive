package com.thinkfree.drive.api.v1.common.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    /**
     * /error-page로 넘기면 원인이 화면에서 사라지므로, 컨테이너가 넘겨준
     * 원본 상태·URI·예외를 여기서 로그로 남긴다 — OAuth2 authorize/consent처럼
     * 리다이렉트로 끝나는 플로우의 실패 원인은 이 로그가 유일한 단서다.
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        log.warn("[CustomErrorController] error-page redirect - status : {}, uri : {}, message : {}",
                status, uri, message, (Throwable) exception);
        return "redirect:/error-page";
    }
}
