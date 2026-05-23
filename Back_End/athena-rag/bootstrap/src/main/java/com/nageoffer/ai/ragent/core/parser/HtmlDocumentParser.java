

package com.nageoffer.ai.ragent.core.parser;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTML 文档解析器
 * <p>
 * 专门用于解析 HTML 格式的文档，特别是 Athena 笔记
 * 使用 Jsoup 进行 HTML 解析和文本提取，能够正确处理 HTML 标签和样式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlDocumentParser implements DocumentParser {

    @Override
    public String getParserType() {
        return ParserType.HTML.getType();
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParseResult.ofText("");
        }

        try {
            String html = new String(content, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);
            // 提取纯文本，自动去除所有 HTML 标签和样式
            String text = doc.text();
            String cleaned = TextCleanupUtil.cleanup(text);
            return ParseResult.ofText(cleaned);
        } catch (Exception e) {
            log.error("HTML 解析失败，MIME 类型: {}", mimeType, e);
            throw new ServiceException("HTML 文档解析失败: " + e.getMessage());
        }
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        try {
            Document doc = Jsoup.parse(stream, StandardCharsets.UTF_8.name(), "");
            String text = doc.text();
            return TextCleanupUtil.cleanup(text);
        } catch (Exception e) {
            log.error("从 HTML 文件中提取文本内容失败: {}", fileName, e);
            throw new ServiceException("解析 HTML 文件失败: " + fileName);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        // 支持 HTML 和相关的 MIME 类型
        return mimeType != null && (
                mimeType.equalsIgnoreCase("text/html") ||
                mimeType.equalsIgnoreCase("application/xhtml+xml") ||
                mimeType.equalsIgnoreCase("HTML")
        );
    }
}
