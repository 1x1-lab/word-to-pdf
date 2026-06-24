package com.example.wordtopdf.docx;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SVG → PNG 转换器。
 *
 * <p>docx4j 的 {@code createImagePart} 对 SVG 支持不稳定，转 PDF 时容易丢图或尺寸错乱，
 * 所以先转成 PNG 再插入。转换时尽量读取 SVG 自身的 {@code width/height} 或
 * {@code viewBox}，按原始像素尺寸输出，避免 Batik 用默认 400×400 导致后续缩放失真。</p>
 */
public final class SvgToPngConverter {

    private static final Logger log = LoggerFactory.getLogger(SvgToPngConverter.class);

    /** SVG 没有声明尺寸时的兜底输出大小（像素）。 */
    private static final int DEFAULT_SIZE = 800;

    private SvgToPngConverter() {
    }

    /**
     * 如果输入是 SVG，转成 PNG；否则原样返回。
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
            throw new RuntimeException("SVG conversion failed: " + e.getMessage(), e);
        }
    }

    private static boolean isSvg(byte[] bytes) {
        if (bytes.length < 5) return false;
        String head = new String(bytes, 0, Math.min(512, bytes.length), StandardCharsets.UTF_8).trim().toLowerCase();
        return head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"));
    }

    private static byte[] convert(byte[] svgBytes) throws Exception {
        Dimension size = extractSvgSize(svgBytes);

        PNGTranscoder transcoder = new PNGTranscoder();
        if (size != null && size.width > 0 && size.height > 0) {
            // 按 viewBox/width 的真实比例，只定宽度，让 Batik 自动算高度，
            // 避免同时设宽高导致按 preserveAspectRatio 加白边。
            int targetWidth = Math.max(size.width, DEFAULT_SIZE);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) targetWidth);
            log.info("SVG transcoding to width {} px (aspect ratio {}:{})", targetWidth, size.width, size.height);
        } else {
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) DEFAULT_SIZE);
            log.warn("SVG has no explicit size; transcoding to {} px wide", DEFAULT_SIZE);
        }

        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(out);
        transcoder.transcode(input, output);
        out.flush();
        log.info("SVG converted to PNG ({} bytes → {} bytes)", svgBytes.length, out.size());
        return out.toByteArray();
    }

    /**
     * 从 SVG 字节里解析原始像素尺寸。
     *
     * <p>优先读 {@code viewBox} 的宽高（它定义了内容的真实比例），
     * 没有 viewBox 再读 {@code width}/{@code height}。
     * 只支持纯数字或带 px 单位的长度。</p>
     */
    private static Dimension extractSvgSize(byte[] svgBytes) {
        String svg = new String(svgBytes, StandardCharsets.UTF_8);

        String viewBox = extractAttribute(svg, "viewBox");
        if (viewBox != null && !viewBox.isBlank()) {
            String[] parts = viewBox.trim().split("[,\\s]+");
            if (parts.length == 4) {
                try {
                    float w = Float.parseFloat(parts[2]);
                    float h = Float.parseFloat(parts[3]);
                    if (w > 0 && h > 0) {
                        return new Dimension((int) w, (int) h);
                    }
                } catch (NumberFormatException e) {
                    // ignore malformed viewBox
                }
            }
        }

        Float width = parseLength(extractAttribute(svg, "width"));
        Float height = parseLength(extractAttribute(svg, "height"));
        if (width != null && height != null && width > 0 && height > 0) {
            return new Dimension(width.intValue(), height.intValue());
        }
        return null;
    }

    private static String extractAttribute(String svg, String attrName) {
        // 匹配 <svg ... attr="value" ...> 或 attr='value'
        Pattern p = Pattern.compile("<svg[^>]+\\s" + attrName + "=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(svg);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Float parseLength(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toLowerCase();
        // 去掉 px 单位
        if (v.endsWith("px")) {
            v = v.substring(0, v.length() - 2).trim();
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
