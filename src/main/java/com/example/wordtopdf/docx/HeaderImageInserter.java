package com.example.wordtopdf.docx;

import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在文档最顶部插入一张固定图片（比如 Logo）。
 *
 * <p>docx4j 11.x 原生支持 PNG / JPEG / GIF / SVG，直接传原始字节即可。</p>
 */
public final class HeaderImageInserter {

    private static final Logger log = LoggerFactory.getLogger(HeaderImageInserter.class);

    private HeaderImageInserter() {
    }

    /**
     * 在文档顶部插入图片。
     *
     * <p>尺寸规则（保持图片原始宽高比）：</p>
     * <ul>
     *   <li>宽和高都传（&gt; 0）→ 同时使用传入值（注意：会破坏比例，除非你算好）</li>
     *   <li>只传宽度 → 高度按原始宽高比自动算</li>
     *   <li>只传高度 → 宽度按原始宽高比自动算</li>
     *   <li>都不传（都是 0）→ 保持图片原始尺寸</li>
     * </ul>
     */
    public static void insertAtTop(WordprocessingMLPackage pkg,
                                   byte[] imageBytes,
                                   String altText,
                                   long widthEmu,
                                   long heightEmu) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes must not be empty");
        }

        BinaryPartAbstractImage imagePart =
                BinaryPartAbstractImage.createImagePart(pkg, imageBytes);

        Inline inline = imagePart.createImageInline(altText, altText, 0, 1, false);

        // 打印原始尺寸（调试用）
        long origCx = inline.getExtent().getCx();
        long origCy = inline.getExtent().getCy();
        log.info("Image original size: cx={}, cy={} EMU (≈{}×{} cm)",
                origCx, origCy, origCx / 360000, origCy / 360000);

        applyDimensions(inline, widthEmu, heightEmu, origCx, origCy);

        // 打印最终尺寸
        log.info("Image final size: cx={}, cy={} EMU (≈{}×{} cm)",
                inline.getExtent().getCx(), inline.getExtent().getCy(),
                inline.getExtent().getCx() / 360000, inline.getExtent().getCy() / 360000);

        Drawing drawing = new Drawing();
        drawing.getAnchorOrInline().add(inline);

        R run = new R();
        run.getContent().add(drawing);

        P paragraph = new P();
        paragraph.getContent().add(run);

        pkg.getMainDocumentPart().getContent().add(0, paragraph);
    }

    /**
     * 按规则应用目标尺寸，保持原始宽高比。
     */
    private static void applyDimensions(Inline inline, long widthEmu, long heightEmu,
                                        long origCx, long origCy) {
        if (widthEmu > 0 && heightEmu > 0) {
            // 两个都指定：直接用
            inline.getExtent().setCx(widthEmu);
            inline.getExtent().setCy(heightEmu);
        } else if (widthEmu > 0) {
            // 只指定宽度
            inline.getExtent().setCx(widthEmu);
            if (origCx > 0) {
                inline.getExtent().setCy(Math.round((double) origCy * widthEmu / origCx));
            }
        } else if (heightEmu > 0) {
            // 只指定高度
            inline.getExtent().setCy(heightEmu);
            if (origCy > 0) {
                inline.getExtent().setCx(Math.round((double) origCx * heightEmu / origCy));
            }
        }
        // 两个都是 0：保持原始尺寸
    }
}
