package com.example.wordtopdf.web;

import com.example.wordtopdf.config.ConvertProperties;
import com.example.wordtopdf.service.ConvertService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 转换服务的 HTTP 入口。
 *
 * <p>对外提供两个端点：</p>
 * <ul>
 *   <li>{@code POST /api/convert} —— 上传 docx + 变量 JSON，返回 PDF</li>
 *   <li>{@code GET /api/sample-template} —— 下载一个内置示例 docx 模板，让用户开箱即用</li>
 * </ul>
 *
 * <p>根路径 {@code /} 由 Spring Boot 自动映射到 {@code static/index.html}，
 * 提供前端上传页面。</p>
 */
@Controller
@RequestMapping("/api")
public class ConvertController {

    private static final Logger log = LoggerFactory.getLogger(ConvertController.class);

    private final ConvertService convertService;
    private final ObjectMapper objectMapper;
    private final ConvertProperties convertProperties;

    public ConvertController(ConvertService convertService, ObjectMapper objectMapper,
                             ConvertProperties convertProperties) {
        this.convertService = convertService;
        // 用 Spring Boot 自带的 ObjectMapper，保证日期格式、Snake Case 等配置全局一致
        this.objectMapper = objectMapper;
        this.convertProperties = convertProperties;
    }

    /**
     * 转换接口：上传 docx + 变量 JSON，返回 PDF。
     *
     * <p>请求格式：multipart/form-data</p>
     * <ul>
     *   <li>{@code template}：docx 文件</li>
     *   <li>{@code variables}：JSON 字符串，如 {@code {"title":"hello","user.name":"alice"}}。
     *       可选，不传则不做替换。</li>
     * </ul>
     *
     * <p>响应：{@code application/pdf} 二进制流，带 Content-Disposition 头。</p>
     *
     * @param template  multipart 上传的 docx 文件
     * @param variables JSON 字符串形式的变量 map
     * @return 200 + PDF 字节；输入有问题返回 400；转换失败返回 422
     */
    @PostMapping(value = "/convert", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> convert(
            @RequestParam("template") MultipartFile template,
            @RequestParam(value = "variables", required = false) String variables,
            @RequestParam(value = "logo-width", required = false) Double logoWidth,
            @RequestParam(value = "logo-height", required = false) Double logoHeight,
            @RequestParam(value = "skip-logo", required = false, defaultValue = "false") boolean skipLogo) {

        // 1. 校验上传文件非空
        if (template == null || template.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 2. 解析变量 JSON（解析失败不报错，按"无变量"处理，让流程继续走）
        Map<String, Object> vars = parseVariables(variables);

        try {
            // 3. 调用 Service 做实际转换
            byte[] docx = template.getBytes();
            byte[] pdf = convertService.convert(docx, vars, logoWidth, logoHeight, skipLogo);

            // 4. 设置响应头，让浏览器把 PDF 当下载文件处理
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "converted.pdf");
            headers.setContentLength(pdf.length);
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (ConvertService.ConversionException e) {
            // 转换失败（docx 损坏、变量替换出错等）→ 422
            log.warn("Conversion rejected: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity().build();
        } catch (IOException e) {
            // 读 multipart 字节失败 → 400
            log.warn("Failed to read uploaded template: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 动态生成一个示例 docx 模板供用户下载试用。
     *
     * <p>模板里包含 {@code ${title}}、{@code ${name}} 等占位符，正好对应
     * {@code index.html} 默认显示的 JSON。用户下载后可以马上体验整个流程，
     * 不用手搓 docx。</p>
     *
     * @return 200 + docx 字节
     */
    @GetMapping(value = "/sample-template",
            produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> sampleTemplate() throws IOException {
        try {
            // 用 docx4j 程序化生成一个 docx（不依赖磁盘上的模板文件）
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            MainDocumentPart main = pkg.getMainDocumentPart();
            for (String line : List.of(
                    "标题：${title}",
                    "作者：${name}",
                    "日期：${date}",
                    "公司：${company}",
                    "",
                    "这是一个 POC 演示模板。所有 ${...} 占位符会被替换为变量 JSON 中对应的值。"
            )) {
                main.getContent().add(paragraphOf(line));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pkg.save(out);
            byte[] docx = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "demo-template.docx");
            headers.setContentLength(docx.length);
            return ResponseEntity.ok().headers(headers).body(docx);
        } catch (Exception e) {
            log.error("Failed to build sample template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 返回 Logo 尺寸的默认配置，供前端页面加载时回显到输入框。
     *
     * @return JSON，如 {@code {"width":0.0,"height":2.0}}
     */
    @GetMapping("/logo-size-defaults")
    public ResponseEntity<Map<String, Double>> logoSizeDefaults() {
        return ResponseEntity.ok(Map.of(
                "width", convertProperties.getHeaderImageWidth(),
                "height", convertProperties.getHeaderImageHeight()
        ));
    }

    /**
     * 工具方法：把一行文字包成一个段落 {@code <w:p><w:r><w:t>text</w:t></w:r></w:p>}。
     */
    private static P paragraphOf(String text) {
        P p = new P();
        R r = new R();
        Text t = new Text();
        t.setValue(text);
        // 保留首尾空格
        t.setSpace("preserve");
        r.getContent().add(t);
        p.getContent().add(r);
        return p;
    }

    /**
     * 解析 variables 参数（JSON 字符串）成 Map。
     *
     * <p>容错策略：</p>
     * <ul>
     *   <li>空 / null：返回空 Map</li>
     *   <li>解析失败：记录警告 + 返回空 Map，不抛异常</li>
     * </ul>
     */
    private Map<String, Object> parseVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(variables, new TypeReference<>() {
            });
        } catch (IOException e) {
            log.warn("Malformed variables JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
