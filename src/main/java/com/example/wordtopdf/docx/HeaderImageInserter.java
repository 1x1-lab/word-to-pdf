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

    /** 1 cm = 360000 EMU */
    private static final long EMU_PER_CM = 360000L;

    /**
     * 在文档顶部插入图片。
     *
     * <p>尺寸用 cm，内部转成 EMU。尺寸规则（保持原始宽高比）：</p>
     * <ul>
     *   <li>宽和高都传（&gt; 0）→ 同时使用传入值</li>
     *   <li>只传宽度 → 高度按原始宽高比自动算</li>
     *   <li>只传高度 → 宽度按原始宽高比自动算</li>
     *   <li>都不传（都是 0）→ 保持图片原始尺寸</li>
     * </ul>
     */
    public static void insertAtTop(WordprocessingMLPackage pkg,
                                   byte[] imageBytes,
                                   String altText,
                                   double widthCm,
                                   double heightCm) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes must not be empty");
        }

        BinaryPartAbstractImage imagePart =
                BinaryPartAbstractImage.createImagePart(pkg, imageBytes);

        Inline inline = imagePart.createImageInline(altText, altText, 0, 1, false);

        long origCx = inline.getExtent().getCx();
        long origCy = inline.getExtent().getCy();
        log.info("Image original: {:.2f} x {:.2f} cm", origCx / (double) EMU_PER_CM, origCy / (double) EMU_PER_CM);

        long widthEmu = cmToEmu(widthCm);
        long heightEmu = cmToEmu(heightCm);
        applyDimensions(inline, widthEmu, heightEmu, origCx, origCy);

        log.info("Image final: {:.2f} x {:.2f} cm",
                inline.getExtent().getCx() / (double) EMU_PER_CM,
                inline.getExtent().getCy() / (double) EMU_PER_CM);

        Drawing drawing = new Drawing();
        drawing.getAnchorOrInline().add(inline);

        R run = new R();
        run.getContent().add(drawing);

        P paragraph = new P();
        paragraph.getContent().add(run);

        pkg.getMainDocumentPart().getContent().add(0, paragraph);
    }

    /** cm → EMU */
    private static long cmToEmu(double cm) {
        return Math.round(cm * EMU_PER_CM);
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
