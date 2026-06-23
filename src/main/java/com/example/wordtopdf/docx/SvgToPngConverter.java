package com.example.wordtopdf.docx;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 把 SVG 字节流转成 PNG 字节流。
 *
 * <p>docx4j 的 {@code BinaryPartAbstractImage} 不直接支持 SVG 插入（Word 老版本对
 * SVG 支持不一致）。最稳的做法是用 Apache Batik 把 SVG 渲染成 PNG，再走 PNG
 * 插入流程。Batik 已经通过 Apache FOP（docx4j-export-fo 的传递依赖）在 classpath 里了，
 * 不用额外引入。</p>
 *
 * <p>用法：在 {@code HeaderImageInserter.insertAtTop} 之前调用
 * {@link #convertIfSvg(byte[])}，把可能的 SVG 字节流转成 PNG。非 SVG 字节原样返回，
 * 所以同一个调用点能同时处理 PNG/JPEG/SVG。</p>
 */
public final class SvgToPngConverter {

    private SvgToPngConverter() {
    }

    /**
     * 如果输入是 SVG，转成 PNG；否则原样返回。
     *
     * @param imageBytes 图片原始字节
     * @return PNG 字节（如果是 SVG）；原字节（如果不是 SVG）
     * @throws RuntimeException SVG 转 PNG 失败
     */
    public static byte[] convertIfSvg(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }
        if (!isSvg(imageBytes)) {
            return imageBytes;
        }
        try {
            return convert(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("SVG → PNG conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * 通过文件头判断字节流是不是 SVG。
     * <p>SVG 文件通常是 {@code <?xml ...?>} 开头后面带 {@code <svg}，
     * 或者直接 {@code <svg ...>} 开头。检查前 512 字节就够。</p>
     */
    private static boolean isSvg(byte[] bytes) {
        if (bytes.length < 5) {
            return false;
        }
        String head = new String(bytes, 0, Math.min(512, bytes.length), StandardCharsets.UTF_8).trim().toLowerCase();
        return head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"));
    }

    /**
     * 用 Batik 的 PNGTranscoder 把 SVG 转成 PNG。
     * <p>不指定输出尺寸 → Batik 用 SVG 自带的 viewBox / width / height 决定 PNG 像素尺寸。
     * 如果 SVG 没声明尺寸，Batik 会按默认 DPI（96）渲染。</p>
     */
    private static byte[] convert(byte[] svgBytes) throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();
        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(out);
        transcoder.transcode(input, output);
        out.flush();
        return out.toByteArray();
    }
}
