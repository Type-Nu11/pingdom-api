package com.typenull.pingdom.place.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationGraphAffinityService {

    private static final double RESTART_PROBABILITY = 0.35d;
    private static final int ITERATION_COUNT = 12;
    private static final int MAX_NEIGHBOR_COUNT = 8;
    private static final double MIN_EDGE_SIMILARITY = 0.05d;

    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    public Map<Long, Double> score(
            Collection<Long> candidatePlaceIds,
            Map<Long, Double> seedWeights,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        Map<Long, Double> emptyScores = initializeEmptyScores(candidatePlaceIds);
        if (candidatePlaceIds.isEmpty() || seedWeights.isEmpty()) {
            return emptyScores;
        }

        Set<Long> graphNodeIds = new LinkedHashSet<>(seedWeights.keySet());
        graphNodeIds.addAll(candidatePlaceIds);

        Map<Long, Double> prior = buildPrior(graphNodeIds, seedWeights);
        Map<Long, List<GraphEdge>> transitionGraph = buildTransitionGraph(graphNodeIds, similarityContext);
        Map<Long, Double> currentScores = new HashMap<>(prior);

        for (Long nodeId : graphNodeIds) {
            currentScores.putIfAbsent(nodeId, 0d);
        }

        for (int iteration = 0; iteration < ITERATION_COUNT; iteration++) {
            Map<Long, Double> nextScores = initializeRestartScores(graphNodeIds, prior);

            for (Long fromNodeId : graphNodeIds) {
                double currentScore = currentScores.getOrDefault(fromNodeId, 0d);
                List<GraphEdge> edges = transitionGraph.getOrDefault(fromNodeId, List.of());

                for (GraphEdge edge : edges) {
                    nextScores.merge(
                            edge.targetPlaceId(),
                            (1d - RESTART_PROBABILITY) * currentScore * edge.weight(),
                            Double::sum
                    );
                }
            }

            currentScores = nextScores;
        }

        return normalizeCandidateScores(candidatePlaceIds, currentScores);
    }

    private Map<Long, Double> initializeEmptyScores(Collection<Long> candidatePlaceIds) {
        Map<Long, Double> emptyScores = new HashMap<>();
        for (Long candidatePlaceId : candidatePlaceIds) {
            emptyScores.put(candidatePlaceId, 0d);
        }
        return emptyScores;
    }

    private Map<Long, Double> buildPrior(Set<Long> graphNodeIds, Map<Long, Double> seedWeights) {
        Map<Long, Double> prior = new HashMap<>();
        double totalWeight = seedWeights.values().stream()
                .filter(weight -> weight > 0d)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalWeight <= 0d) {
            return prior;
        }

        for (Long nodeId : graphNodeIds) {
            double weight = seedWeights.getOrDefault(nodeId, 0d);
            prior.put(nodeId, weight / totalWeight);
        }

        return prior;
    }

    private Map<Long, List<GraphEdge>> buildTransitionGraph(
            Set<Long> graphNodeIds,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        Map<Long, List<GraphEdge>> transitionGraph = new HashMap<>();

        for (Long fromNodeId : graphNodeIds) {
            List<GraphEdge> edges = graphNodeIds.stream()
                    .filter(targetNodeId -> !fromNodeId.equals(targetNodeId))
                    .map(targetNodeId -> new GraphEdge(
                            targetNodeId,
                            placeRecommendationSimilarityService.similarity(fromNodeId, targetNodeId, similarityContext)
                    ))
                    .filter(edge -> edge.weight() >= MIN_EDGE_SIMILARITY)
                    .sorted(Comparator.comparingDouble(GraphEdge::weight).reversed())
                    .limit(MAX_NEIGHBOR_COUNT)
                    .toList();

            if (edges.isEmpty()) {
                transitionGraph.put(fromNodeId, List.of(new GraphEdge(fromNodeId, 1d)));
                continue;
            }

            double totalWeight = edges.stream()
                    .mapToDouble(GraphEdge::weight)
                    .sum();

            List<GraphEdge> normalizedEdges = new ArrayList<>(edges.size());
            for (GraphEdge edge : edges) {
                normalizedEdges.add(new GraphEdge(edge.targetPlaceId(), edge.weight() / totalWeight));
            }
            transitionGraph.put(fromNodeId, List.copyOf(normalizedEdges));
        }

        return transitionGraph;
    }

    private Map<Long, Double> initializeRestartScores(Set<Long> graphNodeIds, Map<Long, Double> prior) {
        Map<Long, Double> restartScores = new HashMap<>();

        for (Long nodeId : graphNodeIds) {
            restartScores.put(nodeId, RESTART_PROBABILITY * prior.getOrDefault(nodeId, 0d));
        }

        return restartScores;
    }

    private Map<Long, Double> normalizeCandidateScores(
            Collection<Long> candidatePlaceIds,
            Map<Long, Double> scores
    ) {
        double maxScore = candidatePlaceIds.stream()
                .mapToDouble(candidatePlaceId -> scores.getOrDefault(candidatePlaceId, 0d))
                .max()
                .orElse(0d);

        Map<Long, Double> normalizedScores = new HashMap<>();
        for (Long candidatePlaceId : candidatePlaceIds) {
            double score = scores.getOrDefault(candidatePlaceId, 0d);
            normalizedScores.put(candidatePlaceId, maxScore > 0d ? score / maxScore : 0d);
        }

        return normalizedScores;
    }

    private record GraphEdge(Long targetPlaceId, double weight) {
    }
}
