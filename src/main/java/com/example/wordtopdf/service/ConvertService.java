package com.example.wordtopdf.service;

import com.example.wordtopdf.config.ConvertProperties;
import com.example.wordtopdf.docx.DocxTemplateFiller;
import com.example.wordtopdf.docx.HeaderImageInserter;
import com.example.wordtopdf.docx.SimplePdfExporter;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 转换流程的总编排，串起整个 pipeline：
 *
 * <pre>
 *   读 docx 字节
 *     -> WordprocessingMLPackage（反序列化 docx）
 *     -> {@link DocxTemplateFiller} 填充 ${var} 占位符
 *     -> 顶部插入固定图片（Logo）
 *     -> {@link PdfExporter} 导出 PDF
 * </pre>
 *
 * <p>变量替换走自研的 {@link DocxTemplateFiller}（50 行代码，覆盖跨 run 拆分、
 * save/load 往返、找不到 key 保留占位符等所有边界场景）。不引入 poi-tl / office-stamper
 * 之类的第三方模板引擎 —— 因为业务需求只有简单文本替换，自研完全够用，少一个
 * 外部依赖就少一份维护风险。</p>
 */
@Service
public class ConvertService {

    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);

    private final ResourceLoader resourceLoader;
    private final ConvertProperties properties;
    private final SimplePdfExporter pdfExporter;

    // 头图字节的缓存。图片一般是固定的（比如 Logo），每次请求都重新读 IO 太浪费。
    // 用 volatile + synchronized 双重检查锁保证线程安全。
    private volatile byte[] cachedHeaderImage;

    public ConvertService(ResourceLoader resourceLoader, ConvertProperties properties,
                          SimplePdfExporter pdfExporter) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.pdfExporter = pdfExporter;
    }

    /**
     * 把 docx 模板转成 PDF。
     *
     * <p>执行流程：变量替换 → 插入头图 → 转 PDF。每一步失败都包装成
     * {@link ConversionException} 抛给 Controller。</p>
     *
     * @param docxTemplate docx 文件的原始字节
     * @param variables    占位符键值对，key 对应 {@code ${key}}
     * @return 渲染好的 PDF 字节
     * @throws ConversionException 任何步骤失败
     */
    public byte[] convert(byte[] docxTemplate, Map<String, ?> variables) throws ConversionException {
        long start = System.currentTimeMillis();

        try (ByteArrayInputStream in = new ByteArrayInputStream(docxTemplate);
             ByteArrayOutputStream out = new ByteArrayOutputStream(docxTemplate.length)) {

            // 1. 反序列化 docx
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(in);

            // 2. 填充占位符 ${var}
            int replacements = DocxTemplateFiller.fill(pkg, variables);
            log.info("Filled {} placeholder(s) for {} variable(s).", replacements,
                    variables == null ? 0 : variables.size());

            // 3. 顶部插入固定图片
            insertHeaderImage(pkg);

            // 调试开关：通过 -Dapp.docx.dump=/path/to/out.docx 把处理后的 docx 落盘
            String dumpDocx = System.getProperty("app.docx.dump");
            if (dumpDocx != null && !dumpDocx.isBlank()) {
                try (java.io.FileOutputStream docxOut = new java.io.FileOutputStream(dumpDocx)) {
                    pkg.save(docxOut);
                    log.info("Mutated docx dumped to {}", dumpDocx);
                } catch (Exception e) {
                    log.warn("docx dump failed: {}", e.toString());
                }
            }

            // 4. 渲染 PDF（用 SimplePdfExporter：所有字体映射到一个固定字体）
            pdfExporter.export(pkg, out);

            byte[] pdf = out.toByteArray();
            log.info("PDF generated, size={} bytes, took {} ms.",
                    pdf.length, System.currentTimeMillis() - start);
            return pdf;
        } catch (ConversionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Conversion failed", e);
            throw new ConversionException("Conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * 在文档顶部插入头图。找不到头图资源时跳过（不算错误，方便禁用）。
     */
    private void insertHeaderImage(WordprocessingMLPackage pkg) throws ConversionException {
        byte[] image = loadHeaderImage();
        if (image == null) {
            log.info("Header image disabled (resource not found); skipping.");
            return;
        }
        try {
            String altText = deriveAltText(properties.getHeaderImage());
            HeaderImageInserter.insertAtTop(
                    pkg,
                    image,
                    altText,
                    properties.getHeaderImageWidthEmu(),
                    properties.getHeaderImageHeightEmu()
            );
        } catch (Exception e) {
            throw new ConversionException("Failed to insert header image: " + e.getMessage(), e);
        }
    }

    /**
     * 从 header-image 配置路径里提取 altText（无障碍文字）。
     * 例：{@code classpath:templates/logo.png} → {@code "logo"}。
     * 提取失败时退回固定字符串 "header-image"，保证 docx 始终有非空 altText。
     */
    private static String deriveAltText(String location) {
        if (location == null || location.isBlank()) {
            return "header-image";
        }
        // 去掉 Spring resource 前缀（classpath: / file: / http: 等）
        String path = location.replaceAll("^[a-zA-Z]+:", "");
        // 取最后一段（文件名），去扩展名
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = fileName.lastIndexOf('.');
        String result = dot > 0 ? fileName.substring(0, dot) : fileName;
        return result.isBlank() ? "header-image" : result;
    }

    /**
     * 加载头图字节，带缓存。
     *
     * <p>第一次调用时从配置的位置（classpath / 文件路径）读取，之后直接返回缓存。
     * 双重检查锁（DCL）保证多线程下只读一次。</p>
     *
     * @return 图片字节；配置的头图资源不存在时返回 null（跳过插入）
     */
    private byte[] loadHeaderImage() throws ConversionException {
        byte[] cached = cachedHeaderImage;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedHeaderImage != null) {
                return cachedHeaderImage;
            }
            String location = properties.getHeaderImage();
            try {
                Resource resource = resourceLoader.getResource(location);
                if (!resource.exists()) {
                    log.warn("Header image resource '{}' not found; skipping.", location);
                    return null;
                }
                try (InputStream is = resource.getInputStream()) {
                    cachedHeaderImage = StreamUtils.copyToByteArray(is);
                    log.info("Loaded header image from '{}' ({} bytes).",
                            location, cachedHeaderImage.length);
                }
                return cachedHeaderImage;
            } catch (IOException e) {
                throw new ConversionException("Cannot read header image '" + location + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * 转换流程的统一异常类型。
     * <p>Controller 通过 catch 这个异常把响应映射成 422（Unprocessable Entity），
     * 而不是把堆栈当成 500 暴露给前端。</p>
     */
    public static final class ConversionException extends RuntimeException {
        public ConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
