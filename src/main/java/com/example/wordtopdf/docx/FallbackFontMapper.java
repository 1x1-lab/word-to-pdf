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

    @Override
    public void populateFontMappings(Set<String> documentFontNames, Fonts wmlFonts) {
        // 手动映射已在构建时通过 put() 完成，这里不需要系统扫描。
    }
}
