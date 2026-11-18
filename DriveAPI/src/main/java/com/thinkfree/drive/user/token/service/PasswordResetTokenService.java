package com.thinkfree.drive.user.token.service;

import com.thinkfree.drive.user.token.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 비밀번호 재설정 토큰 정리 — 발급·검증은 UserService 가 저장소로 직접 한다 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetTokenService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /** 계정 완전 삭제 cascade — 대상 계정들의 재설정 토큰 */
    public void deleteByUsers(List<Integer> userSeqList) {
        passwordResetTokenRepository.deleteByUserSeqList(userSeqList);
    }
}
