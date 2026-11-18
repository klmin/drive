package com.thinkfree.drive.permission.aspect;

import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermission;
import com.thinkfree.drive.permission.aspect.annotation.CheckResourcePermissions;
import com.thinkfree.drive.permission.aspect.exception.PermissionAspectErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
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
public class ResourcePermissionAspect {
    
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private final ResourcePermissionService resourcePermissionService;

    @Before("@annotation(check)")
    public void before(JoinPoint jp, CheckResourcePermission check){
        
        doPermissionCheck(jp, check);
    }
    
    @Before("@annotation(checks)")
    public void beforeMultiple(JoinPoint jp, CheckResourcePermissions checks) {
        
        for (CheckResourcePermission check : checks.value()) {
            doPermissionCheck(jp, check);
        }
    }
    
    private void doPermissionCheck(JoinPoint jp, CheckResourcePermission check){

        CustomUserDetails details = SecurityUtil.getUserDetails();
        Integer requestUserSeq = SecurityUtil.getUserSeq();
        Boolean isDelete = check.isDelete().toBoolean();

        if(requestUserSeq == null){
            throw ApiRuntimeException.type(PermissionAspectErrorCode.PERMISSION_UNAUTHENTICATED_USER);
        }
        
        PermissionAwareResource permissionAwareResource = Stream.of(jp.getArgs())
                                                                .filter(PermissionAwareResource.class::isInstance)
                                                                .map(PermissionAwareResource.class::cast)
                                                                .findFirst()
                                                                .orElse(null);
        
        Integer sharedByUserSeq;
        Long resourceSeq;
        MethodSignature ms = (MethodSignature) jp.getSignature();
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(jp.getTarget(), ms.getMethod(), jp.getArgs(), NAME_DISCOVERER);
        
        if (permissionAwareResource == null) {
            
            sharedByUserSeq = PARSER.parseExpression(check.sharedByUserSeq()).getValue(ctx, Integer.class);
            resourceSeq  = PARSER.parseExpression(check.resourceSeq()).getValue(ctx, Long.class);
            
        }else{
            
            sharedByUserSeq = permissionAwareResource.sharedByUserSeq();
            resourceSeq = permissionAwareResource.resourceSeq();
        }
        
        Integer ownerSeq = sharedByUserSeq == null ? requestUserSeq : sharedByUserSeq;

        // 보안 경계인 아스펙트가 SecurityContext 로부터 요청자(익명 여부 포함)를 구성해 전달.
        RequestActor actor = RequestActor.of(requestUserSeq, details != null && details.isAnonymous());
        resourcePermissionService.checkPermission(check.value(), resourceSeq, ownerSeq, actor, isDelete);

    }
    
}
