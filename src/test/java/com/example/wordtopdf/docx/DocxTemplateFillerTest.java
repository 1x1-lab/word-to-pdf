package com.example.wordtopdf.docx;

import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocxTemplateFillerTest {

    @Test
    void fillsSimplePlaceholder() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "Hello ${name}",
                "Today is ${date}"
        );

        int n = DocxTemplateFiller.fill(pkg, Map.of("name", "Alice", "date", "2026-06-22"));

        assertThat(n).isEqualTo(2);
        assertThat(paragraphText(pkg, 0)).isEqualTo("Hello Alice");
        assertThat(paragraphText(pkg, 1)).isEqualTo("Today is 2026-06-22");
    }

    @Test
    void handlesMultiplePlaceholdersPerParagraph() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "${a} + ${b} = ${c}"
        );

        int n = DocxTemplateFiller.fill(pkg, Map.of("a", "1", "b", "2", "c", "3"));

        assertThat(n).isEqualTo(3);
        assertThat(paragraphText(pkg, 0)).isEqualTo("1 + 2 = 3");
    }

    @Test
    void leavesUnknownKeyUntouched() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("Hi ${name}, ${missing}");

        int n = DocxTemplateFiller.fill(pkg, Map.of("name", "Bob"));

        assertThat(n).isEqualTo(1);
        // 找不到的 key 保留 ${missing} 原文
        assertThat(paragraphText(pkg, 0)).isEqualTo("Hi Bob, ${missing}");
    }

    @Test
    void supportsNestedKeys() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("Owner: ${user.name}");

        int n = DocxTemplateFiller.fill(pkg, Map.of("user.name", "Charlie"));

        assertThat(n).isEqualTo(1);
        assertThat(paragraphText(pkg, 0)).isEqualTo("Owner: Charlie");
    }

    @Test
    void placeholderSplitAcrossRuns() throws Exception {
        // 模拟 Word 把 "${name}" 拆成多个 run: "${", "na", "me}"
        WordprocessingMLPackage pkg = DocxTemplateBuilder.buildSplit(
                new String[]{"Hi ", "${", "na", "me", "}"}
        );

        int n = DocxTemplateFiller.fill(pkg, Map.of("name", "Dora"));

        assertThat(n).isEqualTo(1);
        assertThat(paragraphText(pkg, 0)).isEqualTo("Hi Dora");
    }

    @Test
    void noPlaceholders() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("Plain text");
        int n = DocxTemplateFiller.fill(pkg, Map.of("x", "y"));
        assertThat(n).isZero();
    }

    /**
     * 回归测试：HTTP 层会走 save → load 往返。docx4j 反序列化时会把 body 直接子元素
     * 包在 JAXBElement 里，walker 必须正确处理这种情况才能找到 P。
     */
    @Test
    void worksAfterSaveLoadRoundTrip() throws Exception {
        WordprocessingMLPackage original = DocxTemplateBuilder.build(
                "Hello ${name}", "Today is ${date}"
        );
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        original.save(baos);

        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                new java.io.ByteArrayInputStream(baos.toByteArray())
        );

        int n = DocxTemplateFiller.fill(pkg, Map.of("name", "Alice", "date", "2026-06-22"));
        assertThat(n).isEqualTo(2);
    }

    private static String paragraphText(WordprocessingMLPackage pkg, int idx) {
        MainDocumentPart main = pkg.getMainDocumentPart();
        @SuppressWarnings("unchecked")
        List<P> paragraphs = (List<P>) (List<?>) main.getContent();
        P p = paragraphs.get(idx);
        StringBuilder sb = new StringBuilder();
        for (Object c : p.getContent()) {
            Object n = XmlUtils.unwrap(c);
            if (n instanceof R) {
                R r = (R) n;
                for (Object rc : r.getContent()) {
                    Object t = XmlUtils.unwrap(rc);
                    if (t instanceof Text) {
                        sb.append(((Text) t).getValue());
                    }
                }
            }
        }
        return sb.toString();
    }
}
