package com.example.wordtopdf.docx;

import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.io.TempDir;
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

        boolean hasImageRel = main.getRelationshipsPart().getRelationships().getRelationship().stream()
                .map(Relationship::getType)
                .anyMatch(Namespaces.IMAGE::equals);
        assertThat(hasImageRel).isTrue();
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

        double w = 5.0;  // 5 cm
        double h = 2.0;  // 2 cm
        HeaderImageInserter.insertAtTop(pkg, png, "logo", w, h);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isEqualTo(Math.round(5.0 * 360000));
        assertThat(inline.getExtent().getCy()).isEqualTo(Math.round(2.0 * 360000));
    }

    @Test
    void appliesWidthOnlyKeepsAspectRatio() throws Exception {
        byte[] png = makePng(40, 30);

        WordprocessingMLPackage ref = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(ref, png, "logo", 0, 0);
        long origCx = firstInline(ref).getExtent().getCx();
        long origCy = firstInline(ref).getExtent().getCy();

        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 5.0, 0);  // 宽 5cm

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isEqualTo(Math.round(5.0 * 360000));
        long expectedCy = Math.round((double) origCy * (5.0 * 360000) / origCx);
        assertThat(inline.getExtent().getCy()).isEqualTo(expectedCy);
    }

    @Test
    void appliesHeightOnlyKeepsAspectRatio() throws Exception {
        byte[] png = makePng(40, 30);

        WordprocessingMLPackage ref = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(ref, png, "logo", 0, 0);
        long origCx = firstInline(ref).getExtent().getCx();
        long origCy = firstInline(ref).getExtent().getCy();

        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 0, 2.0);  // 高 2cm

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCy()).isEqualTo(Math.round(2.0 * 360000));
        long expectedCx = Math.round((double) origCx * (2.0 * 360000) / origCy);
        assertThat(inline.getExtent().getCx()).isEqualTo(expectedCx);
    }

    @Test
    void preservesOriginalSizeWhenBothZero() throws Exception {
        byte[] png = makePng(40, 30);

        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("body");
        HeaderImageInserter.insertAtTop(pkg, png, "logo", 0, 0);

        var inline = firstInline(pkg);
        assertThat(inline.getExtent().getCx()).isPositive();
        assertThat(inline.getExtent().getCy()).isPositive();
    }

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
