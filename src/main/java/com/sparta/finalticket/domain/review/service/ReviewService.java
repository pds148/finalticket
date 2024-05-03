package com.sparta.finalticket.domain.review.service;

import com.sparta.finalticket.domain.game.entity.Game;
import com.sparta.finalticket.domain.game.repository.GameRepository;
import com.sparta.finalticket.domain.review.dto.request.ReviewRequestDto;
import com.sparta.finalticket.domain.review.dto.request.ReviewUpdateRequestDto;
import com.sparta.finalticket.domain.review.dto.response.ReviewCountAndAvgResponseDto;
import com.sparta.finalticket.domain.review.dto.response.ReviewGameListResponseDto;
import com.sparta.finalticket.domain.review.dto.response.ReviewResponseDto;
import com.sparta.finalticket.domain.review.dto.response.ReviewUpdateResponseDto;
import com.sparta.finalticket.domain.review.entity.Review;
import com.sparta.finalticket.domain.review.entity.ReviewSortType;
import com.sparta.finalticket.domain.review.repository.ReviewRepository;
import com.sparta.finalticket.domain.user.entity.User;
import com.sparta.finalticket.global.exception.review.GameIdRequiredException;
import com.sparta.finalticket.global.exception.review.ReviewGameNotFoundException;
import com.sparta.finalticket.global.exception.review.ReviewNotFoundException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final GameRepository gameRepository;
    private final RedisCacheService redisCacheService;
    private final ReviewStatisticService reviewStatisticService;
    private final DistributedReviewService distributedReviewService;
    private final RedisReviewService redisReviewService;

    @Transactional
    public ReviewResponseDto createReview(Long gameId, ReviewRequestDto requestDto, User user) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                Review review = createReviewFromRequest(gameId, requestDto);
                review.setUser(user);
                Review createdReview = reviewRepository.save(review);
                createCacheAndRedis(gameId, createdReview);
                reviewStatisticService.updateReviewStatistics(gameId);
                return new ReviewResponseDto(createdReview);
            } else {
                throw new RuntimeException("리뷰 생성을 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("리뷰 생성을 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewGameListResponseDto> getReviewsByGameId(Long gameId) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                List<Review> reviews = reviewRepository.findByGameId(gameId);
                return reviews.stream()
                        .map(ReviewGameListResponseDto::new)
                        .toList();
            } else {
                throw new ReviewNotFoundException("게임 ID에 대한 리뷰 조회를 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("게임 ID에 대한 리뷰 조회를 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional(readOnly = true)
    public ReviewCountAndAvgResponseDto getReviewsCountAndAvgByGameId(Long gameId) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                Double avg = Math.round(redisReviewService.getAverageReviewScore(gameId) * 10) / 10.0;
                Long count = redisReviewService.getTotalReviewCount(gameId);
                return new ReviewCountAndAvgResponseDto(avg, count);
            } else {
                throw new ReviewNotFoundException("경기 ID에 대한 리뷰 조회를 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("경기 ID에 대한 리뷰 조회를 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional(readOnly = true)
    public ReviewResponseDto getReviewByGameId(Long gameId, Long reviewId) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                Review review = getReviewByIdAndGameId(gameId, reviewId);
                Game game = getGameById(gameId);
                review.setGame(game);
                getCacheAndRedis(reviewId, review);
                return new ReviewResponseDto(review);
            } else {
                throw new ReviewNotFoundException("경기 ID에 대한 리뷰 조회를 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("경기 ID에 대한 리뷰 조회를 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional
    public ReviewUpdateResponseDto updateReview(Long gameId, Long reviewId, ReviewUpdateRequestDto requestDto, User user) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                Review review = updateReviewFromRequest(gameId, reviewId, requestDto);
                review.setUser(user);
                Review updatedReview = reviewRepository.save(review);
                updateCacheAndRedis(reviewId, updatedReview);
                reviewStatisticService.updateReviewStatistics(gameId);
                return new ReviewUpdateResponseDto(updatedReview);
            } else {
                throw new ReviewNotFoundException("리뷰 업데이트를 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("리뷰 업데이트를 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional
    public void deleteReview(Long gameId, Long reviewId, User user) {
        RLock lock = distributedReviewService.getLock(gameId);
        try {
            if (distributedReviewService.tryLock(lock, 1000, 5000)) {
                Review review = deleteReviewById(reviewId);
                Game game = new Game();
                review.setGame(game);
                reviewRepository.delete(review);
                deleteCacheAndRedis(reviewId);
                reviewStatisticService.updateReviewStatistics(gameId);
            } else {
                throw new ReviewNotFoundException("리뷰 삭제를 위한 락 획득에 실패했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewNotFoundException("리뷰 삭제를 위해 락을 획득하는 도중에 중단되었습니다.");
        } finally {
            distributedReviewService.unlock(lock);
        }
    }

    @Transactional
    public ReviewResponseDto likeReview(Long reviewId) {
        Review review = getReviewById(reviewId);
        Long likeCount = review.getLikeCount();
        if (likeCount == null) {
            review.setLikeCount(1L);
        } else {
            review.setLikeCount(likeCount + 1);
        }
        Review updatedReview = reviewRepository.save(review);
        return new ReviewResponseDto(updatedReview);
    }

    @Transactional
    public ReviewResponseDto dislikeReview(Long reviewId) {
        Review review = getReviewById(reviewId);
        review.setDislikeCount(review.getDislikeCount() + 1);
        Review updatedReview = reviewRepository.save(review);
        return new ReviewResponseDto(updatedReview);
    }

    @Transactional
    public void reportReview(Long gameId, Long reviewId, User user) {
        Review review = getReviewById(reviewId);
        review.setReported(true);
        Long reportCount = review.getReportCount();
        review.setReportCount(reportCount != null ? reportCount + 1 : 1);
        reviewRepository.save(review);
    }

    public List<ReviewResponseDto> filterReviewsByCriteria(Long gameId, Long minScore, Long maxScore) {
        List<Review> reviews = reviewRepository.findByGameId(gameId);

        // 평점 기준으로 필터링 및 정렬
        List<Review> filteredAndSortedReviews = reviews.stream()
                .filter(review -> (minScore == null || review.getScore() >= minScore) &&
                        (maxScore == null || review.getScore() <= maxScore))
                .sorted(Comparator.comparingLong(Review::getScore)) // 평점에 따라 오름차순 정렬
                .toList();

        return filteredAndSortedReviews.stream()
                .map(ReviewResponseDto::new)
                .toList();
    }

    @Transactional
    public ReviewResponseDto recommendReview(Long reviewId) {
        Review review = getReviewById(reviewId);
        Long recommendationCount = review.getRecommendationCount();
        if (recommendationCount == null) {
            review.setRecommendationCount(1L);
        } else {
            review.setRecommendationCount(recommendationCount + 1);
        }
        Review updatedReview = reviewRepository.save(review);
        return new ReviewResponseDto(updatedReview);
    }

    // 모든 게임의 리뷰 통계를 업데이트하는 메서드
    @Transactional
    public void updateReviewStatisticsForAllGames() {
        List<Game> games = gameRepository.findAll();
        games.forEach(game -> reviewStatisticService.updateReviewStatistics(game.getId()));
    }

    // 모든 리뷰 데이터를 조회하는 메서드
    @Transactional(readOnly = true)
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDto> filterReviewsByCriteria(Long gameId, Long minScore, Long maxScore, ReviewSortType sortType) {
        List<Review> reviews = reviewRepository.findByGameId(gameId);

        // 평점 기준으로 필터링
        List<Review> filteredReviews = reviews.stream()
                .filter(review -> (minScore == null || review.getScore() >= minScore) &&
                        (maxScore == null || review.getScore() <= maxScore))
                .toList();

        // 선택된 정렬 방법에 해당하는 Comparator 인스턴스를 가져옴
        Comparator<Review> comparator = sortType.getComparator();

        // 정렬
        List<Review> sortedReviews = filteredReviews.stream()
                .sorted(comparator)
                .toList();

        // DTO로 변환하여 반환
        return sortedReviews.stream()
                .map(ReviewResponseDto::new)
                .toList();
    }

    private Review createReviewFromRequest(Long gameId, ReviewRequestDto requestDto) {
        if (gameId == null) {
            throw new GameIdRequiredException("게임 ID가 필요합니다.");
        }

        Review review = new Review();
        review.setReview(requestDto.getReview());
        review.setScore(requestDto.getScore());
        review.setState(true);
        Game game = getGameById(gameId);
        review.setGame(game);
        return review;
    }

    private Review updateReviewFromRequest(Long gameId, Long reviewId, ReviewUpdateRequestDto requestDto) {
        Review review = getReviewById(reviewId);
        Game game = getGameById(gameId);
        review.setGame(game);
        review.setReview(requestDto.getReview());
        review.setScore(requestDto.getScore());
        review.setState(true);
        return review;
    }

    public void createCacheAndRedis(Long gameId, Review review) {
        redisCacheService.createReview(review.getId(), review);
    }

    public void getCacheAndRedis(Long reviewId, Review review) {
        redisCacheService.getReview(reviewId, review);
    }

    public void updateCacheAndRedis(Long reviewId, Review review) {
        redisCacheService.updateReview(reviewId, review);
    }

    public void deleteCacheAndRedis(Long reviewId) {
        redisCacheService.clearReviewCache(reviewId);
    }

    private Game getGameById(Long gameId) {
        return gameRepository.findById(gameId)
            .orElseThrow(() -> new ReviewGameNotFoundException("경기를 찾을 수 없습니다."));
    }

    private Review getReviewByIdAndGameId(Long gameId, Long reviewId) {
        return (Review) reviewRepository.findReviewByGameIdAndReviewId(gameId, reviewId)
            .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다."));
    }

    private Review getReviewById(Long reviewId) {
        return reviewRepository.findReviewByIdAndStateTrue(reviewId)
            .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다."));
    }

    private Review deleteReviewById(Long reviewId) {
        return reviewRepository.findReviewByIdAndDeleteId(reviewId)
            .orElseThrow(() -> new ReviewNotFoundException("해당 리뷰가 존재하지 않습니다."));
    }

    public List<ReviewResponseDto> getUserReviewList(User user) {
        return reviewRepository.getUserReviewList(user);
    }
}
