

package com.nageoffer.ai.ragent.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.dto.IntentCandidate;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.INTENT_MIN_SCORE;
import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.MAX_INTENT_COUNT;
import static com.nageoffer.ai.ragent.rag.enums.IntentKind.SYSTEM;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentResolver {

    @Qualifier("defaultIntentClassifier")
    private final IntentClassifier intentClassifier;
    @Qualifier("intentClassifyThreadPoolExecutor")
    private final Executor intentClassifyExecutor;

    @RagTraceNode(name = "intent-resolve", type = "INTENT")
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        List<String> subQuestions = CollUtil.isNotEmpty(rewriteResult.subQuestions())
                ? rewriteResult.subQuestions()
                : List.of(rewriteResult.rewrittenQuestion());
        log.info("[RAG对话链路][意图识别] 开始解析意图，rewrittenQuestion：{}，subQuestionCount：{}，threshold：{}，maxIntentCount：{}",
                StrUtil.maxLength(rewriteResult.rewrittenQuestion(), 120),
                subQuestions.size(),
                INTENT_MIN_SCORE,
                MAX_INTENT_COUNT);
        for (int i = 0; i < subQuestions.size(); i++) {
            log.info("[RAG对话链路][意图识别] 子问题准备分类，index：{}，question：{}",
                    i, StrUtil.maxLength(subQuestions.get(i), 120));
        }
        List<CompletableFuture<SubQuestionIntent>> tasks = subQuestions.stream()
                .map(q -> CompletableFuture.supplyAsync(
                        () -> new SubQuestionIntent(q, classifyIntents(q)),
                        intentClassifyExecutor
                ))
                .toList();
        List<SubQuestionIntent> subIntents = tasks.stream()
                .map(CompletableFuture::join)
                .toList();
        log.info("[RAG对话链路][意图识别] 子问题分类全部完成，subIntentCount：{}，totalRetainedBeforeCap：{}",
                subIntents.size(),
                subIntents.stream().mapToInt(si -> si.nodeScores() == null ? 0 : si.nodeScores().size()).sum());
        List<SubQuestionIntent> capped = capTotalIntents(subIntents);
        log.info("[RAG对话链路][意图识别] 意图数量裁剪完成，totalRetainedAfterCap：{}，summary：{}",
                capped.stream().mapToInt(si -> si.nodeScores() == null ? 0 : si.nodeScores().size()).sum(),
                summarizeSubIntents(capped));
        return capped;
    }

    public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
        log.info("[RAG对话链路][意图识别] 开始合并子问题意图分组，subIntentCount：{}",
                subIntents == null ? 0 : subIntents.size());
        List<NodeScore> mcpIntents = new ArrayList<>();
        List<NodeScore> kbIntents = new ArrayList<>();
        for (SubQuestionIntent si : subIntents) {
            List<NodeScore> currentMcp = filterMcpIntents(si.nodeScores());
            List<NodeScore> currentKb = filterKbIntents(si.nodeScores());
            log.info("[RAG对话链路][意图识别] 子问题分组结果，question：{}，kbCount：{}，mcpCount：{}，rawCount：{}",
                    StrUtil.maxLength(si.subQuestion(), 120),
                    currentKb.size(),
                    currentMcp.size(),
                    si.nodeScores() == null ? 0 : si.nodeScores().size());
            mcpIntents.addAll(currentMcp);
            kbIntents.addAll(currentKb);
        }
        log.info("[RAG对话链路][意图识别] 意图分组合并完成，kbIntentSummary：{}，mcpIntentSummary：{}",
                summarizeNodeScores(kbIntents), summarizeNodeScores(mcpIntents));
        return new IntentGroup(mcpIntents, kbIntents);
    }

    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return nodeScores.size() == 1
                && nodeScores.get(0).getNode() != null
                && nodeScores.get(0).getNode().getKind() == SYSTEM;
    }

    private List<NodeScore> classifyIntents(String question) {
        log.info("[RAG对话链路][意图识别] 调用意图分类器，question：{}",
                StrUtil.maxLength(question, 120));
        List<NodeScore> scores = intentClassifier.classifyTargets(question);
        log.info("[RAG对话链路][意图识别] 分类器返回原始候选，question：{}，rawCount：{}，rawTop：{}",
                StrUtil.maxLength(question, 120),
                scores == null ? 0 : scores.size(),
                summarizeNodeScores(scores));
        List<NodeScore> retained = scores.stream()
                .filter(ns -> ns.getScore() >= INTENT_MIN_SCORE)
                .limit(MAX_INTENT_COUNT)
                .toList();
        log.info("[RAG对话链路][意图识别] 阈值过滤后保留候选，question：{}，retainedCount：{}，retained：{}",
                StrUtil.maxLength(question, 120), retained.size(), summarizeNodeScores(retained));
        return retained;
    }

    private List<NodeScore> filterMcpIntents(List<NodeScore> nodeScores) {
        return nodeScores.stream()
                .filter(ns -> ns.getNode() != null && ns.getNode().getKind() == IntentKind.MCP)
                .filter(ns -> StrUtil.isNotBlank(ns.getNode().getMcpToolId()))
                .toList();
    }

    private List<NodeScore> filterKbIntents(List<NodeScore> nodeScores) {
        return nodeScores.stream()
                .filter(ns -> {
                    IntentNode node = ns.getNode();
                    if (node == null) {
                        return false;
                    }
                    return node.getKind() == null || node.getKind() == IntentKind.KB;
                })
                .toList();
    }

    /**
     * 限制总意图数量不超过 MAX_INTENT_COUNT
     * <p>
     * 策略：
     * 1. 如果总数未超限，直接返回
     * 2. 如果超限，每个子问题至少保留 1 个最高分意图
     * 3. 剩余配额按分数从高到低分配给其他意图
     */
    private List<SubQuestionIntent> capTotalIntents(List<SubQuestionIntent> subIntents) {
        int totalIntents = subIntents.stream()
                .mapToInt(si -> si.nodeScores().size())
                .sum();

        log.info("[RAG对话链路][意图识别] 检查总意图数量，totalIntents：{}，maxIntentCount：{}",
                totalIntents, MAX_INTENT_COUNT);
        // 未超限，直接返回
        if (totalIntents <= MAX_INTENT_COUNT) {
            log.info("[RAG对话链路][意图识别] 总意图数量未超限，无需裁剪");
            return subIntents;
        }

        log.info("[RAG对话链路][意图识别] 总意图数量超限，开始裁剪，裁剪策略：每个子问题保留最高分，再按全局分数补齐剩余配额");
        // 步骤1：收集所有意图，按子问题索引分组
        List<IntentCandidate> allCandidates = collectAllCandidates(subIntents);

        // 步骤2：每个子问题保留最高分意图
        List<IntentCandidate> guaranteedIntents = selectTopIntentPerSubQuestion(allCandidates, subIntents.size());

        // 步骤3：计算剩余配额
        int remaining = MAX_INTENT_COUNT - guaranteedIntents.size();

        // 步骤4：从剩余候选中按分数选择
        List<IntentCandidate> additionalIntents = selectAdditionalIntents(allCandidates, guaranteedIntents, remaining);

        log.info("[RAG对话链路][意图识别] 裁剪选择完成，allCandidateCount：{}，guaranteedCount：{}，remainingQuota：{}，additionalCount：{}",
                allCandidates.size(), guaranteedIntents.size(), remaining, additionalIntents.size());
        // 步骤5：合并并重建结果
        return rebuildSubIntents(subIntents, guaranteedIntents, additionalIntents);
    }

    /**
     * 收集所有意图候选，标记所属子问题索引
     */
    private List<IntentCandidate> collectAllCandidates(List<SubQuestionIntent> subIntents) {
        List<IntentCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < subIntents.size(); i++) {
            List<NodeScore> nodeScores = subIntents.get(i).nodeScores();
            if (CollUtil.isEmpty(nodeScores)) {
                continue;
            }
            for (NodeScore ns : nodeScores) {
                candidates.add(new IntentCandidate(i, ns));
            }
        }
        // 按分数降序排序
        candidates.sort((a, b) -> Double.compare(b.nodeScore().getScore(), a.nodeScore().getScore()));
        log.info("[RAG对话链路][意图识别] 已收集并排序全部候选，candidateCount：{}，topCandidates：{}",
                candidates.size(), summarizeCandidates(candidates));
        return candidates;
    }

    /**
     * 每个子问题选择最高分意图（保底策略）
     */
    private List<IntentCandidate> selectTopIntentPerSubQuestion(List<IntentCandidate> allCandidates, int subQuestionCount) {
        List<IntentCandidate> topIntents = new ArrayList<>();
        boolean[] selected = new boolean[subQuestionCount];

        for (IntentCandidate candidate : allCandidates) {
            int index = candidate.subQuestionIndex();
            if (!selected[index]) {
                topIntents.add(candidate);
                selected[index] = true;
            }
            // 所有子问题都有了保底意图，提前退出
            if (topIntents.size() == subQuestionCount) {
                break;
            }
        }
        return topIntents;
    }

    /**
     * 从剩余候选中选择额外意图
     */
    private List<IntentCandidate> selectAdditionalIntents(List<IntentCandidate> allCandidates,
                                                          List<IntentCandidate> guaranteedIntents,
                                                          int remaining) {
        if (remaining <= 0) {
            return List.of();
        }

        List<IntentCandidate> additional = new ArrayList<>();
        for (IntentCandidate candidate : allCandidates) {
            // 跳过已经被选为保底的意图
            if (guaranteedIntents.contains(candidate)) {
                continue;
            }
            additional.add(candidate);
            if (additional.size() >= remaining) {
                break;
            }
        }
        return additional;
    }

    /**
     * 根据选中的意图重建 SubQuestionIntent 列表
     */
    private List<SubQuestionIntent> rebuildSubIntents(List<SubQuestionIntent> originalSubIntents,
                                                      List<IntentCandidate> guaranteedIntents,
                                                      List<IntentCandidate> additionalIntents) {
        // 合并所有选中的意图
        List<IntentCandidate> allSelected = new ArrayList<>(guaranteedIntents);
        allSelected.addAll(additionalIntents);

        // 按子问题索引分组
        Map<Integer, List<NodeScore>> groupedByIndex = new ConcurrentHashMap<>();
        for (IntentCandidate candidate : allSelected) {
            groupedByIndex.computeIfAbsent(candidate.subQuestionIndex(), k -> new ArrayList<>())
                    .add(candidate.nodeScore());
        }

        // 重建结果
        List<SubQuestionIntent> result = new ArrayList<>();
        for (int i = 0; i < originalSubIntents.size(); i++) {
            SubQuestionIntent original = originalSubIntents.get(i);
            List<NodeScore> retained = groupedByIndex.getOrDefault(i, List.of());
            result.add(new SubQuestionIntent(original.subQuestion(), retained));
        }
        log.info("[RAG对话链路][意图识别] 裁剪后重建子问题意图完成，summary：{}", summarizeSubIntents(result));
        return result;
    }

    private String summarizeSubIntents(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return "[]";
        }
        return subIntents.stream()
                .map(si -> "{question=\"" + StrUtil.maxLength(si.subQuestion(), 50)
                        + "\", intents=" + summarizeNodeScores(si.nodeScores()) + "}")
                .toList()
                .toString();
    }

    private String summarizeNodeScores(List<NodeScore> nodeScores) {
        if (CollUtil.isEmpty(nodeScores)) {
            return "[]";
        }
        return nodeScores.stream()
                .limit(8)
                .map(this::summarizeNodeScore)
                .toList()
                .toString();
    }

    private String summarizeCandidates(List<IntentCandidate> candidates) {
        if (CollUtil.isEmpty(candidates)) {
            return "[]";
        }
        return candidates.stream()
                .limit(8)
                .map(candidate -> "{subQuestionIndex=" + candidate.subQuestionIndex()
                        + ", intent=" + summarizeNodeScore(candidate.nodeScore()) + "}")
                .toList()
                .toString();
    }

    private String summarizeNodeScore(NodeScore nodeScore) {
        if (nodeScore == null) {
            return "null";
        }
        IntentNode node = nodeScore.getNode();
        if (node == null) {
            return "{node=null, score=" + nodeScore.getScore() + "}";
        }
        return "{id=" + node.getId()
                + ", name=" + node.getName()
                + ", kind=" + node.getKind()
                + ", score=" + String.format("%.4f", nodeScore.getScore())
                + ", path=" + StrUtil.maxLength(node.getFullPath(), 80)
                + "}";
    }
}
