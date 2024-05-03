package com.sparta.finalticket.domain.review.aspect;

import com.sparta.finalticket.domain.review.service.RedisReviewService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedisReviewService redisReviewService;
    private final RedissonClient redissonClient;

    @Pointcut("execution(* com.sparta.finalticket.domain.review.service.ReviewService.createReview(..)) " +
            "|| execution(* com.sparta.finalticket.domain.review.service.ReviewService.getReviewByGameId(..)) " +
            "|| execution(* com.sparta.finalticket.domain.review.service.ReviewService.updateReview(..)) " +
            "|| execution(* com.sparta.finalticket.domain.review.service.ReviewService.deleteReview(..))")
    public void reviewServiceMethods() {
    }

    @Around("reviewServiceMethods()")
    public Object applyDistributedLock(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result;
        Object[] args = joinPoint.getArgs();

        Long gameId = Arrays.stream(args)
                .filter(arg -> arg instanceof Long)
                .map(arg -> (Long) arg)
                .findFirst()
                .orElse(null);

        if (gameId != null) {
            RLock lock = redissonClient.getLock("reviewLock:" + gameId);
            try {
                lock.lock();
                result = joinPoint.proceed();
            } finally {
                lock.unlock();
            }

            // 리뷰 관련 데이터를 Redis에 업데이트
            redisReviewService.setTotalReviewCount(gameId, redisReviewService.getTotalReviewCount(gameId));
            redisReviewService.setAverageReviewScore(gameId, redisReviewService.getAverageReviewScore(gameId));

            return result;
        } else {
            return joinPoint.proceed();
        }
    }
}
