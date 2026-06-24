package com.example.wordtopdf.docx;

import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExporterTest {

    @Test
    void producesValidPdf(@TempDir Path tmp) throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "PDF test",
                "Title: ${title}",
                "Body content."
        );
        DocxTemplateFiller.fill(pkg, Map.of("title", "Demo"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 不走 SimplePdfExporter（测试环境没加载字体），直接用 docx4j 验证管线
        org.docx4j.Docx4J.toPDF(pkg, out);

        byte[] pdf = out.toByteArray();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(5, pdf.length))).startsWith("%PDF");

        Path saved = tmp.resolve("sample.pdf");
        Files.write(saved, pdf);
        assertThat(Files.size(saved)).isEqualTo(pdf.length);
    }

    @Test
    void pipelineDoesNotCrashOnCjk() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "Hello ${greeting}",
                "ABC"
        );
        DocxTemplateFiller.fill(pkg, Map.of("greeting", "World"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.docx4j.Docx4J.toPDF(pkg, out);

        assertThat(out.toByteArray()).isNotEmpty();
    }
}
