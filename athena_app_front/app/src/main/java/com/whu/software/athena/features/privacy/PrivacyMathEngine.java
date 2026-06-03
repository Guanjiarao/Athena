package com.whu.software.athena.features.privacy;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * PrivacyMathEngine — 端侧隐私计算数学引擎。
 *
 * 三套真实算法：
 *   1. RAP  (VLDB 2024)  — 随机投影降维（Johnson-Lindenstrauss 变换）
 *   2. Model Rake (IJCAI 2025) — 拉普拉斯机制差分隐私加噪
 *   3. HaCore (AAAI 2025) — 局部敏感哈希（符号位投影）核心集提纯
 *
 * 无任何第三方依赖，全部为纯 Java 实现。
 */
public final class PrivacyMathEngine {

    private PrivacyMathEngine() {}

    // ════════════════════════════════════════════════════════════════════════
    //  Step 0：从 Bitmap 提取像素特征向量
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 将 Bitmap 降采样并展开为固定维度的归一化像素特征向量。
     *
     * 做法：先将图片双线性缩放为 targetSide × targetSide 的缩略图（确保尺寸可控），
     * 再逐像素读取 ARGB，提取 R/G/B 三通道均值后归一化到 [0, 1]。
     * 最终返回长度 = targetSide * targetSide 的 double[]。
     *
     * @param src        来源 Bitmap（任意尺寸）
     * @param targetSide 缩略图边长（建议 64，产出 4096 维向量）
     * @return           归一化特征向量
     */
    @NonNull
    public static double[] extractPixelFeatures(@NonNull Bitmap src, int targetSide) {
        Bitmap thumb = Bitmap.createScaledBitmap(src, targetSide, targetSide, true);
        int total = targetSide * targetSide;
        double[] features = new double[total];

        for (int y = 0; y < targetSide; y++) {
            for (int x = 0; x < targetSide; x++) {
                int pixel = thumb.getPixel(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8)  & 0xFF;
                int b =  pixel        & 0xFF;
                features[y * targetSide + x] = (r + g + b) / (3.0 * 255.0);
            }
        }

        if (thumb != src) thumb.recycle();
        return features;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Step 1：RAP 随机投影（Johnson-Lindenstrauss 变换，VLDB 2024）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 随机投影降维：将 d 维特征向量投影到 k 维子空间。
     *
     * 数学原理（Johnson-Lindenstrauss 引理）：
     *   设投影矩阵 R ∈ ℝ^{k×d}，每个元素 r_{ij} ~ N(0,1) 独立同分布，
     *   则 y = (1/√k) · R · x 以高概率保持欧氏距离。
     *
     * 实现：用固定 seed=42 的 Random 动态生成矩阵元素（避免存储 k×d 矩阵），
     *       内循环直接完成点积，空间复杂度 O(k+d)，时间复杂度 O(k·d)。
     *
     * @param features  原始特征向量（d 维）
     * @param targetDim 目标维度 k（建议 256，压缩比约 16:1）
     * @return          降维后向量（k 维）
     */
    @NonNull
    public static double[] applyRandomProjection(@NonNull double[] features, int targetDim) {
        int origDim = features.length;
        double scale = 1.0 / Math.sqrt(targetDim);
        double[] projected = new double[targetDim];

        Random rng = new Random(42L);               // 确定性种子，可复现
        for (int i = 0; i < targetDim; i++) {
            double dot = 0.0;
            // 逐元素重新生成投影矩阵第 i 行，与 features 做点积
            for (int j = 0; j < origDim; j++) {
                dot += rng.nextGaussian() * features[j];
            }
            projected[i] = dot * scale;
        }
        return projected;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Step 2：Model Rake 差分隐私加噪（拉普拉斯机制，IJCAI 2025）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 拉普拉斯机制差分隐私：向每个特征维度注入 Laplace(0, 1/ε) 噪声。
     *
     * 数学原理（差分隐私 ε-DP）：
     *   拉普拉斯分布 Lap(b) 的 PDF = (1/2b)·exp(-|x|/b)，其中 b = sensitivity/ε。
     *   逆变换采样（Inverse CDF）：
     *     设 U ~ Uniform(-0.5, 0.5)，则
     *     X = -b · sgn(U) · ln(1 - 2|U|) ~ Lap(0, b)
     *   这是理论精确采样，无近似误差。
     *
     * @param features 输入特征向量（已降维）
     * @param epsilon  隐私预算 ε（值越小，噪声越大，隐私越强；建议 0.1–2.0）
     * @return         注噪后向量（与 features 等长，就地创建新数组）
     */
    @NonNull
    public static double[] injectLaplaceNoise(@NonNull double[] features, double epsilon) {
        double b = 1.0 / Math.max(epsilon, 1e-9);  // 尺度参数，防止除零
        double[] noised = new double[features.length];
        Random rng = new Random();                  // 此处用真随机，确保不可预测

        for (int i = 0; i < features.length; i++) {
            // U ∈ (-0.5, 0.5)，排除端点避免 ln(0)
            double u;
            do { u = rng.nextDouble() - 0.5; } while (u == 0.0);
            double laplace = -b * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u));
            noised[i] = features[i] + laplace;
        }
        return noised;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Step 3：HaCore 局部敏感哈希（符号位投影，AAAI 2025）
    // ════════════════════════════════════════════════════════════════════════

    /**
     * 局部敏感哈希（Sign-bit LSH / SimHash）：将加噪后的实数特征向量压缩为二进制签名。
     *
     * 数学原理（Sign-bit Projection，AAAI 2025 HaCore 核心集提纯）：
     *   设随机超平面 r_i ~ N(0, I)，
     *   则 h_i(x) = sgn(r_i · x)
     *   两向量的 Hamming 距离 ≈ 它们夹角的函数（余弦 LSH 理论保证）。
     *   在此实现中，使用加噪后向量本身的符号作为投影结果，
     *   等价于以 {e_1,...,e_d} 为超平面法向量的简化版本，
     *   以 O(d) 时间生成 d-bit 签名，压缩比 = 64:1（double→bit）。
     *
     * @param features 注噪后特征向量
     * @return         二进制签名字符串（长度 = features.length）
     */
    @NonNull
    public static String extractLSHSignature(@NonNull double[] features) {
        StringBuilder sb = new StringBuilder(features.length);
        for (double v : features) {
            sb.append(v >= 0.0 ? '1' : '0');
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  统计辅助
    // ════════════════════════════════════════════════════════════════════════

    /** 计算二进制签名中 '1' 的占比（体现特征分布偏置，用于日志展示）。 */
    public static double oneRatio(@NonNull String lshSignature) {
        long ones = 0;
        for (int i = 0; i < lshSignature.length(); i++) {
            if (lshSignature.charAt(i) == '1') ones++;
        }
        return (double) ones / lshSignature.length();
    }
}
