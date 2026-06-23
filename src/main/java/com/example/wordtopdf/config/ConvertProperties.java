package com.example.wordtopdf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 转换流程的可配置项。
 *
 * <p>对应 {@code application.yml} 里的 {@code app.convert.*}：</p>
 *
 * <pre>
 * app:
 *   convert:
 *     header-image: classpath:templates/header-logo.png
 *     header-image-width-emu: 0
 *     header-image-height-emu: 600000
 *     font-path: /opt/app/fonts/font.otf
 * </pre>
 *
 * <p><b>EMU</b> = English Metric Unit，Office 内部长度单位。914400 EMU = 1 英寸 = 2.54 cm。
 * 换算：1 cm ≈ 360000 EMU。</p>
 *
 * <p>用普通 class（不是 record），保证兼容 JDK 11（record 是 JDK 16+）。
 * Spring 用 setter 注入字段。</p>
 */
@ConfigurationProperties(prefix = "app.convert")
public class ConvertProperties {

    /** 头图位置，支持 classpath: / file: / http(s): 前缀 */
    private String headerImage;

    /** 头图宽度（EMU）；0/null 表示不指定，由高度按比例算 */
    private Long headerImageWidthEmu;

    /** 头图高度（EMU）；0/null 表示不指定，由宽度按比例算 */
    private Long headerImageHeightEmu;

    /** SimplePdfExporter 用的字体文件完整路径（文件系统路径，绝对或相对） */
    private String fontPath;

    public String getHeaderImage() {
        if (headerImage == null || headerImage.isBlank()) {
            return "classpath:templates/header-logo.png";
        }
        return headerImage;
    }

    public void setHeaderImage(String headerImage) {
        this.headerImage = headerImage;
    }

    public Long getHeaderImageWidthEmu() {
        return headerImageWidthEmu == null ? 0L : headerImageWidthEmu;
    }

    public void setHeaderImageWidthEmu(Long headerImageWidthEmu) {
        this.headerImageWidthEmu = headerImageWidthEmu;
    }

    public Long getHeaderImageHeightEmu() {
        return headerImageHeightEmu == null ? 0L : headerImageHeightEmu;
    }

    public void setHeaderImageHeightEmu(Long headerImageHeightEmu) {
        this.headerImageHeightEmu = headerImageHeightEmu;
    }

    public String getFontPath() {
        return fontPath == null ? "" : fontPath;
    }

    public void setFontPath(String fontPath) {
        this.fontPath = fontPath;
    }
}
