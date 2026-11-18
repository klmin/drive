package com.thinkfree.drive.user.converter;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.query.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", imports = {Instant.class})
public interface UserConverter {
    
    default UserVisibleQuery toUserVisibleQuery(List<Integer> userSeqList){
        return UserVisibleQuery.builder().userSeqList(userSeqList).build();
    }

    UserSearchQuery toUserSearchQuery(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, Integer tenantSeq);

    UserSearchQuery toOwnerSearchQuery(String searchKeyword, RoleType roleType, List<UserStatus> userStatuses, Integer requestUserSeq);

    
    @Mapping(target = "mfaSetupDate", expression = "java(java.time.Instant.now())")
    UserMfaQuery toSetupMfaQuery(int userSeq, String mfaSecretKey);
    
    UserMfaQuery toConfirmMfaQuery(int userSeq, Instant mfaConfirmDate, Instant cutoffAt);

    UserMfaQuery toClearPendingMfaQuery(Instant cutoffAt, int updateUserSeq);

    UserTenantSearchQuery toUserTenantSearchQuery(String searchKeyword, Integer tenantSeq, List<RoleType> roleTypes, List<UserStatus> userStatuses);

}
