package com.exchange.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("within(com.exchange.controller..*) || within(com.exchange.service..*)")
    public void applicationLayer() {}

    @Before("applicationLayer()")
    public void logBefore(JoinPoint jp) {
        log.debug(">> {}.{}()", jp.getSignature().getDeclaringTypeName(), jp.getSignature().getName());
    }

    @AfterReturning(pointcut = "applicationLayer()", returning = "result")
    public void logAfter(JoinPoint jp, Object result) {
        log.debug("<< {}.{}() returned", jp.getSignature().getDeclaringTypeName(), jp.getSignature().getName());
    }

    @AfterThrowing(pointcut = "applicationLayer()", throwing = "ex")
    public void logException(JoinPoint jp, Exception ex) {
        log.warn("Exception in {}.{}(): {}", jp.getSignature().getDeclaringTypeName(),
                jp.getSignature().getName(), ex.getMessage());
    }
}
