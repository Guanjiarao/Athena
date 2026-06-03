package com.whu.software.athena.features.privacy;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * ShapleyMathEngine — 端侧蒙特卡洛 Shapley 价值评估引擎。
 *
 * 论文参照：PS-MI (VLDB 2025) — Privacy-preserving Shapley-based
 *           Marginal-contribution Inference for Federated Learning
 *
 * 三个核心方法：
 *   1. assessDataQuality     — Shannon 信息熵评估 LSH 签名丰富度
 *   2. evaluateUtility       — 对数增长效用函数（模拟联邦模型精度增益）
 *   3. computeMonteCarloShapley — 蒙特卡洛近似 Shapley 值
 */
public final class ShapleyMathEngine {

    /** SharedPreferences 文件名与键名（供外部共享）。 */
    public static final String PREFS_NAME       = "athena_data_asset";
    public static final String KEY_SHAPLEY_SUM  = "shapley_cumulative";
    public static final String KEY_POINTS_SUM   = "athena_points_cumulative";

    private ShapleyMathEngine() {}

    // ════════════════════════════════════════════════════════════════════════
    //  1. 数据质量评估（Shannon 熵）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 计算 LSH 签名的 Shannon 信息熵，衡量特征向量的"多样性"与信息丰富度。
     *
     * 原理：
     *   对二值字符串 s，令 p = count('1')/len(s)，q = 1-p，
     *   则 H = -(p·log₂p + q·log₂q)   （最大值为 1，当 p=0.5 时）
     *   归一化后直接作为数据质量分 ∈ [0, 1]。
     *
     * @param lshSignature HaCore 生成的二进制签名字符串
     * @return 数据质量分（0.0 ~ 1.0）
     */
    public static double assessDataQuality(@NonNull String lshSignature) {
        if (lshSignature.isEmpty()) return 0.0;

        long ones = 0;
        for (int i = 0; i < lshSignature.length(); i++) {
            if (lshSignature.charAt(i) == '1') ones++;
        }

        double p = (double) ones / lshSignature.length();
        double q = 1.0 - p;

        // 避免 log(0)
        if (p <= 0.0 || q <= 0.0) return 0.0;

        // 二元香农熵，归一化（最大值 1.0 对应 p=0.5）
        double entropy = -(p * log2(p) + q * log2(q));
        return Math.min(1.0, Math.max(0.0, entropy));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  2. 联邦效用函数（对数增长模型）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 模拟联邦医疗大模型在 coalitionSize 个节点、累计质量 totalQuality 下的预测精度。
     *
     * 公式（对数增益模型，参照 VLDB 2025 PS-MI §4.2）：
     *   U(k, Q) = min(0.99,  0.5 + 0.4 · log₁₀(1 + Q))
     *
     * 其中 Q = coalitionSize · avgQuality，体现"节点越多、质量越好，精度增益越大"。
     *
     * @param coalitionSize 联盟节点数量
     * @param totalQuality  联盟的累计质量分（所有节点质量之和）
     * @return              模型效用（精度），上限 0.99
     */
    public static double evaluateUtility(int coalitionSize, double totalQuality) {
        if (coalitionSize <= 0 || totalQuality <= 0) return 0.5; // 基础精度
        double utility = 0.5 + 0.4 * Math.log10(1.0 + totalQuality);
        return Math.min(0.99, utility);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  3. 蒙特卡洛 Shapley 值计算（核心）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 基于蒙特卡洛采样逼近目标用户的 Shapley 贡献度。
     *
     * 算法（PS-MI VLDB 2025, Algorithm 1）：
     *   Φ(i) ≈ (1/T) · Σ_{t=1}^{T} [U(S_t ∪ {i}) - U(S_t)]
     *   其中 S_t 为每轮随机抽取的先行联盟（大小 k ~ Uniform[0, n-1]），
     *   目标用户质量固定为其自身 LSH 签名的熵值。
     *
     * 实现细节：
     *   - 其他参与者的质量用服从 Beta(2,5) 近似的随机值模拟（均值~0.29，偏低以突出贡献边际）
     *   - 每轮随机确定先行联盟大小 k，再抽取各节点质量
     *   - 边际贡献 = U(k+1, Q+userQuality) - U(k, Q)
     *
     * @param userHash         用户本次上传的 HaCore LSH 签名
     * @param totalParticipants 联邦网络总节点数
     * @param simulations      蒙特卡洛模拟次数
     * @return                 Shapley 贡献度（保留 4 位小数）
     */
    public static double computeMonteCarloShapley(
            @NonNull String userHash, int totalParticipants, int simulations) {

        double userQuality = assessDataQuality(userHash);
        Random rng = new Random();
        double marginalSum = 0.0;

        for (int t = 0; t < simulations; t++) {
            // 随机先行联盟大小 k ∈ [0, n-1]
            int k = rng.nextInt(Math.max(1, totalParticipants));

            // 随机生成 k 个先行节点的质量（Beta(2,5) 近似：2 个 Uniform 之积）
            double coalitionQuality = 0.0;
            for (int j = 0; j < k; j++) {
                double q = rng.nextDouble() * rng.nextDouble(); // Beta(2,1) 近似
                coalitionQuality += 0.3 + 0.7 * q;             // 缩放到 [0.3, 1.0]
            }

            double utilityWithout = evaluateUtility(k, coalitionQuality);
            double utilityWith    = evaluateUtility(k + 1, coalitionQuality + userQuality);
            marginalSum += (utilityWith - utilityWithout);
        }

        double shapley = marginalSum / simulations;
        // 保留 4 位小数
        return Math.round(shapley * 10000.0) / 10000.0;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  4. 对齐贡献 Shapley（RLHF 纠偏语料专用）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 计算用户提交的 RLHF 纠偏文本对联邦大模型的 Shapley 对齐贡献度。
     *
     * 背景（Constitutional AI / RLHF, Anthropic 2022 & InstructGPT 2022）：
     *   人工纠偏语料是构建 SFT / RLHF 微调数据集的高价值种子数据，其信息价值
     *   远超普通图片脱敏特征，因此引入 5× 对齐加权因子（alignmentBoost）。
     *
     * 算法：
     *   1. 从文本提取"字符级多元香农熵"作为质量分 Q_text ∈ [0, 1]：
     *        对每个字符 c，统计频率 f_c，计算 H = -Σ f_c·log₂(f_c)，
     *        再除以 log₂(alphabetSize) 归一化。
     *   2. 将 Q_text 加权融合到 LSH 签名伪造哈希中（复用 computeMonteCarloShapley）：
     *        构造一个长度为 256 的二值伪签名，其激活率 p = clamp(Q_text, 0.3, 0.7)，
     *        以确保香农熵落在 [0.88, 1.0] 的高质区间（文本普遍比图片特征更均匀分布）。
     *   3. 调用 computeMonteCarloShapley 得到基础 φ，再乘以 alignmentBoost=5。
     *   4. 保留 4 位小数返回。
     *
     * @param correctionText 用户提交的纠偏/反馈文本（不为空）
     * @return 对齐贡献加权后的 Shapley 值（≈ 普通图片贡献的 5 倍）
     */
    public static double computeAlignmentShapley(@NonNull String correctionText) {
        final double ALIGNMENT_BOOST = 5.0;

        // ── Step 1：字符级多元香农熵，衡量文本信息丰度 ──────────────────────
        double textQuality = assessTextEntropy(correctionText);

        // ── Step 2：将文本质量映射为等价 LSH 伪签名（256 bits）────────────
        // 激活率 p 夹在 [0.3, 0.7]，让二元熵保持在 0.88~1.0 的高质区间
        double activationRate = 0.3 + textQuality * 0.4; // [0.3, 0.7]
        String pseudoHash = buildPseudoHash(activationRate, 256);

        // ── Step 3：复用蒙特卡洛引擎计算基础 Shapley ──────────────────────
        double baseShapley = computeMonteCarloShapley(pseudoHash, 100, 500);

        // ── Step 4：对齐加权 × 5，保留 4 位小数 ──────────────────────────
        double weighted = baseShapley * ALIGNMENT_BOOST;
        return Math.round(weighted * 10000.0) / 10000.0;
    }

    /**
     * 计算字符串的字符级多元 Shannon 熵，归一化到 [0, 1]。
     * 空字符串或全同字符返回 0.0；字符分布越均匀，得分越趋近 1.0。
     */
    static double assessTextEntropy(@NonNull String text) {
        if (text.isEmpty()) return 0.0;

        // 统计每个字符的出现频次
        java.util.HashMap<Character, Integer> freq = new java.util.HashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }

        int n = text.length();
        int alphabetSize = freq.size();
        if (alphabetSize <= 1) return 0.0;

        double entropy = 0.0;
        for (int count : freq.values()) {
            double p = (double) count / n;
            entropy -= p * log2(p);
        }

        // 归一化：最大熵 = log₂(alphabetSize)
        double maxEntropy = log2(alphabetSize);
        return maxEntropy > 0 ? Math.min(1.0, entropy / maxEntropy) : 0.0;
    }

    /**
     * 根据目标激活率 p 生成一条长度为 {@code length} 的确定性伪 LSH 签名。
     * 使用线性同余序列替代 Random，保证相同输入输出一致（可复现）。
     */
    private static String buildPseudoHash(double activationRate, int length) {
        StringBuilder sb = new StringBuilder(length);
        // 线性同余生成器（seed 固定，保证可复现）
        long state = 0x5DEECE66DL;
        for (int i = 0; i < length; i++) {
            state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            double r = (state >>> 17) / (double) (1 << 31);
            sb.append(r < activationRate ? '1' : '0');
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════════════════════════════════

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }
}
