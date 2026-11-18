package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.service.ResourceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@DisplayName("DefaultResourceService 폴더 이동 — 자기 자신·하위 폴더·같은 위치 거부")
class DefaultResourceServiceFolderMoveValidationTest extends RepositoryTest {

    private static final int ADMIN = 2;
    private static final int SUPER_ADMIN = 1;

    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void login() {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private Resource folder(int userSeq, String name, Resource parent) {
        Resource r = Resource.builder().userSeq(userSeq).ownerSeq(userSeq).resourceType(ResourceType.FOLDER)
                .parentFolderSeq(parent == null ? null : parent.getResourceSeq()).resourceName(name).originalFileName(name)
                .fullPathSeq(parent == null ? "" : parent.getFullPathSeq()).fullPath(parent == null ? "" : parent.getFullPath())
                .copyNumber(0).registerUserSeq(userSeq).build();
        resourceRepository.save(r);
        r.initFolderPath();
        resourceRepository.flush();
        return r;
    }

    private void newRequest() {
        entityManager.flush();
        entityManager.clear();
    }

    private void move(Resource folder, Resource destination, int targetUserSeq) {
        resourceService.folderMove(ResourceMoveDto.builder().resourceSeq(folder.getResourceSeq()).userSeq(ADMIN).targetUserSeq(targetUserSeq)
                .parentFolderSeq(destination == null ? null : destination.getResourceSeq()).resourceType(ResourceType.FOLDER).isDelete(false).build());
    }

    private static void assertUpdateFail(Runnable r) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiRuntimeException.class,
                e -> assertThat(e.getCode()).isEqualTo(ResourceErrorCode.UPDATE_FAIL));
    }

    private Long parentOf(Resource r) {
        return jdbc.queryForObject("SELECT parent_folder_seq FROM tb_resource WHERE resource_seq = ?", Long.class, r.getResourceSeq());
    }

    @Test
    @DisplayName("빈 폴더를 자기 자신으로 이동 — UPDATE_FAIL")
    void emptyFolder_intoSelf() {
        Resource a = folder(ADMIN, "a", null);
        newRequest();

        assertUpdateFail(() -> move(a, a, ADMIN));
    }

    @Test
    @DisplayName("내용 있는 폴더를 자기 자신으로 이동 — UPDATE_FAIL")
    void nonEmptyFolder_intoSelf() {
        Resource a = folder(ADMIN, "a", null);
        folder(ADMIN, "child", a);
        newRequest();

        assertUpdateFail(() -> move(a, a, ADMIN));
    }

    @Test
    @DisplayName("빈 자식 폴더로 이동 — UPDATE_FAIL")
    void intoEmptyChild() {
        Resource a = folder(ADMIN, "a", null);
        Resource child = folder(ADMIN, "child", a);
        newRequest();

        assertUpdateFail(() -> move(a, child, ADMIN));
    }

    @Test
    @DisplayName("빈 손자 폴더로 이동 — UPDATE_FAIL")
    void intoEmptyGrandchild() {
        Resource a = folder(ADMIN, "a", null);
        Resource child = folder(ADMIN, "child", a);
        Resource grandchild = folder(ADMIN, "grandchild", child);
        newRequest();

        assertUpdateFail(() -> move(a, grandchild, ADMIN));
    }

    @Test
    @DisplayName("현재 부모 폴더로 이동 — UPDATE_FAIL")
    void sameParent() {
        Resource p = folder(ADMIN, "p", null);
        Resource a = folder(ADMIN, "a", p);
        newRequest();

        assertUpdateFail(() -> move(a, p, ADMIN));
    }

    @Test
    @DisplayName("루트 폴더를 루트로 이동 — UPDATE_FAIL")
    void sameRoot() {
        Resource a = folder(ADMIN, "a", null);
        newRequest();

        assertUpdateFail(() -> move(a, null, ADMIN));
    }

    @Test
    @DisplayName("형제 폴더로 이동 — 성공")
    void intoSibling() {
        Resource a = folder(ADMIN, "a", null);
        Resource b = folder(ADMIN, "b", null);
        newRequest();

        move(a, b, ADMIN);

        assertThat(parentOf(a)).isEqualTo(b.getResourceSeq());
    }

    @Test
    @DisplayName("하위 폴더를 루트로 이동 — 성공")
    void childToRoot() {
        Resource p = folder(ADMIN, "p", null);
        Resource a = folder(ADMIN, "a", p);
        newRequest();

        move(a, null, ADMIN);

        assertThat(parentOf(a)).isNull();
    }

    @Test
    @DisplayName("다른 소유자의 루트로 이동 — 부모가 둘 다 null 이어도 같은 위치가 아니라 성공")
    void otherOwnerRoot() {
        Resource a = folder(ADMIN, "a", null);
        newRequest();

        move(a, null, SUPER_ADMIN);

        assertThat(jdbc.queryForObject("SELECT user_seq FROM tb_resource WHERE resource_seq = ?", Integer.class, a.getResourceSeq())).isEqualTo(SUPER_ADMIN);
    }
}
