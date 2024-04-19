package com.sparta.finalticket.domain.review.service;

import com.sparta.finalticket.domain.game.entity.Game;
import com.sparta.finalticket.domain.review.dto.response.ReviewResponseDto;
import com.sparta.finalticket.domain.review.entity.Review;
import com.sparta.finalticket.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisService redisService;

    public void createReview(Long reviewId, Review reviewData) {
        ReviewResponseDto reviewResponseDto = new ReviewResponseDto(reviewData);
        cacheReviewData(reviewId, reviewResponseDto);
    }

    public void updateReview(Long reviewId, Review updatedReviewData) {
        ReviewResponseDto reviewResponseDto = new ReviewResponseDto(updatedReviewData);
        cacheReviewData(reviewId, reviewResponseDto);
    }

    public void getReview(Long reviewId, Review getReviewData) {
        ReviewResponseDto reviewResponseDto = new ReviewResponseDto(getReviewData);
        cacheReviewData(reviewId, reviewResponseDto);
    }

    public void cacheReviewData(Long reviewId, ReviewResponseDto reviewData) {
        // ReviewResponseDto를 Review 객체로 변환하여 저장
        Review review = new Review();
        review.setId(reviewData.getId());
        review.setReview(reviewData.getReview());
        review.setScore(reviewData.getScore());
        review.setState(reviewData.getState());

        // userId와 gameId를 직접 설정하는 것이 아니라 User 객체와 Game 객체를 설정해야 함
        User user = new User();
        user.setId(reviewData.getUserId());
        review.setUser(user);

        Game game = new Game();
        game.setId(reviewData.getGameId());
        review.setGame(game);

        redisService.cacheReviewData("review_" + reviewId, review);
    }

    public String getCachedReviewData(Long reviewId) {
        return redisService.getValues("review_" + reviewId);
    }

    public void clearReviewCache(Long reviewId) {
        redisService.deleteValues("review_" + reviewId);
    }

    // 매개변수 없는 버전의 clearReviewCache 메서드 추가
    public void clearReviewCache() {
        // Redis에서 모든 리뷰 데이터를 삭제
        Set<String> keys = redisService.getAllKeys("review_*");
        keys.forEach(redisService::deleteValues);
    }

    public void clearGameCache(Long gameId) {
        // 해당 게임에 대한 리뷰 캐시를 모두 삭제합니다.
        // Assuming getAllKeys is an existing method in RedisService
        Set<String> keys = redisService.getAllKeys("review_" + gameId + "_*");
        keys.forEach(redisService::deleteValues);
    }

    // 모든 리뷰 데이터를 캐시에서 삭제하는 메서드
    public void clearAllReviews() {
        // Redis에서 모든 리뷰 데이터를 삭제
        Set<String> keys = redisService.getAllKeys("review_*");
        keys.forEach(key -> {
            // 키에서 리뷰 ID를 추출
            String reviewIdString = key.substring(key.lastIndexOf("_") + 1);
            Long reviewId;
            try {
                // 리뷰 ID를 Long 형으로 파싱
                reviewId = Long.parseLong(reviewIdString);
            } catch (NumberFormatException e) {
                // 리뷰 ID가 올바르지 않은 경우, 로그를 출력하고 건너뜀
                System.out.println("Invalid review ID: " + reviewIdString);
                return;
            }
            // 리뷰 데이터 삭제
            redisService.deleteValues(key);
        });
    }
}
