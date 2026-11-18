package com.thinkfree.drive.application.apikey.converter;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.application.apikey.ApiKeyActor;
import com.thinkfree.drive.application.apikey.command.ApiKeyIssueCommand;
import com.thinkfree.drive.application.apikey.command.ApiKeyListCommand;
import com.thinkfree.drive.apikey.dto.ApiKeyIssueDto;
import com.thinkfree.drive.apikey.query.ApiKeyListQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Command + Actor → 도메인 dto 변환 전담.
 * 어댑터의 request → Command 변환은 각 진입점의 컨버터가 담당한다.
 */
@Mapper(componentModel = "spring", imports = {CursorPaginationQuery.class})
public interface ApiKeyCommandConverter {

    /** tenantSeq 는 Command·Actor 양쪽에 있어 출처를 명시한다 — 키가 속할 테넌트는 요청값이다 */
    @Mapping(target = "tenantSeq", source = "command.tenantSeq")
    @Mapping(target = "loginUserSeq", source = "actor.userSeq")
    @Mapping(target = "loginRoleType", source = "actor.roleType")
    @Mapping(target = "loginTenantSeq", source = "actor.tenantSeq")
    @Mapping(target = "requestedRoleType", source = "command.roleType")
    ApiKeyIssueDto toIssueDto(ApiKeyIssueCommand command, ApiKeyActor actor);

    /**
     * 조회 범위를 결정하는 컬럼만 채운다 — 관리자는 tenantSeq, 사용자는 registerUserSeq,
     * 슈퍼관리자는 둘 다 비우고 요청 필터(command.tenantSeq)만 쓴다.
     * 관리자·슈퍼관리자의 registerUserSeq 는 범위가 아니라 요청 필터(command.registerUserSeq)다.
     */
    @Mapping(target = "tenantSeq", source = "tenantSeq")
    @Mapping(target = "registerUserSeq", source = "registerUserSeq")
    @Mapping(target = "page", expression = "java(CursorPaginationQuery.of(command.cursorValue(), command.cursorId(), command.cursorType(), command.pageSize()))")
    ApiKeyListQuery toListQuery(ApiKeyListCommand command, Integer tenantSeq, Integer registerUserSeq);
}
