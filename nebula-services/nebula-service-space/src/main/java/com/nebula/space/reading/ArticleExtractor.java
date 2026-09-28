package com.nebula.space.reading;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页正文提取：Readability 的简化版
 *
 * <p>先删掉脚本、导航、页眉页脚、侧栏、评论这类区域，再按「像样的段落」给父元素打分，
 * 分最高的当正文容器，按块级元素和换行切成段落。按类名删区块可能误伤（布局容器叫 layout-sidebars 之类），
 * 结果太短时不按类名删再提取一次，取长的那份。正文太短（登录墙、验证页、纯图片）算没抓到。</p>
 */
public final class ArticleExtractor {

    /**
     * 正文少于这么多字算没抓到
     */
    static final int MIN_TEXT = 140;
    /**
     * 按类名删区块后正文少于这么多字，就不删再试一次
     */
    static final int RETRY_TEXT = 500;
    static final int MAX_TEXT = 200_000;
    static final int MAX_PARAGRAPHS = 3000;
    static final int MAX_TITLE = 300;
    static final int EXCERPT_LENGTH = 120;

    /**
     * 段落分隔：块级元素边界、&lt;br&gt;、pre 里的换行
     */
    private static final char BREAK = '\u2029';

    private static final String NOISE = "script, style, noscript, template, iframe, object, embed, svg, canvas, "
            + "form, button, input, select, textarea, nav, header, footer, aside";
    private static final Pattern UNLIKELY = Pattern.compile(
            "-ad-|ad-break|banner|breadcrumb|combx|comment|community|cookie|disqus|extra|footer|gdpr|header|legends|"
                    + "menu|nav|pager|pagination|popup|recommend|related|remark|replies|rss|share|shoutbox|sidebar|"
                    + "skyscraper|social|sponsor|subscribe|supplemental|toolbar", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAYBE = Pattern.compile("and|article|body|column|content|entry|main|post|shadow|text",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u3000]+");
    /**
     * 零宽空格、零宽连接符、BOM：标题锚点和排版常带，看不见但会占下标
     */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+(?:['’-][A-Za-z0-9]+)*");

    /**
     * @param title      页面标题，取不到为 null
     * @param excerpt    摘要（页面描述或正文开头），可能为空串
     * @param paragraphs 正文段落；没抓到为 null
     * @param minutes    预计阅读分钟，没正文为 0
     */
    public record Article(String title, String excerpt, List<String> paragraphs, int minutes) {
    }

    private ArticleExtractor() {
    }

    public static Article extract(String html, String baseUri) {
        return extract(Jsoup.parse(html, baseUri));
    }

    public static Article extract(Document doc) {
        String title = title(doc);
        String description = firstMeta(doc, "meta[property=og:description]", "meta[name=description]", "meta[name=twitter:description]");
        Document untouched = doc.clone();
        List<String> paragraphs = body(doc, true);
        if (length(paragraphs) < RETRY_TEXT) {
            List<String> loose = body(untouched, false);
            if (length(loose) > length(paragraphs)) {
                paragraphs = loose;
            }
        }
        // 正文开头重复的标题去掉
        for (int i = 0; i < Math.min(3, paragraphs.size()); i++) {
            if (paragraphs.get(i).equals(title)) {
                paragraphs.remove(i);
                break;
            }
        }
        return article(title, description, paragraphs);
    }

    /**
     * 纯文本页面：空行分段
     */
    public static Article fromPlainText(String text) {
        List<String> paragraphs = new ArrayList<>();
        int total = 0;
        for (String block : text.split("\\r?\\n\\s*\\r?\\n")) {
            String p = normalize(block);
            if (p.length() < 2) {
                continue;
            }
            if (total + p.length() > MAX_TEXT || paragraphs.size() >= MAX_PARAGRAPHS) {
                break;
            }
            paragraphs.add(p);
            total += p.length();
        }
        return article(null, null, paragraphs);
    }

    /**
     * 预计阅读分钟：中文按每分钟 400 字，英文按每分钟 220 词
     */
    static int minutes(List<String> paragraphs) {
        long han = 0;
        long words = 0;
        for (String p : paragraphs) {
            han += HAN.matcher(p).results().count();
            words += WORD.matcher(p).results().count();
        }
        return (int) Math.max(1, Math.ceil(han / 400.0 + words / 220.0));
    }

    /**
     * 按字符截断，不切断代理对
     */
    static String cut(String s, int max) {
        if (s == null || s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max));
    }

    // ----------------------------------------------------------------- 内部

    private static List<String> body(Document doc, boolean stripUnlikely) {
        clean(doc, stripUnlikely);
        return paragraphs(pickRoot(doc));
    }

    private static int length(List<String> paragraphs) {
        return paragraphs.stream().mapToInt(String::length).sum();
    }

    private static Article article(String title, String description, List<String> paragraphs) {
        List<String> content = length(paragraphs) < MIN_TEXT ? null : paragraphs;
        String excerpt;
        if (description != null && description.length() >= 20) {
            excerpt = description;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String p : paragraphs) {
                if (sb.length() >= EXCERPT_LENGTH) {
                    break;
                }
                if (!sb.isEmpty() && !isCjk(sb.charAt(sb.length() - 1))) {
                    sb.append(' ');
                }
                sb.append(p);
            }
            excerpt = sb.toString();
        }
        return new Article(title, cut(excerpt, EXCERPT_LENGTH).trim(), content, content == null ? 0 : minutes(content));
    }

    private static String title(Document doc) {
        String t = firstMeta(doc, "meta[property=og:title]", "meta[name=twitter:title]");
        if (t == null) {
            t = normalize(doc.title());
        }
        if (t.isEmpty()) {
            Element h1 = doc.selectFirst("h1");
            t = h1 == null ? "" : normalize(h1.text());
        }
        return t.isEmpty() ? null : cut(t, MAX_TITLE);
    }

    private static String firstMeta(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element meta = doc.selectFirst(selector);
            if (meta != null) {
                String v = normalize(meta.attr("content"));
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * 删掉不可能是正文的部分：脚本样式、导航页眉页脚、隐藏元素；stripUnlikely 时再删类名像侧栏评论广告的区块
     * （包着 main / article 的是布局容器，不删）
     */
    private static void clean(Document doc, boolean stripUnlikely) {
        doc.select(NOISE).remove();
        doc.select("[hidden], [aria-hidden=true], [style~=(?i)display\\s*:\\s*none]").remove();
        if (!stripUnlikely) {
            return;
        }
        for (Element el : doc.body().select("*")) {
            String tag = el.normalName();
            if (el == doc.body() || "article".equals(tag) || "main".equals(tag) || "a".equals(tag)) {
                continue;
            }
            String key = el.className() + " " + el.id();
            if (!key.isBlank() && UNLIKELY.matcher(key).find() && !MAYBE.matcher(key).find() && el.select("main, article").isEmpty()) {
                el.remove();
            }
        }
    }

    /**
     * 正文容器：优先 itemprop=articleBody；否则每个像样的段落给父元素加分、祖父元素加一半，取最高的；
     * 都没有就退到 article / main / body
     */
    private static Element pickRoot(Document doc) {
        Element body = doc.body();
        Element marked = largest(doc.select("[itemprop=articleBody]"));
        if (marked != null && marked.text().length() >= MIN_TEXT) {
            return marked;
        }
        Map<Element, Double> scores = new IdentityHashMap<>();
        for (Element p : body.select("p, pre")) {
            String text = p.text();
            if (text.length() < 25 || p.parent() == null) {
                continue;
            }
            double score = 1 + commas(text) + Math.min(text.length() / 100.0, 3);
            Element parent = p.parent();
            scores.merge(parent, score, Double::sum);
            if (parent.parent() != null) {
                scores.merge(parent.parent(), score / 2, Double::sum);
            }
        }
        Element top = scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (top != null) {
            return top;
        }
        Element fallback = largest(doc.select("article, main, [role=main]"));
        return fallback != null ? fallback : body;
    }

    private static Element largest(Elements elements) {
        Element best = null;
        int bestLength = -1;
        for (Element el : elements) {
            int length = el.text().length();
            if (length > bestLength) {
                best = el;
                bestLength = length;
            }
        }
        return best;
    }

    private static int commas(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',' || c == '，' || c == '、') {
                n++;
            }
        }
        return n;
    }

    /**
     * 按块级元素边界、&lt;br&gt; 切段；pre 里每行一段。段内空白压成一个空格
     */
    static List<String> paragraphs(Element root) {
        StringBuilder sb = new StringBuilder();
        int[] preDepth = {0};
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    String s = t.getWholeText();
                    sb.append(preDepth[0] > 0 ? s.replace('\n', BREAK) : s);
                } else if (node instanceof Element e) {
                    if ("pre".equals(e.normalName())) {
                        preDepth[0]++;
                    }
                    if ("br".equals(e.normalName()) || e.isBlock()) {
                        sb.append(BREAK);
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e) {
                    if (e.isBlock()) {
                        sb.append(BREAK);
                    }
                    if ("pre".equals(e.normalName())) {
                        preDepth[0]--;
                    }
                }
            }
        }, root);

        List<String> out = new ArrayList<>();
        int total = 0;
        for (String raw : sb.toString().split(String.valueOf(BREAK))) {
            String p = normalize(raw);
            if (p.length() < 2 || (!out.isEmpty() && out.get(out.size() - 1).equals(p))) {
                continue;
            }
            if (total + p.length() > MAX_TEXT || out.size() >= MAX_PARAGRAPHS) {
                break;
            }
            out.add(p);
            total += p.length();
        }
        return out;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        Matcher m = SPACES.matcher(ZERO_WIDTH.matcher(s).replaceAll(""));
        return m.replaceAll(" ").trim();
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                || (c >= '\u3000' && c <= '\u303F')
                || (c >= '\uFF00' && c <= '\uFFEF');
    }
}
