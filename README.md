# Word → PDF POC

将 `.docx` 模板转换为 PDF，支持：

1. **变量填充**：用自研的 `DocxTemplateFiller`（~100 行代码，零外部依赖）填充模板里的 `${key}` 占位符。
2. **顶部固定图片**：转换前自动在文档第一段插入一张固定 Logo（可配置尺寸与图片源）。
3. **PDF 导出**：通过 docx4j 的 FO exporter（底层 Apache FOP）输出 PDF。
4. **中文渲染**：把 docx 常见字体名（Calibri / 宋体 / 微软雅黑 …）映射到 CJK 兼容字体。
5. **前端预览**：转换完成后在浏览器内嵌 `<iframe>` 预览 PDF。

纯 Java 实现，零外部进程依赖。

---

## 版权合规（关键）

| 组件 | 协议 | 用途 |
|---|---|---|
| Spring Boot | Apache-2.0 | Web 框架 |
| docx4j (`docx4j-JAXB-ReferenceImpl` / `docx4j-export-fo`) | Apache-2.0 | 读写/操作 `.docx`，PDF 导出 |
| Apache FOP | Apache-2.0 | XSL-FO → PDF 渲染 |
| Apache POI（docx4j 传递依赖） | Apache-2.0 | OOXML 底层 |
| **DocxTemplateFiller**（项目内自研） | Apache-2.0 | `${key}` 占位符填充 |

**未使用**（版权风险）：
- `Aspose.Words` / `Spire.Office` — 商业付费
- `iText 5/7 Community` — AGPL（传染性，不适合闭源商业项目）

---

## 项目结构

```
src/main/java/com/example/wordtopdf/
├── WordToPdfApplication.java        启动类
├── config/ConvertProperties.java    @ConfigurationProperties
├── docx/
│   ├── DocxTemplateFiller.java      自研 ${key} 占位符填充（含 JAXBElement unwrap）
│   ├── HeaderImageInserter.java     在文档顶部插入图片
│   └── PdfExporter.java             docx → PDF，含字体映射
├── service/
│   └── ConvertService.java          流程编排
└── web/ConvertController.java       HTTP 端点

src/main/resources/
├── application.yml                  头图路径、尺寸配置
├── static/index.html                前端上传 + PDF 预览页面（无 CDN）
└── templates/header-logo.png        顶部固定图片（占位 Logo）

src/test/java/...                    20 个测试，全部通过
```

---

## 变量替换方案（自研 DocxTemplateFiller）

### 为什么不用第三方模板引擎

业务需求只有简单文本替换（`${key}` → 值），没有表格循环、条件区块、图片占位符等复杂需求。曾经试过几个第三方库都不太合适：

- **poi-tl**：2024-01 后 2+ 年不发新版（半停更）
- **office-stamper**：2.6+ 要 Java 21，3.x 要 Java 23（部署门槛高）
- **DocStencil**：23 stars，太新，生产风险
- **XDocReport**：API 繁琐
- **HTML 路线**：业务人员维护 Word 模板的前提就不成立

业务场景就 50 行代码可以覆盖：自研 `DocxTemplateFiller`（在 `docx/DocxTemplateFiller.java`），所有边界情况都已经在单元测试里覆盖。

### 处理的核心难点

Word 在编辑时会**把一段连续文字拆成多个 `<w:r>` 节点**（例如改了一半文字的颜色）。`${title}` 这种占位符可能被拆成 `${`、`tit`、`le}` 三个 run，按 run 单独做正则匹配会失败。

DocxTemplateFiller 的做法：
1. 遍历段落
2. **拼接段落里所有 run 的可见文字** → 完整字符串
3. 在完整字符串上做正则替换
4. 把替换结果**写回第一个 run**（保留格式），其他 run 的文字清空

边界情况全部处理：
- ✅ 跨 run 拆分（`${`、`tit`、`le}`）
- ✅ save → load 往返后 JAXBElement 包裹（用 `XmlUtils.unwrap` 解开）
- ✅ 一段里多个占位符（`${a} + ${b} = ${c}`）
- ✅ 嵌套 key（`${user.name}`）
- ✅ 漏传 key 默认**保留 `${key}` 字面值**，方便在 PDF 里直接看到漏传字段

### 漏传变量的行为

变量 map 里没传的 key，**占位符 `${key}` 完整保留在最终 PDF 里**：

```
模板：    标题：${title}  作者：${name}
传 title：标题：已传  作者：${name}     ← ${name} 保留，方便发现漏传
```

行为差异：
- 自研：保留 `${key}`（方便排查）
- poi-tl 默认：清空（看不到漏传）
- office-stamper passing()：保留 `${key}`

### 如果未来要表格循环 / 条件 / 图片

那时再换库也来得及。当前的 `ConvertService` 只依赖 `DocxTemplateFiller.fill(pkg, map)` 一个静态方法，把这个方法换掉就行。整个 pipeline 的其他部分（插头图、转 PDF、HTTP 接口）都不动。

---

## 运行

### 前置
- Java 17+（用 Java 21 实测）
- Maven 3.6+

### 启动

```bash
mvn spring-boot:run
```

打开 [http://localhost:8080](http://localhost:8080)。页面提供：
- 上传 `.docx` 模板
- 填写变量 JSON
- 转换 + 在页面内预览 PDF
- 「下载示例模板」按钮（服务端动态生成含 `${...}` 占位符的 demo docx）

### API

#### `POST /api/convert`

multipart/form-data：
- `template`: `.docx` 文件
- `variables`: JSON 字符串，如 `{"title":"hello","user.name":"alice"}`

返回 `application/pdf` 二进制流。

#### `GET /api/sample-template`

下载内置示例 docx。

---

## 配置项（`application.yml`）

```yaml
app:
  convert:
    header-image: classpath:templates/header-logo.png
    # 图片尺寸（EMU，1 cm ≈ 360000 EMU）
    # 只填一个，另一个按比例自动算；都填 0 用原始尺寸
    header-image-width-emu: 0
    header-image-height-emu: 600000   # ~1.67 cm
```

---

## 字体配置

`PdfExporter` 处理字体的策略有三个关键点：

1. **限制 docx4j 字体扫描目录**：通过 `docx4j.fonts.physical=/System/Library/Fonts/Supplemental`（macOS）。docx4j 默认的 `IdentityPlusMapper` 在初始化时会扫描目录里所有字体；macOS `/System/Library/Fonts` 根目录下几个 AAT 字体会触发内部 assertion，把 `IdentityPlusMapper` 类永久标记为 "erroneous"，之后任何 PDF 导出都崩。限定到 `Supplemental` 子目录（全是现代 OTF/TTF）避开这个问题。

2. **用 `IdentityPlusMapper` 而不是 `PassthroughFontMapper`**：前者在扫描完成后会把 docx 中声明的字体名映射到系统物理字体；后者完全不映射，FOP 找不到字体会回退到 Times-Roman，中文变方块。

3. **显式把常见 Office 字体名映射到 `Arial Unicode MS`**：`Arial Unicode MS` 存在于 `Supplemental` 目录，覆盖 CJK + 西文字符。把 `Calibri`、`Times New Roman`、`Arial`、`SimSun`、`宋体`、`微软雅黑`、`PingFang SC` 等 19 个常见字体名都映射到它，无论 docx 模板里用了什么字体名，中文都能渲染。

### 不要手工注入 `FopFactory`

曾经尝试过在 `FOSettings` 里塞一个手工构建的 `FopFactory`（带 `setConfiguration` 或 `FopConfParser`），结果**每个段落都被渲染到单独一页**（region-body 错位到页面底部）。正确做法：直接调用 `Docx4J.toPDF(pkg, out)`，让 docx4j 用它自己内部的默认 `FopFactory`。

### 切到 Linux / Windows

修改 `PdfExporter.configureFontScanDirectory()` 里的路径默认值，或启动时加 `-Ddocx4j.fonts.physical=/usr/share/fonts`。Linux 上把 `Arial Unicode MS` 换成 `Noto Sans CJK`（先确认系统装了）。

---

## 测试

```bash
mvn test
```

13 个测试：

| 类 | 覆盖 |
|---|---|
| `HeaderImageInserterTest` (6) | 插入位置、空字节校验、自定义尺寸、只设宽/高自动按比例、保持原始 |
| `DocxTemplateFillerTest` (7) | 单/多占位符、嵌套 key、未知 key 保留、跨 run 拆分、save/load 往返 |
| `PdfExporterTest` (2) | PDF 头签名 + DocxTemplateFiller 填充 + CJK 渲染不抛异常 |
| `ConvertServiceTest` (2) | 端到端 + 无头图回退 |
| `ConvertControllerIT` (3) | MockMvc 集成：上传 + 替换 + 错误处理 |

---

## 已知限制（POC 范围）

- **变量类型**：当前只支持简单文本填充。如果未来要表格循环、条件区块、图片占位符，再换 poi-tl / office-stamper 之类的模板引擎（`ConvertService` 只依赖 `DocxTemplateFiller` 一个静态方法，替换很容易）。
- **漏传变量**：占位符 `${key}` 原样保留在 PDF 里（方便发现漏传字段）。
- **换行符**：变量值里的 `\n` 默认作为普通字符显示（不会渲染成 Word 换行）。如果需要支持换行，在 `DocxTemplateFiller.fillParagraph` 里把 `\n` 替换成 `<w:br/>` 节点，~10 行代码即可。
- **字体**：默认所有文本走 Arial Unicode（统一外观）。如需保留 Word 模板原始字体名映射，需要在 `PdfExporter.buildFontMapper` 扩展。
- **PDF 转换引擎**：docx4j + FOP 对复杂排版（精细表格、SmartArt、复杂页眉页脚）渲染不如 LibreOffice 原生；质量达不到要求时建议切到 LibreOffice + jodconverter。
- **并发**：`ConvertService` 单实例，每次请求在内存中创建 `WordprocessingMLPackage`。生产环境如需限流，建议在 Controller 层加 `RateLimiter`。
