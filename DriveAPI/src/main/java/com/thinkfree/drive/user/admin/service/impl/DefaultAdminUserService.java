package com.thinkfree.drive.user.admin.service.impl;

import com.thinkfree.drive.license.service.LicenseService.SeatPolicy;
import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.exception.exception.AdminUserErrorCode;
import com.thinkfree.drive.user.admin.result.AdminUserInsertResult;
import com.thinkfree.drive.user.admin.service.AdminUserService;
import com.thinkfree.drive.user.entity.Password;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.enums.SignupType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.query.AdminUserBulkUpdateQuery;
import com.thinkfree.drive.user.query.UserDeleteMarkQuery;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.role.dto.UserRoleAssignmentDto;
import com.thinkfree.drive.user.role.service.UserRoleService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

import static com.thinkfree.drive.global.utils.PasswordEncoderUtils.ENCODER;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultAdminUserService implements AdminUserService {

    private final UserRepository userRepository;
    private final UserRoleService userRoleService;
    // SYSTEM_MANAGED 등록·수정 거부(USER_REGISTER·USER_UPDATE)는 진입점 AdminUserImportUseCase(bulkUpsert·importCsv)가 검증·잠금 전에 한다

    @Override
    public List<AdminUserInsertResult> registerAll(List<AdminUserSaveDto> saveDtos, SeatPolicy seatPolicy) {
        List<User> entities = saveDtos.stream()
                                      .map(DefaultAdminUserService::toUserEntity)
                                      .toList();

        userRepository.insertAll(entities, seatPolicy.isSeat(), seatPolicy.constrainValue());
        assignInitialRoles(entities, saveDtos);

        return toInsertResults(entities, saveDtos);
    }

    /**
     * update 행은 배치 UPDATE(유지 예외 — {@code @DynamicUpdate} 엔티티의 변경 감지는 JDBC 배치가 안 돼 행마다 왕복, B1).
     * 호출부(AdminUserImportUseCase.bulkUpsert)가 같은 트랜잭션에서 이 행들을 잠금으로 로드한 관리 엔티티는 고치지 않는다 — dirty 가 아니라 커밋 flush 가
     * 배치 결과를 덮어쓰지 않는다. 그 엔티티의 메모리 값은 배치 전 값이니 이 뒤로 읽지 않는다.
     */
    @Override
    public List<AdminUserInsertResult> upsertAll(List<AdminUserSaveDto> inserts, List<AdminUserBulkUpdateDto> updates, SeatPolicy seatPolicy) {
        List<User> insertEntities = inserts.stream()
                .map(DefaultAdminUserService::toUserEntity)
                .toList();

        if (!insertEntities.isEmpty()) {
            userRepository.insertAll(insertEntities, seatPolicy.isSeat(), seatPolicy.constrainValue());
            assignInitialRoles(insertEntities, inserts);
        }

        if (!updates.isEmpty()) {
            userRepository.updateAll(updates.stream().map(AdminUserBulkUpdateQuery::from).toList());
        }

        return toInsertResults(insertEntities, inserts);
    }

    /** INSERT 직후(키 채움) 계정마다 요청 역할 전부 부여 — 엔티티는 saveDtos 와 같은 순서로 만들었다. 등록자는 tb_user 와 같은 값 */
    private void assignInitialRoles(List<User> inserted, List<AdminUserSaveDto> saveDtos) {
        userRoleService.assignInitialRolesAll(IntStream.range(0, inserted.size())
                .mapToObj(i -> new UserRoleAssignmentDto(inserted.get(i).getUserSeq(), saveDtos.get(i).roles(), inserted.get(i).getRegisterUserSeq()))
                .toList());
    }

    /** INSERT 직후(키 채움) 신원 값 + 부여한 역할(saveDtos 와 같은 순서) — insertAll 은 엔티티를 영속성 컨텍스트에 올리지 않는다 */
    private static List<AdminUserInsertResult> toInsertResults(List<User> inserted, List<AdminUserSaveDto> saveDtos) {
        return IntStream.range(0, inserted.size()).mapToObj(i -> AdminUserInsertResult.from(inserted.get(i), saveDtos.get(i).roles())).toList();
    }

    // 일괄 등록은 CSV 파서/bulk 검증이 비밀번호 패턴을 이미 확인한 뒤라 encodeWithoutValidation을 쓴다 (단건 registerUser는 encrypt)
    private static User toUserEntity(AdminUserSaveDto c) {
        return User.createUser(c.userId(), c.tenantSeq(), Password.encodeWithoutValidation(c.encPwd(), ENCODER), SignupType.LOCAL, c.userName(), UserStatus.ACTIVE,
                c.totalQuotaByte(), c.registerUserSeq(), null, c.countryCode(), c.phoneNumber());
    }

    @Override
    /** 없거나 PENDING_DELETE 가 아니면 실패(구 JPQL 0행과 같음). 수정자·수정일은 Auditing */
    public void restoreUser(int userSeq) {
        User user = userRepository.findById(userSeq);
        DriveAssert.throwIfFalse(user != null && user.restore(), AdminUserErrorCode.RESTORE_FAILED);
    }
    
    @Override
    public void markUsersAsDeleted(UserDeleteMarkQuery query) {
        var result = userRepository.markUsersAsDeleted(query);
        if(query.getUserSeqList() != null){
            DriveAssert.throwIfTrue(result != query.getUserSeqList().size(), AdminUserErrorCode.MARK_USERS_AS_DELETED_MISMATCH);
        }
    }
}
