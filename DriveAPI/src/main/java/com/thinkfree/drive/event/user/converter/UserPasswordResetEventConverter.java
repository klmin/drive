package com.thinkfree.drive.event.user.converter;

import com.thinkfree.drive.email.dto.UserPasswordResetEmailDto;
import com.thinkfree.drive.event.user.UserPasswordResetRequestedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Locale;

@Mapper(componentModel = "spring")
public interface UserPasswordResetEventConverter {

    @Mapping(target = "tenantSeq",   source = "event.tenantSeq")
    @Mapping(target = "toEmail",     source = "event.toEmail")
    @Mapping(target = "userName",    source = "event.userName")
    @Mapping(target = "serviceName", source = "event.serviceName")
    @Mapping(target = "resetLink",   expression = "java(linkBaseUrl + \"/reset-password?t=\" + event.token())")
    @Mapping(target = "locale",      source = "locale")
    @Mapping(target = "logoUrl",     expression = "java(linkBaseUrl + \"/branding/images/email-logo\")")
    UserPasswordResetEmailDto toUserPasswordResetEmailDto(UserPasswordResetRequestedEvent event, String linkBaseUrl, Locale locale);
}