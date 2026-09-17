package com.example.file_management.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExportService {

    private static final String FONT_FAMILY = "ExportFont";

    // 与 FileServiceImpl 里识别图片标记时用的正则同构，这里改成捕获组以便回填 <img src>
    private static final Pattern IMAGE_MARKER_PATTERN = Pattern.compile(
            "\\[\\[IMAGE:(data:image/(?:png|jpe?g|jpg|gif|webp);base64,"
                    + "[A-Za-z0-9+/]+={0,2})]]");

    // 字体只在启动时从 classpath 读一次；导出用 FSSupplier<InputStream> 而不是 File，
    // 因为打包成可执行 jar 后 classpath 资源没有真实文件系统路径，Resource.getFile() 会抛异常
    private final byte[] fontBytes;

    public PdfExportService(
            @Value("${rag.pdf.export-font-path}") Resource fontResource) throws IOException {
        try (InputStream in = fontResource.getInputStream()) {
            this.fontBytes = in.readAllBytes();
        }
    }

    public byte[] export(String title, String content) throws IOException {
        String xhtml = buildXhtml(title, content == null ? "" : content);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFont(() -> new ByteArrayInputStream(fontBytes), FONT_FAMILY);
        builder.withHtmlContent(xhtml, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    // content 里的 [[IMAGE:...]] 标记本身就是合法的 data URI，直接回填进 <img src>。
    // 正文编辑器是纯 <textarea>，换行是字面的单个 \n，不是"空行分段"的段落语义，
    // 所以用 white-space: pre-wrap 原样保留换行，而不是按空行猜段落边界
    private String buildXhtml(String title, String content) {
        StringBuilder body = new StringBuilder();
        Matcher matcher = IMAGE_MARKER_PATTERN.matcher(content);
        int cursor = 0;
        while (matcher.find()) {
            appendTextSegment(body, content.substring(cursor, matcher.start()));
            body.append("<img src=\"").append(matcher.group(1)).append("\"/>\n");
            cursor = matcher.end();
        }
        appendTextSegment(body, content.substring(cursor));

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>\n"
                + "  body { font-family: '" + FONT_FAMILY + "'; font-size: 13px;"
                + " line-height: 1.6; white-space: pre-wrap; }\n"
                + "  h1 { font-family: '" + FONT_FAMILY + "'; font-size: 20px; white-space: normal; }\n"
                + "  img { max-width: 480px; }\n"
                + "</style></head><body>\n"
                + "<h1>" + escape(title == null ? "" : title) + "</h1>\n"
                + body
                + "</body></html>";
    }

    private void appendTextSegment(StringBuilder body, String text) {
        if (!text.isBlank()) {
            body.append("<div>").append(escape(text.strip())).append("</div>\n");
        }
    }

    private String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
