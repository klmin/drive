package com.thinkfree.drive.event.user.converter;

import com.thinkfree.drive.email.dto.UserCsvRegisteredEmailDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserEventConverter {
    
    UserCsvRegisteredEmailDto toAdminUserRegisteredEmailDto(String recipientEmail, String baseUrl, String pw, String loginUrl, String serviceName);
    
}
