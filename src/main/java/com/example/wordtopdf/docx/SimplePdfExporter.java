package com.example.wordtopdf.docx;

import com.example.wordtopdf.config.FontInitializer;
import org.docx4j.Docx4J;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;

/**
 * 简化版 PDF 导出器：用 {@link FontInitializer} 加载的固定字体做所有字体的兜底。
 *
 * <p>Spring {@code @Component}，由 {@link com.example.wordtopdf.service.ConvertService} 注入使用。
 * 字体加载逻辑在 {@link FontInitializer} 里，本类只负责「拿字体 → 建映射 → 渲染 PDF」。</p>
 */
@Component
public class SimplePdfExporter {

    private static final Logger log = LoggerFactory.getLogger(SimplePdfExporter.class);

    private final FontInitializer fontInitializer;

    /**
     * docx 模板里最常见的字体名，全部映射到 FontInitializer 加载的字体。
     * 西文 + 中文（Windows / macOS 两种模板来源都覆盖）。
     */
    private static final String[] MAPPED_FONT_NAMES = {
            "Calibri", "Calibri Light", "Cambria", "Candara",
            "Times New Roman", "Arial", "Helvetica",
            "SimSun", "宋体", "SimHei", "黑体",
            "Microsoft YaHei", "微软雅黑",
            "PingFang SC", "PingFang TC",
            "FangSong", "仿宋", "KaiTi", "楷体",
            "DengXian", "等线", "DengXian Light", "等线 Light",
            "Symbol", "Wingdings", "Wingdings 2", "Wingdings 3",
            "Segoe UI Symbol", "MT Extra"
    };

    public SimplePdfExporter(FontInitializer fontInitializer) {
        this.fontInitializer = fontInitializer;
    }

    /**
     * 把 docx 渲染成 PDF 写到 out。
     */
    public void export(WordprocessingMLPackage pkg, OutputStream out) throws Exception {
        pkg.setFontMapper(buildMapper());
        Docx4J.toPDF(pkg, out);
        out.flush();
    }

    /**
     * 构建 Mapper：把所有常见 Office 字体名映射到 FontInitializer 缓存的字体。
     */
    private Mapper buildMapper() {
        IdentityPlusMapper mapper = new IdentityPlusMapper();
        PhysicalFont font = fontInitializer.getFont();
        if (font == null) {
            log.warn("No font loaded; PDF may not render Chinese.");
            return mapper;
        }
        for (String name : MAPPED_FONT_NAMES) {
            mapper.put(name, font);
        }
        return mapper;
    }
}
