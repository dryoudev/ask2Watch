package com.ask2watch.service.recommendation;

import com.ask2watch.dto.recommendation.RecommendationDto;

import java.util.List;

public interface RecommendationProvider {

    List<RecommendationDto> getRecommendations(Long userId, int limit);
}
