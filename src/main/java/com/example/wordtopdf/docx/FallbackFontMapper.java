package com.example.wordtopdf.docx;

import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFont;
import org.docx4j.wml.Fonts;

import java.util.Set;

/**
 * 简单的字体映射器，不做系统字体扫描。
 *
 * <p>{@link org.docx4j.fonts.IdentityPlusMapper} 的静态初始化块会强制调用
 * {@code PhysicalFonts.discoverPhysicalFonts()}，在 Linux 无系统字体的环境下会抛
 * {@code NoSuchFileException}。本类继承 {@link Mapper} 但不触发系统扫描，只使用
 * 手动添加的字体映射。</p>
 */
public class FallbackFontMapper extends Mapper {

    private final PhysicalFont fallbackFont;

    public FallbackFontMapper(PhysicalFont fallbackFont) {
        this.fallbackFont = fallbackFont;
    }

    @Override
    public void populateFontMappings(Set<String> documentFontNames, Fonts wmlFonts) {
        // 把文档里所有字体都映射到兜底字体，避免 FOP 找不到字体。
        if (fallbackFont == null) {
            return;
        }
        for (String name : documentFontNames) {
            if (name != null && !name.isBlank() && get(name) == null) {
                put(name, fallbackFont);
            }
        }
    }
}
