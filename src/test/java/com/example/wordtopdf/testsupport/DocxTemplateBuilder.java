package com.example.wordtopdf.testsupport;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;

/**
 * Builds small docx templates on the fly for tests. Avoids committing a
 * binary .docx to source control.
 */
public final class DocxTemplateBuilder {

    private DocxTemplateBuilder() {
    }

    /**
     * @param lines each line becomes its own paragraph
     */
    public static WordprocessingMLPackage build(String... lines) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        for (String line : lines) {
            main.getContent().add(paragraphOf(line));
        }
        return pkg;
    }

    /**
     * Build a paragraph where each segment is its own run. Useful for
     * asserting that placeholder replacement still works when Word splits
     * a placeholder across multiple {@code <w:r>} elements.
     */
    public static WordprocessingMLPackage buildSplit(String[] segmentsPerLine) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        MainDocumentPart main = pkg.getMainDocumentPart();
        P p = new P();
        for (String segment : segmentsPerLine) {
            R r = new R();
            Text t = new Text();
            t.setValue(segment);
            t.setSpace("preserve");
            r.getContent().add(t);
            p.getContent().add(r);
        }
        main.getContent().add(p);
        return pkg;
    }

    private static P paragraphOf(String text) {
        P p = new P();
        R r = new R();
        Text t = new Text();
        t.setValue(text);
        t.setSpace("preserve");
        r.getContent().add(t);
        p.getContent().add(r);
        return p;
    }
}
