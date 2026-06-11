package com.exchange.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PerformanceMonitorAspect {

    private static final long SLOW_THRESHOLD_MS = 500;

    @Around("within(com.exchange.service..*)")
    public Object measureExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > SLOW_THRESHOLD_MS) {
                log.warn("SLOW execution [{} ms] — {}.{}()", elapsed,
                        pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName());
            } else {
                log.debug("Execution [{} ms] — {}.{}()", elapsed,
                        pjp.getSignature().getDeclaringTypeName(), pjp.getSignature().getName());
            }
        }
    }
}
