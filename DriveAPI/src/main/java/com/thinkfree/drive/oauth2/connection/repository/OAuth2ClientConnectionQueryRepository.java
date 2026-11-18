package com.thinkfree.drive.oauth2.connection.repository;

import com.thinkfree.drive.oauth2.connection.dto.OAuth2ClientConnectionData;

import java.util.List;

/**
 * tb_oauth2_client_connection 읽기(목록 화면) — {@code DefaultOAuth2ClientConnectionService.findConnections} 가 주입. 쓰기·쓰기 흐름 안의 조회는 {@link OAuth2ClientConnectionRepository}.
 */
public interface OAuth2ClientConnectionQueryRepository {

    /** 사용자의 연결 목록 + 클라이언트 표시 이름(SAS 테이블 LEFT JOIN, 클라이언트 행이 지워졌으면 null), register_date DESC */
    List<OAuth2ClientConnectionData> findAllWithClientNameByUserSeq(int userSeq);
}
