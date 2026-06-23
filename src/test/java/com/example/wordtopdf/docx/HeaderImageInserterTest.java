package com.example.wordtopdf.docx;

import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderImageInserterTest {

    @Test
    void insertsImageAsFirstParagraph(@TempDir Path tmp) throws Exception {
        byte[] png = makePng(40, 30);
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body1", "body2");

        HeaderImageInserter.insertAtTop(pkg, png, "logo", 0, 0);

        MainDocumentPart main = pkg.getMainDocumentPart();
        List<Object> content = main.getContent();
        assertThat(content).hasSize(3);
        assertThat(content.get(0)).isInstanceOf(P.class);

        P firstParagraph = (P) content.get(0);
        R run = (R) firstParagraph.getContent().get(0);
        assertThat(run.getContent()).anyMatch(Drawing.class::isInstance);

        // Image relationship should be added to the package.
        boolean hasImageRel = main.getRelationshipsPart().getRelationships().getRelationship().stream()
                .map(Relationship::getType)
                .anyMatch(Namespaces.IMAGE::equals);
        assertThat(hasImageRel).isTrue();

        // Body content preserved below.
        assertThat(main.getContent().get(1)).isInstanceOf(P.class);
    }

    @Test
    void rejectsEmptyImageBytes() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("x");
        assertThatThrownBy(() -> HeaderImageInserter.insertAtTop(pkg, new byte[0], "x", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesCustomDimensions() throws Exception {
        byte[] png = makePng(10, 10);
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");

        long w = 1234L;
        long h = 5678L;
        HeaderImageInserter.insertAtTop(pkg, png, "logo", w, h);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isEqualTo(w);
        assertThat(inline.getExtent().getCy()).isEqualTo(h);
    }

    /**
     * 只设宽度，高度应该按原始宽高比自动缩放，保持图片比例。
     */
    @Test
    void appliesWidthOnlyKeepsAspectRatio() throws Exception {
        // 40x30 px → 原始比例 4:3
        byte[] png = makePng(40, 30);

        // 先不设尺寸，读原始 cx/cy
        WordprocessingMLPackage ref = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(ref, png, "logo", 0L, 0L);
        long origCx = firstInline(ref).getExtent().getCx();
        long origCy = firstInline(ref).getExtent().getCy();
        assertThat(origCx).isPositive();
        assertThat(origCy).isPositive();

        // 只设宽度 = 400000 EMU
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 400000L, 0L);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isEqualTo(400000L);
        // 高度按比例：origCy * 400000 / origCx
        long expectedCy = origCy * 400000L / origCx;
        assertThat(inline.getExtent().getCy()).isEqualTo(expectedCy);
    }

    /**
     * 只设高度，宽度应该按原始宽高比自动缩放。
     */
    @Test
    void appliesHeightOnlyKeepsAspectRatio() throws Exception {
        byte[] png = makePng(40, 30);

        WordprocessingMLPackage ref = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(ref, png, "logo", 0L, 0L);
        long origCx = firstInline(ref).getExtent().getCx();
        long origCy = firstInline(ref).getExtent().getCy();

        // 只设高度 = 300000 EMU
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 0L, 300000L);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCy()).isEqualTo(300000L);
        long expectedCx = origCx * 300000L / origCy;
        assertThat(inline.getExtent().getCx()).isEqualTo(expectedCx);
    }

    /**
     * 两个都不设（都 0），保持原始尺寸。
     */
    @Test
    void preservesOriginalSizeWhenBothZero() throws Exception {
        byte[] png = makePng(40, 30);

        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 0L, 0L);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isPositive();
        assertThat(inline.getExtent().getCy()).isPositive();
    }

    /** 取段落里第一个 inline drawing，方便断言尺寸。 */
    private static org.docx4j.dml.wordprocessingDrawing.Inline firstInline(WordprocessingMLPackage pkg) {
        P first = (P) pkg.getMainDocumentPart().getContent().get(0);
        R run = (R) first.getContent().get(0);
        Drawing drawing = (Drawing) run.getContent().stream()
                .filter(Drawing.class::isInstance)
                .findFirst()
                .orElseThrow();
        return (org.docx4j.dml.wordprocessingDrawing.Inline) drawing.getAnchorOrInline().get(0);
    }

    private static byte[] makePng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
