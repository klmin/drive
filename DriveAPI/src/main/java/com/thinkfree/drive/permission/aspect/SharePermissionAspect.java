package com.thinkfree.drive.permission.aspect;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.permission.aspect.annotation.CheckSharePermission;
import com.thinkfree.drive.permission.aspect.exception.PermissionAspectErrorCode;
import com.thinkfree.drive.permission.service.SharePermissionService;
import com.thinkfree.drive.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Aspect
@Component
@RequiredArgsConstructor
public class SharePermissionAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private final SharePermissionService sharePermissionService;

    @Before("@annotation(check)")
    public void before(JoinPoint jp, CheckSharePermission check){
        doPermissionCheck(jp, check);
    }

    private void doPermissionCheck(JoinPoint jp, CheckSharePermission checkSharePermission){
        Integer requestUserSeq = SecurityUtil.getUserSeq();

        if (requestUserSeq == null){
            throw ApiRuntimeException.type(PermissionAspectErrorCode.PERMISSION_UNAUTHENTICATED_USER);
        }

        PermissionAwareShare permissionAwareShare = Stream.of(jp.getArgs())
                                                                .filter(PermissionAwareShare.class::isInstance)
                                                                .map(PermissionAwareShare.class::cast)
                                                                .findFirst()
                                                                .orElse(null);

        Integer sharedByUserSeq;
        Long shareSeq;
        MethodSignature ms = (MethodSignature) jp.getSignature();
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(jp.getTarget(), ms.getMethod(), jp.getArgs(), NAME_DISCOVERER);

        if (permissionAwareShare == null) {
            sharedByUserSeq = PARSER.parseExpression(checkSharePermission.sharedByUserSeq()).getValue(ctx, Integer.class);
            shareSeq  = PARSER.parseExpression(checkSharePermission.shareSeq()).getValue(ctx, Long.class);
        } else {
            sharedByUserSeq = permissionAwareShare.sharedByUserSeq();
            shareSeq = permissionAwareShare.shareSeq();
        }

        boolean isCheckShareSeq = checkSharePermission.isCheckShareSeq();
        if (!isCheckShareSeq && shareSeq == null){
            return;
        }

        Integer ownerSeq = sharedByUserSeq == null ? requestUserSeq : sharedByUserSeq;
        sharePermissionService.authorize(checkSharePermission.value(), shareSeq, ownerSeq, requestUserSeq);
    }

}
