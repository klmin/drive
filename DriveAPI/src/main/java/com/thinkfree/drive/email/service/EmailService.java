package com.thinkfree.drive.email.service;

import com.thinkfree.drive.email.dto.ShareTargetAddedEmailDto;
import com.thinkfree.drive.email.dto.UserCsvRegisteredEmailDto;
import com.thinkfree.drive.email.dto.UserPasswordResetEmailDto;
import com.thinkfree.drive.email.exception.EmailSendException;
import com.thinkfree.drive.email.model.EmailContent;
import com.thinkfree.drive.email.model.EmailTemplateContext;
import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import com.thinkfree.drive.email.template.service.EmailTemplateFindService;
import com.thinkfree.drive.email.util.MailSenderFactory;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.exception.SmtpConfigErrorCode;
import com.thinkfree.drive.smtpconfig.service.SmtpConfigFindService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserData;
import com.thinkfree.drive.user.service.UserFindService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SmtpConfigFindService smtpConfigFindService;
    private final MailSenderFactory mailSenderFactory;
    private final MessageSourceService messageSourceService;
    private final EmailTemplateFindService emailTemplateFindService;
    private final TenantFindService tenantFindService;
    private final UserFindService userFindService;

    // SMTP 설정 테스트 메일 발송 (고객사 SMTP 설정에 저장된 테스트 메일로 발송)
    public void sendTestEmail(SmtpConfigData smtpConfig) {
        JavaMailSender mailSender = mailSenderFactory.createDynamicMailSender(smtpConfig);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpConfig.getFromAddress());
            message.setTo(smtpConfig.getTestReceiverEmail());
            message.setSubject("[SMTP 연동 테스트]");
            message.setText("SMTP 설정이 정상적으로 동작합니다.");

            mailSender.send(message);

        } catch (Exception e) {
            throw ApiRuntimeException.type(SmtpConfigErrorCode.SMTP_SEND_FAILED);
        }
    }


    // 비밀번호 재설정 링크 이메일 발송
    public void sendPasswordResetEmail(UserPasswordResetEmailDto dto) {
        SmtpConfigData smtpConfig = smtpConfigFindService.findCurrentSmtpConfig(dto.getTenantSeq());
        JavaMailSender mailSender = mailSenderFactory.createDynamicMailSender(smtpConfig);

        Map<String, String> params = Map.of(
                "logoUrl",     dto.getLogoUrl(),
                "serviceName", dto.getServiceName(),
                "userName",    dto.getUserName(),
                "resetLink",   dto.getResetLink()
        );

        // subject: {0} = serviceName / body: {0} = logoUrl, {1} = userName, {2} = resetLink
        EmailTemplateContext ctx = EmailTemplateContext.create(
                params,
                List.of("serviceName"),
                List.of("logoUrl", "userName", "resetLink")
        );

        EmailContent content = buildEmailContent(EmailTemplateKey.PASSWORD_RESET, ctx, dto.getLocale());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(smtpConfig.getFromAddress());
            helper.setTo(dto.getToEmail());
            helper.setSubject(content.subject());
            helper.setText(content.body(), true); // true = HTML
            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailSendException("비밀번호 재설정 메일 발송에 실패했습니다.");
        }
    }



    public void sendShareTargetAddedEmail(ShareTargetAddedEmailDto dto) {

        SmtpConfigData smtpConfig = smtpConfigFindService.findCurrentSmtpConfig(dto.getTenantSeq());
        JavaMailSender mailSender = mailSenderFactory.createDynamicMailSender(smtpConfig);
        boolean isFile = ResourceType.FILE == dto.getResourceType();
        EmailTemplateKey templateKey = isFile ? EmailTemplateKey.SHARE_FILE : EmailTemplateKey.SHARE_FOLDER;

        // 같은 주소는 1통
        List<String> recipients = dto.getRecipientEmails().stream().distinct().toList();
        Integer actorTenantSeq = dto.getTenantSeq();

        // 같은 이메일이 여러 테넌트에 있을 수 있음(테넌트별 user_id 유니크) — 보낸 사람 테넌트에도 있으면 같은 테넌트로 본다
        Set<String> sameTenantUserIds = userFindService.findUserTenantsByUserIds(recipients).stream()
                .filter(user -> actorTenantSeq.equals(user.getTenantSeq()))
                .map(UserData::getUserId)
                .collect(Collectors.toSet());

        List<String> sameTenantEmails = recipients.stream()
                .filter(sameTenantUserIds::contains)
                .toList();
        List<String> otherTenantEmails = recipients.stream()
                .filter(email -> !sameTenantUserIds.contains(email))
                .toList();
        
        sendShareTargetAddedMail(mailSender, smtpConfig, templateKey, dto, dto.getRequestUserName(), sameTenantEmails);
        
        if (!otherTenantEmails.isEmpty()) {
            String actorTenantName = tenantFindService.findByTenantSeq(actorTenantSeq).getTenantName();
            String displayUserName = dto.getRequestUserName() + "(" + actorTenantName + ") ";
            sendShareTargetAddedMail(mailSender, smtpConfig, templateKey, dto, displayUserName, otherTenantEmails);
        }
    }

    private void sendShareTargetAddedMail(JavaMailSender mailSender, SmtpConfigData smtpConfig, EmailTemplateKey templateKey,
                                          ShareTargetAddedEmailDto dto, String displayUserName, List<String> toEmailList) {
        if (toEmailList == null || toEmailList.isEmpty()) {
            return;
        }
        String[] toEmails = toEmailList.toArray(new String[0]);

        Map<String, String> params = Map.of(
                    "resourceName",    dto.getResourceName(),
                    "requestUserName", displayUserName,
                    "requestUserId",   dto.getRequestUserId()
        );

        EmailTemplateContext emailTemplateContext = EmailTemplateContext.create(params, List.of("resourceName"), List.of("requestUserName", "requestUserId", "resourceName"));
        EmailContent content = buildEmailContent(templateKey, emailTemplateContext, dto.getLocale());

        log.info("[sendShareTargetAddedEmail] toEmails : {}", Arrays.toString(toEmails));

        try {

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpConfig.getFromAddress());
            message.setTo(toEmails);
            message.setSubject(content.subject());
            message.setText(content.body());

            mailSender.send(message);

        } catch (Exception e) {
            throw new EmailSendException("공유 대상 등록 이메일 전송에 실패했습니다.");
        }
    }
    
    public void sendUserCsvRegisteredEmail(Integer tenantSeq, List<UserCsvRegisteredEmailDto> dtoList, Locale locale) {

        if(dtoList == null || dtoList.isEmpty()){
            return;
        }

        SmtpConfigData smtpConfig = smtpConfigFindService.findCurrentSmtpConfig(tenantSeq);
        JavaMailSender mailSender = mailSenderFactory.createDynamicMailSender(smtpConfig);
        EmailTemplateKey templateKey = EmailTemplateKey.ADMIN_USER_CSV_REGISTER;
        EmailTemplateData emailTemplate = emailTemplateFindService.findByTemplateKeyAndLocale(templateKey, locale);
        
        List<SimpleMailMessage> messages = dtoList.stream()
                                                  .map(dto -> {
                                                        Map<String, String> params = Map.of(
                                                        "baseUrl",  dto.getBaseUrl(),
                                                        "userId",   dto.getRecipientEmail(),
                                                        "pw",       dto.getPw(),
                                                        "loginUrl", dto.getLoginUrl(),
                                                                "serviceName", dto.getServiceName()
                                                        );
                                                        
                                                        EmailTemplateContext ctx = EmailTemplateContext.create(
                                                            params,
                                                            List.of("serviceName"),
                                                            List.of("baseUrl", "userId", "pw", "loginUrl", "serviceName")
                                                        );
                                                        
                                                        EmailContent emailContent = renderEmailContent(emailTemplate, templateKey, ctx, locale);
                                                        return buildMailMessage(smtpConfig, dto.getRecipientEmail(), emailContent);
                                                  })
                                                  .toList();
        
        try{
            mailSender.send(messages.toArray(new SimpleMailMessage[0]));
        }catch (Exception e){
            log.error("sendUserCsvRegisteredEmail Exception ", e);
            throw new EmailSendException("csv 유저 등록 이메일 전송에 실패했습니다.");
        }
            
    }
    
    private SimpleMailMessage buildMailMessage(SmtpConfigData smtpConfig, String recipientEmail, EmailContent content) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(smtpConfig.getFromAddress());
        msg.setTo(recipientEmail);
        msg.setSubject(content.subject());
        msg.setText(content.body());
        return msg;
    }
    
    private EmailContent buildEmailContent(EmailTemplateKey templateKey, EmailTemplateContext emailTemplateContext, Locale locale) {
        EmailTemplateData emailTemplate = emailTemplateFindService.findByTemplateKeyAndLocale(templateKey, locale);
        return renderEmailContent(emailTemplate, templateKey, emailTemplateContext, locale);
    }
    
    private EmailContent renderEmailContent(EmailTemplateData emailTemplate, EmailTemplateKey templateKey, EmailTemplateContext emailTemplateContext, Locale locale){
        if (emailTemplate != null) {
            return new EmailContent(
                    emailTemplateContext.renderTemplate(emailTemplate.getSubjectTemplate()),
                    emailTemplateContext.renderTemplate(emailTemplate.getBodyTemplate())
            );
        }

        String subject = messageSourceService.getMessage(templateKey.getSubjectMessageKey(), emailTemplateContext.getSubjectArgs(), locale);

        if (templateKey == EmailTemplateKey.PASSWORD_RESET) {
            String body = loadClasspathEmailTemplate("password_reset_" + locale.getLanguage() + ".html", emailTemplateContext);
            return new EmailContent(subject, body);
        }

        return new EmailContent(
                subject,
                messageSourceService.getMessage(templateKey.getBodyMessageKey(), emailTemplateContext.getBodyArgs(), locale)
        );
    }

    private String loadClasspathEmailTemplate(String filename, EmailTemplateContext ctx) {
        String path = "email-templates/" + filename;
        try {
            byte[] bytes = new ClassPathResource(path).getInputStream().readAllBytes();
            return ctx.renderTemplate(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[loadClasspathEmailTemplate] 파일 로드 실패: {}, fallback to en", path);
            try {
                byte[] bytes = new ClassPathResource("email-templates/password_reset_en.html").getInputStream().readAllBytes();
                return ctx.renderTemplate(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                log.error("[loadClasspathEmailTemplate] en fallback도 실패", ex);
                throw new EmailSendException("이메일 템플릿 로드에 실패했습니다.");
            }
        }
    }
    

}
