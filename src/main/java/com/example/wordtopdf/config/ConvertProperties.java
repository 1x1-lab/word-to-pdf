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
 *     header-image-width: 0        # cm，0 = 不指定
 *     header-image-height: 1.67    # cm，宽度自动按比例
 *     font-path: /opt/app/fonts/font.otf
 * </pre>
 */
@ConfigurationProperties(prefix = "app.convert")
public class ConvertProperties {

    /** 头图位置，支持 classpath: / file: / http(s): 前缀 */
    private String headerImage;

    /** 头图宽度（cm）；0/null 表示不指定，由高度按比例算 */
    private Double headerImageWidth;

    /** 头图高度（cm）；0/null 表示不指定，由宽度按比例算 */
    private Double headerImageHeight;

    /** 字体文件完整路径（文件系统路径，绝对或相对） */
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

    public Double getHeaderImageWidth() {
        return headerImageWidth == null ? 0.0 : headerImageWidth;
    }

    public void setHeaderImageWidth(Double headerImageWidth) {
        this.headerImageWidth = headerImageWidth;
    }

    public Double getHeaderImageHeight() {
        return headerImageHeight == null ? 0.0 : headerImageHeight;
    }

    public void setHeaderImageHeight(Double headerImageHeight) {
        this.headerImageHeight = headerImageHeight;
    }

    public String getFontPath() {
        return fontPath == null ? "" : fontPath;
    }

    public void setFontPath(String fontPath) {
        this.fontPath = fontPath;
    }
}
