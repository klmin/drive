package com.thinkfree.drive.branding.repository;

import com.thinkfree.drive.branding.entity.Branding;
import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.jooq.tables.records.TbBrandingRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;

import static com.thinkfree.drive.jooq.Tables.TB_BRANDING;

/**
 * tb_branding 쓰기 — 구 BrandingMapper.xml 의 upsertWithImages·upsertTextOnly 를 jOOQ 로({@code ON CONFLICT (tenant_seq) DO UPDATE} 는 JPQL 로 표현 불가). 읽기는 {@link DefaultBrandingQueryRepository}.
 * <ul>
 *   <li>등록 감사 = {@link JdbcAudit}(시큐리티 사용자 우선(없으면 선언 값), 시각은 선언 값 없으면 현재 시각 초 절삭) + 엔티티 write-back. 구 XML 과 같이 <b>충돌 시 update_user_seq·update_date 에도 같은 값</b>(register_* 는 기존 유지)</li>
 *   <li>use_default 는 char(1) Y/N(생성 코드의 Boolean 변환), 이미지는 bytea(null 허용). {@code upsertTextOnly} 는 이미지 컬럼을 INSERT 에도 SET 에도 넣지 않는다(신규면 NULL, 충돌이면 기존 유지)</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class DefaultBrandingRepository implements BrandingRepository {

    private final DSLContext dsl;

    @Override
    public void upsertWithImages(Branding e) {
        Integer registerUserSeq = JdbcAudit.userSeqOr(e.getRegisterUserSeq());
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        e.assignRegisterAudit(registerUserSeq, registerDate);
        dsl.insertInto(TB_BRANDING,
                        TB_BRANDING.TENANT_SEQ, TB_BRANDING.TAB_TITLE, TB_BRANDING.SERVICE_NAME, TB_BRANDING.USE_DEFAULT,
                        TB_BRANDING.FAVICON_DATA, TB_BRANDING.FAVICON_CONTENT_TYPE, TB_BRANDING.LOGO_ICON_DATA, TB_BRANDING.LOGO_ICON_CONTENT_TYPE,
                        TB_BRANDING.LOGO_IMAGE_DATA, TB_BRANDING.LOGO_IMAGE_CONTENT_TYPE, TB_BRANDING.EMAIL_LOGO_DATA, TB_BRANDING.EMAIL_LOGO_CONTENT_TYPE,
                        TB_BRANDING.REGISTER_USER_SEQ, TB_BRANDING.REGISTER_DATE)
                .values(e.getTenantSeq(), e.getTabTitle(), e.getServiceName(), e.getUseDefault(),
                        e.getFaviconData(), e.getFaviconContentType(), e.getLogoIconData(), e.getLogoIconContentType(),
                        e.getLogoImageData(), e.getLogoImageContentType(), e.getEmailLogoData(), e.getEmailLogoContentType(),
                        registerUserSeq, registerDate)
                .onConflict(TB_BRANDING.TENANT_SEQ)
                .doUpdate()
                .set(TB_BRANDING.FAVICON_DATA, e.getFaviconData())
                .set(TB_BRANDING.FAVICON_CONTENT_TYPE, e.getFaviconContentType())
                .set(TB_BRANDING.LOGO_ICON_DATA, e.getLogoIconData())
                .set(TB_BRANDING.LOGO_ICON_CONTENT_TYPE, e.getLogoIconContentType())
                .set(TB_BRANDING.LOGO_IMAGE_DATA, e.getLogoImageData())
                .set(TB_BRANDING.LOGO_IMAGE_CONTENT_TYPE, e.getLogoImageContentType())
                .set(TB_BRANDING.EMAIL_LOGO_DATA, e.getEmailLogoData())
                .set(TB_BRANDING.EMAIL_LOGO_CONTENT_TYPE, e.getEmailLogoContentType())
                .set(textUpdate(e, registerUserSeq, registerDate))
                .execute();
    }

    @Override
    public void upsertTextOnly(Branding e) {
        Integer registerUserSeq = JdbcAudit.userSeqOr(e.getRegisterUserSeq());
        Instant registerDate = JdbcAudit.nowOr(e.getRegisterDate());
        e.assignRegisterAudit(registerUserSeq, registerDate);
        dsl.insertInto(TB_BRANDING,
                        TB_BRANDING.TENANT_SEQ, TB_BRANDING.TAB_TITLE, TB_BRANDING.SERVICE_NAME, TB_BRANDING.USE_DEFAULT,
                        TB_BRANDING.REGISTER_USER_SEQ, TB_BRANDING.REGISTER_DATE)
                .values(e.getTenantSeq(), e.getTabTitle(), e.getServiceName(), e.getUseDefault(), registerUserSeq, registerDate)
                .onConflict(TB_BRANDING.TENANT_SEQ)
                .doUpdate()
                .set(textUpdate(e, registerUserSeq, registerDate))
                .execute();
    }

    /** 충돌 시 두 upsert 가 같이 고치는 텍스트·플래그 + 수정 감사(등록 감사와 같은 값) */
    private TbBrandingRecord textUpdate(Branding e, Integer registerUserSeq, Instant registerDate) {
        TbBrandingRecord r = dsl.newRecord(TB_BRANDING);
        r.setTabTitle(e.getTabTitle());
        r.setServiceName(e.getServiceName());
        r.setUseDefault(e.getUseDefault());
        r.setUpdateUserSeq(registerUserSeq);
        r.setUpdateDate(registerDate);
        return r;
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return dsl.deleteFrom(TB_BRANDING).where(TB_BRANDING.TENANT_SEQ.eq(tenantSeq)).execute();
    }
}
