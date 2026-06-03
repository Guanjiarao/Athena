package com.whu.software.athena.features.privacy;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MLKitAnonymizeService — 端侧离线 OCR + PII 识别服务。
 *
 * 调用入口：
 *   MLKitAnonymizeService.detectPIIInMedicalReport(bitmap, listener)
 *
 * 流程：
 *   1. 使用 ML Kit 中文离线识别器扫描全图文本块。
 *   2. 对每个 TextBlock / Line / Element 依次执行正则匹配。
 *   3. 命中 PII 模式的文本块 Rect 收集到列表，通过回调返回。
 *
 * 支持的 PII 模式：
 *   - 姓名字段（姓名/患者/姓  后跟汉字或拼音）
 *   - 身份证号（18 位：17 位数字 + 末位数字或 X）
 *   - 手机号（1[3-9] 开头的 11 位数字）
 *   - 地址字段（地址/家庭住址/住址 后跟文字）
 *   - 日期（yyyy年mm月dd日 / yyyy-mm-dd / yyyy/mm/dd）
 *   - 医院/科室名称（含"医院"/"诊所"/"科"的行）
 *
 * 注意：所有识别在调用线程（Task 回调）完成，结果通过 OnPIIDetectedListener 返回主线程。
 */
public final class MLKitAnonymizeService {

    private MLKitAnonymizeService() {}

    // ── PII 正则表达式 ────────────────────────────────────────────────────────

    /** 姓名字段：姓名/患者/姓名（姓）后跟 1-10 个汉字或字母 */
    private static final Pattern PATTERN_NAME = Pattern.compile(
            "(?:姓\\s*名|患\\s*者|病\\s*人|就\\s*诊\\s*人|性\\s*名)[\\s:：]*([\\u4e00-\\u9fa5a-zA-Z]{1,10})");

    /** 身份证：17 位数字 + 末位数字或 X/x */
    private static final Pattern PATTERN_ID_CARD = Pattern.compile(
            "\\b[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b");

    /** 手机号：1[3-9] 开头 11 位 */
    private static final Pattern PATTERN_PHONE = Pattern.compile(
            "(?<![\\d])1[3-9]\\d{9}(?![\\d])");

    /** 地址字段 */
    private static final Pattern PATTERN_ADDRESS = Pattern.compile(
            "(?:地\\s*址|家\\s*庭\\s*住\\s*址|住\\s*址|居\\s*住\\s*地)[\\s:：]*([^\\n]{2,40})");

    /** 日期 */
    private static final Pattern PATTERN_DATE = Pattern.compile(
            "\\d{4}\\s*[年/-]\\s*\\d{1,2}\\s*[月/-]\\s*\\d{1,2}\\s*[日]?");

    /** 医院/科室 */
    private static final Pattern PATTERN_HOSPITAL = Pattern.compile(
            "[\\u4e00-\\u9fa5]{1,15}(?:医院|诊所|卫生院|门诊|诊断中心|科|病区|病房)");

    private static final Pattern[] ALL_PATTERNS = {
            PATTERN_NAME, PATTERN_ID_CARD, PATTERN_PHONE,
            PATTERN_ADDRESS, PATTERN_DATE, PATTERN_HOSPITAL
    };

    // ── 回调接口 ──────────────────────────────────────────────────────────────

    public interface OnPIIDetectedListener {
        /**
         * @param piiRegions  命中 PII 的文本块在 Bitmap 坐标系内的矩形列表（像素单位）
         * @param piiTexts    对应文本内容（方便调试/日志）
         */
        void onPIIDetected(@NonNull List<Rect> piiRegions, @NonNull List<String> piiTexts);

        /** OCR 或正则阶段出错时回调。 */
        void onError(@NonNull Exception e);
    }

    // ── 主入口 ────────────────────────────────────────────────────────────────

    /**
     * 对医疗报告图片执行离线 OCR 并自动检测 PII。
     *
     * @param bitmap   待识别的原始 Bitmap
     * @param listener 结果回调（在 ML Kit 的 Task 线程池上触发，如需更新 UI 请 post 到主线程）
     */
    public static void detectPIIInMedicalReport(
            @NonNull Bitmap bitmap,
            @NonNull OnPIIDetectedListener listener) {

        // 使用中文离线识别器（ChineseTextRecognizerOptions）
        // 同时支持中英混排，不依赖 GMS，模型随 APK 捆绑
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    List<Rect>   regions = new ArrayList<>();
                    List<String> texts   = new ArrayList<>();

                    scanForPII(visionText, regions, texts);

                    listener.onPIIDetected(regions, texts);
                    recognizer.close();
                })
                .addOnFailureListener(e -> {
                    recognizer.close();
                    listener.onError(e);
                });
    }

    // ── 内部扫描逻辑 ──────────────────────────────────────────────────────────

    /**
     * 三级扫描：Block → Line → Element，粒度从粗到细。
     * 命中时记录该级别的 boundingBox，并避免重复收录（父级命中则跳过子级）。
     */
    private static void scanForPII(
            @NonNull Text visionText,
            @NonNull List<Rect> outRegions,
            @NonNull List<String> outTexts) {

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            String blockText = block.getText();
            Rect   blockBox  = block.getBoundingBox();
            if (blockBox == null) continue;

            // Block 级：整段命中（通常是多行联合语境，如"姓名：张三  年龄：25"）
            if (matchesAnyPII(blockText)) {
                if (!isAlreadyCovered(outRegions, blockBox)) {
                    outRegions.add(new Rect(blockBox));
                    outTexts.add(blockText.replace("\n", " "));
                }
                continue; // Block 已命中，不再拆解子级
            }

            // Line 级
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                Rect   lineBox  = line.getBoundingBox();
                if (lineBox == null) continue;

                if (matchesAnyPII(lineText)) {
                    if (!isAlreadyCovered(outRegions, lineBox)) {
                        outRegions.add(new Rect(lineBox));
                        outTexts.add(lineText);
                    }
                    continue;
                }

                // Element 级（单词/字符组）
                for (Text.Element element : line.getElements()) {
                    String elemText = element.getText();
                    Rect   elemBox  = element.getBoundingBox();
                    if (elemBox == null) continue;

                    if (matchesAnyPII(elemText)) {
                        if (!isAlreadyCovered(outRegions, elemBox)) {
                            outRegions.add(new Rect(elemBox));
                            outTexts.add(elemText);
                        }
                    }
                }
            }
        }
    }

    /** 对给定文本逐一跑所有 PII 正则，任意一条命中即返回 true。 */
    private static boolean matchesAnyPII(@NonNull String text) {
        for (Pattern p : ALL_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find()) return true;
        }
        return false;
    }

    /**
     * 避免父级区域和子级区域双重收录：
     * 若新矩形已被列表中某个矩形完全包含，则认为已覆盖。
     */
    private static boolean isAlreadyCovered(@NonNull List<Rect> existing, @NonNull Rect candidate) {
        for (Rect r : existing) {
            if (r.contains(candidate)) return true;
        }
        return false;
    }
}
