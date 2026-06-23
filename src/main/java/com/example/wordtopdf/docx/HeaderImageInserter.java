package com.example.wordtopdf.docx;

import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;

/**
 * 在文档最顶部插入一张固定图片（比如 Logo）。
 *
 * <p>实现方式：把图片包成一个段落（{@code <w:p>}），插入到 {@link MainDocumentPart}
 * 的内容列表的最前面（index 0），原来的所有内容自动往下挪。图片作为 inline drawing
 * （嵌入式图片，跟文字一起流动），不是浮动图片（floating）。</p>
 */
public final class HeaderImageInserter {

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
     *
     * @param pkg        目标 docx 包
     * @param imageBytes 图片原始字节（支持 PNG / JPEG / GIF / BMP）
     * @param altText    无障碍文字，也作为 docx 内部关系 ID 的提示名
     * @param widthEmu   目标宽度（EMU 单位，1 cm ≈ 360000 EMU）。传 0 表示不指定
     * @param heightEmu  目标高度（EMU 单位）。传 0 表示不指定
     * @throws Exception docx4j 的图片处理失败会抛通用 Exception
     */
    public static void insertAtTop(WordprocessingMLPackage pkg,
                                   byte[] imageBytes,
                                   String altText,
                                   long widthEmu,
                                   long heightEmu) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes must not be empty");
        }

        // 把图片字节注册到 docx 包里。docx 是个 zip，图片会作为一个独立的 part
        // （类似 word/media/image1.png）存进去，然后正文 XML 里通过关系 ID 引用它。
        BinaryPartAbstractImage imagePart =
                BinaryPartAbstractImage.createImagePart(pkg, imageBytes);

        // 创建一个 inline drawing 对象。createImageInline 内部会：
        // - 给图片生成一个 docx 内部关系（relationship）
        // - 创建 <wp:inline> 元素，里面带图片的原始尺寸、alt 文字、对齐方式等
        // 参数：filenameHint / altText / id1（关系ID提示）/ id2（绘图ID）/ 标志位
        Inline inline = imagePart.createImageInline(altText, altText, 0, 1, false);

        applyDimensions(inline, widthEmu, heightEmu);

        // 构造 XML 结构：<w:p> > <w:r> > <w:drawing> > <wp:inline>
        // 这就是 docx 里图片段落的标准结构
        Drawing drawing = new Drawing();
        drawing.getAnchorOrInline().add(inline);

        R run = new R();
        run.getContent().add(drawing);

        P paragraph = new P();
        paragraph.getContent().add(run);

        // 把这个段落插到内容列表最前面，原来的内容自动往后挪
        pkg.getMainDocumentPart().getContent().add(0, paragraph);
    }

    /**
     * 按规则应用目标尺寸，保持原始宽高比。
     */
    private static void applyDimensions(Inline inline, long widthEmu, long heightEmu) {
        // inline 创建时已经填好图片原始尺寸，从这里读出来
        long origCx = inline.getExtent().getCx();
        long origCy = inline.getExtent().getCy();

        if (widthEmu > 0 && heightEmu > 0) {
            // 两个都指定：直接用（调用方自己保证比例，否则图片会拉伸）
            inline.getExtent().setCx(widthEmu);
            inline.getExtent().setCy(heightEmu);
        } else if (widthEmu > 0) {
            // 只指定宽度：高度按原始宽高比缩放
            // 原始比例 origCy/origCx，新高度 = origCy * widthEmu / origCx
            inline.getExtent().setCx(widthEmu);
            if (origCx > 0) {
                inline.getExtent().setCy(origCy * widthEmu / origCx);
            }
        } else if (heightEmu > 0) {
            // 只指定高度：宽度按原始宽高比缩放
            inline.getExtent().setCy(heightEmu);
            if (origCy > 0) {
                inline.getExtent().setCx(origCx * heightEmu / origCy);
            }
        }
        // 两个都是 0：保持 inline 里默认的原始尺寸，不动
    }
}
