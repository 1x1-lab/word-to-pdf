package com.example.wordtopdf.docx;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SVG → PNG 转换器。docx4j 的 createImagePart 不直接支持 SVG，需要先转成 PNG。
 */
public final class SvgToPngConverter {

    private static final Logger log = LoggerFactory.getLogger(SvgToPngConverter.class);

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
        PNGTranscoder transcoder = new PNGTranscoder();
        TranscoderInput input = new TranscoderInput(new ByteArrayInputStream(svgBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(out);
        transcoder.transcode(input, output);
        out.flush();
        log.info("SVG converted to PNG ({} bytes → {} bytes)", svgBytes.length, out.size());
        return out.toByteArray();
    }
}
