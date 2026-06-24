package com.example.wordtopdf.config;

import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * 应用启动后自动加载字体。
 *
 * <p>用 {@link EventListener} 监听 {@link ApplicationReadyEvent}，在 Spring Boot 完全启动后：</p>
 * <ol>
 *   <li>读 {@code app.convert.font-path} 配置的字体文件路径</li>
 *   <li>用 {@link PhysicalFonts#addPhysicalFont(URI)} 直接加载这一个字体文件（不扫描整个目录）</li>
 *   <li>缓存 {@link PhysicalFont} 对象，后续每次 PDF 渲染直接复用</li>
 * </ol>
 *
 * <p>所有字体相关的逻辑集中在这里，{@link com.example.wordtopdf.docx.SimplePdfExporter}
 * 只管「拿字体 → 建映射 → 渲染 PDF」，不关心字体怎么加载的。</p>
 */
@Component
public class FontInitializer {

    private static final Logger log = LoggerFactory.getLogger(FontInitializer.class);

    private final ConvertProperties properties;

    /** 启动时加载一次的字体对象。 */
    private volatile PhysicalFont cachedFont;

    public FontInitializer(ConvertProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        String fontPath = properties.getFontPath();
        if (fontPath == null || fontPath.isBlank()) {
            log.warn("app.convert.font-path not configured; PDF will not render Chinese.");
            return;
        }
        loadFont(fontPath);
    }

    /**
     * 加载单个字体文件并缓存。
     *
     * <p>用 {@link PhysicalFonts#addPhysicalFont(URI)} 直接加载用户指定的那一个文件，
     * <b>不扫描整个字体目录</b>，避免 macOS 系统目录下 AAT 字体导致 docx4j 崩溃。</p>
     */
    @SuppressWarnings("deprecation")
    private void loadFont(String path) {
        File fontFile = new File(path);
        if (!fontFile.exists()) {
            log.warn("Font file not found: {}", path);
            return;
        }
        log.info("Loading font: {} ({} bytes)", fontFile.getAbsolutePath(), fontFile.length());

        // 直接加载单个字体文件
        try {
            PhysicalFonts.addPhysicalFont(fontFile.toURI());
        } catch (Exception e) {
            log.error("Failed to register font: {}", e.toString());
            return;
        }

        // 按文件名从已注册字体中匹配
        String targetName = fontFile.getName().toLowerCase(Locale.ROOT);
        Map<String, PhysicalFont> all = PhysicalFonts.getPhysicalFonts();
        if (all != null) {
            for (PhysicalFont f : all.values()) {
                URI uri = f.getEmbeddedURI();
                if (uri != null) {
                    String filename = extractFilename(uri).toLowerCase(Locale.ROOT);
                    if (filename.contains(targetName) || targetName.contains(filename)) {
                        cachedFont = f;
                        log.info("Font loaded and cached: name='{}', file='{}'",
                                f.getName(), filename);
                        return;
                    }
                }
            }
        }

        // 文件名匹配失败，取最后注册的（就是刚 add 的）
        if (all != null && !all.isEmpty()) {
            cachedFont = all.values().iterator().next();
            log.info("Font loaded (fallback match): name='{}'", cachedFont.getName());
        } else {
            log.warn("Font registered but could not be found in PhysicalFonts map.");
        }
    }

    /**
     * 获取已缓存的字体。如果字体没加载成功，返回 null。
     *
     * @return 缓存的 {@link PhysicalFont}，或 null
     */
    public PhysicalFont getFont() {
        return cachedFont;
    }

    /** 从 URI 提取文件名（不含路径）。 */
    private static String extractFilename(URI uri) {
        if (uri == null) return "";
        try {
            String p = uri.getPath() != null ? uri.getPath() : uri.getSchemeSpecificPart();
            if (p == null) return "";
            int slash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            return slash >= 0 ? p.substring(slash + 1) : p;
        } catch (Exception e) {
            return "";
        }
    }
}
