package com.example.wordtopdf.docx;

import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 docx 模板里的 <code>${key}</code> 占位符填充成给定 map 里对应的值。
 *
 * <p><b>核心难点</b>：Word 会把一段连续的文字拆成多个 {@code <w:r>}（run）节点。
 * 比如用户在 Word 里改了一段文字一半的格式（粗体、颜色等），Word 就把这段文字
 * 切成多个 run 各自带不同格式。更糟糕的是，{@code ${title}} 这种占位符可能被
 * 拆成 {@code $}、{@code {title}}、{@code }} 三个 run，单纯按 run 做正则根本匹配不到。</p>
 *
 * <p><b>本类的做法</b>：</p>
 * <ol>
 *   <li>遍历文档里每个段落（{@code <w:p>}）</li>
 *   <li>把段落里所有 run 的可见文字拼成一个完整字符串</li>
 *   <li>对这个完整字符串做正则替换</li>
 *   <li>如果发生了替换，把替换后的字符串写回第一个 run（保留它的格式），
 *       其他 run 的文字清空，这样段落的渲染效果就是替换后的完整内容</li>
 * </ol>
 *
 * <p><b>取舍</b>：如果一个段落里混合了多种 run 格式（比如一行字一半粗体一半正常），
 * 替换后整个段落会统一用第一个 run 的格式，丢失 run 级格式。<b>段落级格式</b>
 * （对齐、缩进等）和 <b>run 属性</b>（字号、粗体、字体、颜色）都保留。</p>
 *
 * <p><b>另一个坑：save/load 往返</b>：docx4j 在把 docx 反序列化时，body 的直接
 * 子元素（{@code P}、{@code Tbl}）以及 run 里的 {@code Text} 经常被包在
 * {@code JAXBElement} 里。直接用 {@code instanceof P} 判断会失败。所有判断前
 * 必须先调 {@link XmlUtils#unwrap} 把 JAXBElement 解开。</p>
 */
public final class DocxTemplateFiller {

    /**
     * 占位符正则：匹配 {@code ${key}}。
     * <p>key 必须以字母或下划线开头，后续可以是字母、数字、下划线、点（支持
     * {@code ${user.name}} 这种嵌套写法）。</p>
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][\\w.]*)}");

    private DocxTemplateFiller() {
    }

    /**
     * 把整份 docx 里所有 {@code ${key}} 占位符填充为 map 里对应的值。
     *
     * @param pkg  已经加载的 docx 包
     * @param vars 键值对，key 对应占位符里的名字，value 会被 {@link String#valueOf} 转字符串
     * @return 实际填充的次数（用于日志和诊断）
     */
    public static int fill(WordprocessingMLPackage pkg, Map<String, ?> vars) {
        if (vars == null || vars.isEmpty()) {
            return 0;
        }
        MainDocumentPart main = pkg.getMainDocumentPart();
        // 用长度为 1 的数组当可变整数用，避免内部方法签名上加一个 int 参数
        int[] count = {0};

        // 注意：MainDocumentPart 不是 ContentAccessor，所以要从它的 getContent()
        // 直接开始遍历，不能把 main 本身传给 walk。每个元素可能被 JAXBElement 包着，
        // 要先 unwrap。
        for (Object el : main.getContent()) {
            walk(XmlUtils.unwrap(el), vars, count);
        }
        return count[0];
    }

    /**
     * 递归遍历 docx 的内容树。
     * <p>遇到段落就替换，遇到容器（ContentAccessor，比如 Table、TableCell）就继续往下钻。</p>
     */
    private static void walk(Object el, Map<String, ?> vars, int[] count) {
        Object node = XmlUtils.unwrap(el);
        // 注意：这里没用 JDK 16 的 instanceof pattern matching (instanceof P p)，
        // 改回传统写法，保证 JDK 11 也能编译（项目其余部分还是用 Java 17）。
        if (node instanceof P) {
            fillParagraph((P) node, vars, count);
        } else if (node instanceof ContentAccessor) {
            // 容器节点（表格、单元格等），继续递归
            ContentAccessor ca = (ContentAccessor) node;
            for (Object child : ca.getContent()) {
                walk(child, vars, count);
            }
        }
    }

    /**
     * 处理单个段落：拼接所有 run 的文字 → 正则替换 → 写回第一个 run。
     */
    private static void fillParagraph(P p, Map<String, ?> vars, int[] count) {
        // 取出段落里所有的 run。run 可能被 JAXBElement 包着，所以用 unwrap 解开再过滤。
        // 不用 JDK 16 的 .toList()，用 Collectors.toList() 保持兼容。
        List<R> runs = p.getContent().stream()
                .map(o -> {
                    Object u = XmlUtils.unwrap(o);
                    return u instanceof R ? (R) u : null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (runs.isEmpty()) {
            return;
        }

        // 把所有 run 的 Text 内容拼起来，得到段落完整可见文字
        StringBuilder sb = new StringBuilder();
        for (R r : runs) {
            for (Object child : r.getContent()) {
                Object u = XmlUtils.unwrap(child);
                if (u instanceof Text) {
                    Text t = (Text) u;
                    sb.append(t.getValue());
                }
            }
        }
        String original = sb.toString();
        // 快速过滤：没有 "${" 的段落直接跳过，省一次正则
        if (original.indexOf("${") < 0) {
            return;
        }

        // 在拼接出来的完整文字上做正则替换
        Matcher m = PLACEHOLDER.matcher(original);
        StringBuffer replaced = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object v = vars.get(key);
            if (v == null) {
                // 变量 map 里没有这个 key：直接 continue，不调 appendReplacement。
                // 这样占位符 ${key} 会在最终文档里原样保留（用户能在 PDF 里
                // 一眼看出哪个变量漏传了）。机制：appendReplacement 只在被调用
                // 时才推进"上次匹配位置"；continue 的那一轮位置不前移，下一次
                // appendReplacement 或循环结束时的 appendTail 会把这段未处理的
                // 文本（含 ${key} 本身）原样追加回结果。
                continue;
            }
            // quoteReplacement：防止替换值里有 $ 或 \ 被当成正则元字符
            m.appendReplacement(replaced, Matcher.quoteReplacement(String.valueOf(v)));
            count[0]++;
        }
        m.appendTail(replaced);

        // 段落里没有任何变量被替换，啥都不用改
        if (count[0] == 0) {
            return;
        }

        // 把替换后的文字写回第一个 run，其他 run 的文字清空。
        // 这样视觉上看到的就是替换后的完整内容，而不是"替换值 + 原占位符"叠在一起。
        // 注意：只动 Text 节点（文字内容），run 属性 rPr（字号/粗体/颜色等）完全保留。
        String newText = replaced.toString();
        R first = runs.get(0);
        // 移除第一个 run 里所有 Text 子节点（同样要 unwrap 判断）
        first.getContent().removeIf(o -> XmlUtils.unwrap(o) instanceof Text);
        // 在 run 内容的最前面插入新 Text。这样如果有 <w:rPr>（run 属性）等其他子节点，
        // 它们的相对顺序不会被破坏。
        Text t = new Text();
        t.setValue(newText);
        // xml:space="preserve" 保留首尾空格，不然 Word 会把 " hello " 渲染成 "hello"
        t.setSpace("preserve");
        first.getContent().add(0, t);

        // 把后续 run 的文字清空，避免重复显示
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).getContent().removeIf(o -> XmlUtils.unwrap(o) instanceof Text);
        }
    }
}
