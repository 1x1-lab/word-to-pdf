package com.example.wordtopdf.service;

import com.example.wordtopdf.config.ConvertProperties;
import com.example.wordtopdf.config.FontInitializer;
import com.example.wordtopdf.docx.SimplePdfExporter;
import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.docx4j.XmlUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertServiceTest {

    @Test
    void endToEndConversionProducesPdf() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "Title: ${title}",
                "Author: ${author}"
        );
        ByteArrayOutputStream tmplOut = new ByteArrayOutputStream();
        pkg.save(tmplOut);

        ConvertProperties props = new ConvertProperties();
        props.setHeaderImage("inmemory:logo");
        props.setFontPath("");
        ConvertService service = new ConvertService(
                new InMemoryResourceLoader(makeLogo()),
                props,
                new SimplePdfExporter(new FontInitializer(props))
        );

        byte[] pdf = service.convert(tmplOut.toByteArray(),
                Map.of("title", "Hello", "author", "World"));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(5, pdf.length))).startsWith("%PDF");
    }

    @Test
    void worksWithoutHeaderImage() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("Hello ${name}");
        ByteArrayOutputStream tmplOut = new ByteArrayOutputStream();
        pkg.save(tmplOut);

        ConvertProperties props = new ConvertProperties();
        props.setHeaderImage("inmemory:missing");
        props.setFontPath("");
        ConvertService service = new ConvertService(
                new InMemoryResourceLoader(null),
                props,
                new SimplePdfExporter(new FontInitializer(props))
        );

        byte[] pdf = service.convert(tmplOut.toByteArray(), Map.of("name", "Bob"));
        assertThat(pdf).isNotEmpty();
    }

    private static byte[] makeLogo() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static final class InMemoryResourceLoader implements ResourceLoader {
        private final byte[] payload;

        InMemoryResourceLoader(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public Resource getResource(String location) {
            if (payload == null) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }

                    @Override
                    public InputStream getInputStream() {
                        throw new IllegalStateException("no payload");
                    }
                };
            }
            return new ByteArrayResource(payload) {
                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(payload);
                }
            };
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
