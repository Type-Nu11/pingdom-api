package com.typenull.pingdom.place.application.service.recommendation.similarity;

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

/**
 * 개인 반응 시드와 후보를 최대 64개 노드로 묶어 유사도 그래프의 개인 친화도를 계산합니다.
 * 시드를 먼저 넣으므로 시드 수에 따라 일부 후보는 그래프에 포함되지 않아 점수가 0으로 남습니다.
 * 재시작 확률 0.35로 12번 전파한 점수를 후보 최대값으로 나누어 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationGraphAffinityService {

    private static final double RESTART_PROBABILITY = 0.35d;
    private static final int ITERATION_COUNT = 12;
    private static final int MAX_NEIGHBOR_COUNT = 8;
    private static final int MAX_GRAPH_NODE_COUNT = 64;
    private static final double MIN_EDGE_SIMILARITY = 0.05d;

    private final PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    /**
     * 양수 seed 가중치가 높은 노드를 먼저 선택해 최대 64개 노드의 유사도 그래프에서 개인화 점수를 전파합니다.
     * 12회 재시작 전파 후 후보 최대값으로 정규화하며 seed나 후보가 없으면 모든 후보에 0을 반환합니다.
     * 선택 한도 밖 후보도 응답에는 남지만 그래프 전파를 받지 않아 0점이 됩니다.
     */
    public Map<Long, Double> score(
            Collection<Long> candidatePlaceIds,
            Map<Long, Double> seedWeights,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        Map<Long, Double> emptyScores = initializeEmptyScores(candidatePlaceIds);
        if (candidatePlaceIds.isEmpty() || seedWeights.isEmpty()) {
            return emptyScores;
        }

        Set<Long> graphNodeIds = new LinkedHashSet<>();
        seedWeights.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0d)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_GRAPH_NODE_COUNT)
                .forEach(entry -> graphNodeIds.add(entry.getKey()));
        candidatePlaceIds.stream()
                .filter(candidateId -> graphNodeIds.size() < MAX_GRAPH_NODE_COUNT)
                .forEach(graphNodeIds::add);

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

    /**
     * 유사도 0.05 이상인 이웃 중 노드별 상위 8개를 확률 합 1로 정규화합니다.
     * 이웃이 없으면 자기 자신으로 돌아가는 간선을 두어 전파 점수가 사라지지 않게 합니다.
     */
    private Map<Long, List<GraphEdge>> buildTransitionGraph(
            Set<Long> graphNodeIds,
            PlaceRecommendationSimilarityService.SimilarityContext similarityContext
    ) {
        Map<Long, List<GraphEdge>> transitionGraph = new HashMap<>();
        Map<Long, List<GraphEdge>> edgesByNode = new HashMap<>();
        List<Long> nodeIds = new ArrayList<>(graphNodeIds);

        for (Long nodeId : nodeIds) {
            edgesByNode.put(nodeId, new ArrayList<>());
        }

        for (int leftIndex = 0; leftIndex < nodeIds.size(); leftIndex++) {
            Long leftNodeId = nodeIds.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < nodeIds.size(); rightIndex++) {
                Long rightNodeId = nodeIds.get(rightIndex);
                double similarity = placeRecommendationSimilarityService.similarity(
                        leftNodeId,
                        rightNodeId,
                        similarityContext
                );
                if (similarity < MIN_EDGE_SIMILARITY) {
                    continue;
                }
                edgesByNode.get(leftNodeId).add(new GraphEdge(rightNodeId, similarity));
                edgesByNode.get(rightNodeId).add(new GraphEdge(leftNodeId, similarity));
            }
        }

        for (Long fromNodeId : nodeIds) {
            List<GraphEdge> edges = edgesByNode.getOrDefault(fromNodeId, List.of()).stream()
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
