

package com.nageoffer.ai.ragent.core.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTML 文档解析器
 * <p>
 * 适用于业务侧富文本 HTML 片段，移除标签并尽量保留段落换行。
 */
@Component
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
        String html = new String(content, StandardCharsets.UTF_8);
        return ParseResult.ofText(cleanupHtml(html));
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return cleanupHtml(reader.lines().collect(Collectors.joining("\n")));
        } catch (Exception e) {
            throw new RuntimeException("解析 HTML 文件失败: " + fileName, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().contains("html");
    }

    private String cleanupHtml(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        document.select("br").append("\\n");
        document.select("p, div, li, h1, h2, h3, h4, h5, h6, blockquote").prepend("\\n").append("\\n");
        return TextCleanupUtil.cleanup(document.body().text()
                .replace("\\n", "\n"));
    }
}
