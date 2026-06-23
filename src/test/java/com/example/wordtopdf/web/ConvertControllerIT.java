package com.example.wordtopdf.web;

import com.example.wordtopdf.testsupport.DocxTemplateBuilder;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 转换接口的集成测试（MockMvc）。
 *
 * <p>变量替换逻辑由 office-stamper 负责替换（不再自研），所以这里只断言
 * HTTP 状态码和 Content-Type，不再深挖 docx 内部 XML 结构。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConvertControllerIT {

    @Autowired
    MockMvc mvc;

    @Test
    void convertsTemplateAndReturnsPdf() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build(
                "Hello ${name}",
                "Welcome to ${product}"
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pkg.save(baos);

        MockMultipartFile template = new MockMultipartFile(
                "template", "demo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray()
        );

        mvc.perform(multipart("/api/convert")
                        .file(template)
                        .param("variables", "{\"name\":\"Alice\",\"product\":\"Word-to-PDF POC\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("converted.pdf")));
    }

    @Test
    void rejectsMissingTemplate() throws Exception {
        mvc.perform(multipart("/api/convert"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toleratesMalformedVariablesJson() throws Exception {
        WordprocessingMLPackage pkg = DocxTemplateBuilder.build("static content");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pkg.save(baos);
        MockMultipartFile template = new MockMultipartFile(
                "template", "demo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray()
        );

        // JSON 格式错应该被吞掉（按"无变量"处理），转换仍然成功
        mvc.perform(multipart("/api/convert")
                        .file(template)
                        .param("variables", "{not-json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }
}
