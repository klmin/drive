package com.thinkfree.drive.resource.repository;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_USER;

/**
 * 이름을 정하는 경로(폴더 생성·이름 변경·이동·복원·업로드)의 직렬화 잠금(docs/design/upload-session-redesign.md 규칙 9, §3 #8·#9).
 * 이름 유니크 인덱스를 두지 않으므로, 이름이 정해지는 "자리"(부모 폴더 행, 루트면 사용자 행)를 {@code FOR NO KEY UPDATE} 로 잠그는 것이
 * 같은 위치의 동시 이름 결정을 막는 유일한 장치다. 같은 이름의 기존 행만 잠그던 방식(lockMaxCopyNumbers)은 그런 행이 없으면 아무것도 잠그지 못했다.
 * <p>
 * 부모 폴더 행을 잠그면 그 폴더를 휴지통으로 보내는 쪽(같은 행을 고친다)과도 직렬화되어, 휴지통 폴더 아래에 살아 있는 항목이 생기지 않는다.
 * 엔티티를 영속성 컨텍스트에 올리지 않는다(스칼라 조회).
 */
@Repository
@RequiredArgsConstructor
public class ResourceNamingLockRepository {

    private final DSLContext dsl;

    /** 루트에 이름을 정할 때 — 사용자 행 잠금. @return 행이 있으면 true */
    public boolean lockUser(int userSeq) {
        return dsl.select(TB_USER.USER_SEQ)
                  .from(TB_USER)
                  .where(TB_USER.USER_SEQ.eq(userSeq))
                  .forNoKeyUpdate()
                  .fetchOptional()
                  .isPresent();
    }

    /** 폴더 안에 이름을 정할 때 — 살아 있는(휴지통 아님) 폴더 행 잠금. @return 그런 폴더가 있으면 true */
    public boolean lockLiveFolder(int userSeq, long folderSeq) {
        return dsl.select(TB_RESOURCE.RESOURCE_SEQ)
                  .from(TB_RESOURCE)
                  .where(TB_RESOURCE.USER_SEQ.eq(userSeq))
                  .and(TB_RESOURCE.RESOURCE_SEQ.eq(folderSeq))
                  .and(TB_RESOURCE.RESOURCE_TYPE.eq(ResourceType.FOLDER))
                  .and(TB_RESOURCE.IS_DELETE.eq(false))
                  .forNoKeyUpdate()
                  .fetchOptional()
                  .isPresent();
    }
}
