package com.example.wordtopdf.docx;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SvgToPngConverterTest {

    /**
     * 简单的 SVG（200x100 蓝底白字矩形）。
     */
    private static final byte[] SVG = (
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"100\" viewBox=\"0 0 200 100\">" +
            "  <rect width=\"200\" height=\"100\" fill=\"#2563eb\"/>" +
            "  <text x=\"100\" y=\"55\" font-family=\"sans-serif\" font-size=\"20\" " +
            "        fill=\"white\" text-anchor=\"middle\">Logo</text>" +
            "</svg>"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    void detectsAndConvertsSvg() throws Exception {
        byte[] result = SvgToPngConverter.convertIfSvg(SVG);

        // 结果不是原字节（转换发生了）
        assertThat(result).isNotSameAs(SVG);
        assertThat(result).isNotEmpty();

        // 是合法 PNG
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(200);
        assertThat(img.getHeight()).isEqualTo(100);
    }

    @Test
    void passthroughForNonSvgBytes() {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG 文件头
                0x00, 0x01, 0x02, 0x03
        };
        byte[] result = SvgToPngConverter.convertIfSvg(pngBytes);

        // 非 SVG 原样返回（同一个引用）
        assertThat(result).isSameAs(pngBytes);
    }

    @Test
    void handlesEmptyInput() {
        assertThat(SvgToPngConverter.convertIfSvg(null)).isNull();
        assertThat(SvgToPngConverter.convertIfSvg(new byte[0])).isEmpty();
    }

    @Test
    void handlesSvgStartingDirectlyWithSvgTag() {
        byte[] svgNoXmlDecl = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"50\" height=\"50\"></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] result = SvgToPngConverter.convertIfSvg(svgNoXmlDecl);
        assertThat(result).isNotSameAs(svgNoXmlDecl);
        assertThat(result.length).isGreaterThan(8);
    }
}
