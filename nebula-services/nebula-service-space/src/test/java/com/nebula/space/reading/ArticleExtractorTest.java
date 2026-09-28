package com.nebula.space.reading;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleExtractorTest {

    private static final String P1 = "对于反复发送相同长前缀的应用，系统提示、工具定义、大段参考文档，每次请求都重新处理这部分内容既慢又贵。";
    private static final String P2 = "缓存按前缀匹配：断点之前的内容必须逐字节一致，任何变化都会使该断点及其之后的缓存失效。";
    private static final String P3 = "缓存有存活时间，默认 TTL 较短，在期间内每次命中都会刷新计时。";

    private static final String PAGE = """
            <html><head>
              <title>Prompt caching - Docs</title>
              <meta property="og:title" content="Prompt caching：缓存断点与计费">
              <meta name="description" content="讲清楚缓存断点、TTL 和计费，以及怎么排查命中率低的问题。">
              <style>.x{color:red}</style><script>var tracking = 1;</script>
            </head><body>
              <nav><a href="/">首页</a><a href="/docs">文档</a></nav>
              <header class="site-header">站点页眉</header>
              <div class="layout">
                <article>
                  <h1>Prompt caching：缓存断点与计费</h1>
                  <div class="post-content">
                    <p>%s</p>
                    <p>%s</p>
                    <h2>存活时间</h2>
                    <p>%s</p>
                    <ul><li>工具定义之后一个断点</li><li>系统提示之后一个断点</li></ul>
                    <pre>const a = 1;
            const b = 2;</pre>
                    <p style="display: none">隐藏的文字不要</p>
                  </div>
                </article>
                <div class="sidebar"><p>侧栏推荐：另一篇很长很长很长很长很长很长很长很长的文章，也很有意思。</p></div>
                <div id="comments"><p>评论：写得真好，受益匪浅，收藏了，谢谢作者，期待下一篇。</p></div>
              </div>
              <footer>版权所有</footer>
            </body></html>
            """.formatted(P1, P2, P3);

    @Test
    void extractsArticleAndDropsNoise() {
        ArticleExtractor.Article a = ArticleExtractor.extract(PAGE, "https://docs.example.com/caching");
        assertEquals("Prompt caching：缓存断点与计费", a.title());
        assertEquals("讲清楚缓存断点、TTL 和计费，以及怎么排查命中率低的问题。", a.excerpt());
        assertEquals(List.of(P1, P2, "存活时间", P3, "工具定义之后一个断点", "系统提示之后一个断点", "const a = 1;", "const b = 2;"),
                a.paragraphs());
        assertEquals(1, a.minutes());
    }

    @Test
    void shortPageHasNoContentButKeepsTitle() {
        ArticleExtractor.Article a = ArticleExtractor.extract(
                "<html><head><title>请先登录</title></head><body><p>登录后查看全文</p></body></html>", "https://x.com/a");
        assertEquals("请先登录", a.title());
        assertNull(a.paragraphs());
        assertEquals(0, a.minutes());
        assertEquals("登录后查看全文", a.excerpt());
    }

    @Test
    void lineBreaksSplitParagraphsWhenThereAreNoParagraphTags() {
        String line = "这一行没有用段落标签包起来，只靠换行分开，但也应该被当成独立的一段文字来显示，后面再补一些字让它足够长。";
        ArticleExtractor.Article a = ArticleExtractor.extract(
                "<html><body><div class=\"story\">" + line + "<br>" + line.replace("这一行", "第二行") + "<br><br>" + line.replace("这一行", "第三行") + "</div></body></html>",
                "https://x.com/b");
        assertNotNull(a.paragraphs());
        assertEquals(3, a.paragraphs().size());
        assertTrue(a.paragraphs().get(1).startsWith("第二行"));
    }

    @Test
    void scoringPrefersTheBlockWithMostProse() {
        StringBuilder main = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            main.append("<p>正文第").append(i).append("段，有逗号，有句子，也有足够的长度让它被当成像样的段落。</p>");
        }
        String html = "<html><body><div class=\"widget\"><p>这是一个小部件里的段落，长度也够二十五个字了，凑数凑数。</p></div>"
                + "<div class=\"story\">" + main + "</div></body></html>";
        ArticleExtractor.Article a = ArticleExtractor.extract(html, "https://x.com/c");
        assertEquals(6, a.paragraphs().size());
        assertFalse(a.paragraphs().stream().anyMatch(p -> p.contains("小部件")));
    }

    @Test
    void layoutWrapperNamedLikeSidebarIsNotLost() {
        StringBuilder main = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            main.append("<p>正文第").append(i).append("段，外层布局容器的类名里带了 sidebars，按类名删会把正文一起删掉。</p>");
        }
        String html = "<html><body><div class=\"layout__2-sidebars-inline\"><div class=\"story\">" + main + "</div></div></body></html>";
        ArticleExtractor.Article a = ArticleExtractor.extract(html, "https://x.com/d");
        assertNotNull(a.paragraphs());
        assertEquals(6, a.paragraphs().size());
    }

    @Test
    void zeroWidthCharactersAreDropped() {
        String para = "正文里夹着零宽字符，渲染看不见，但会让划线的下标和看到的文字对不上，所以提取时去掉。".repeat(4);
        ArticleExtractor.Article a = ArticleExtractor.extract(
                "<html><body><h2>标题\u200B</h2><p>\uFEFF" + para + "</p></body></html>", "https://x.com/e");
        assertEquals(List.of("标题", para), a.paragraphs());
    }

    @Test
    void minutesCountHanCharactersAndWords() {
        assertEquals(2, ArticleExtractor.minutes(List.of("字".repeat(800))));
        assertEquals(2, ArticleExtractor.minutes(List.of("word ".repeat(440))));
        assertEquals(1, ArticleExtractor.minutes(List.of("短")));
    }

    @Test
    void cutKeepsSurrogatePairsWhole() {
        String s = "ab\uD840\uDC00cd";
        assertEquals("ab\uD840\uDC00", ArticleExtractor.cut(s, 3));
        assertEquals(s, ArticleExtractor.cut(s, 10));
    }

    @Test
    void plainTextSplitsOnBlankLines() {
        String para = "纯文本的一段话，写得比较长，这样加起来才能超过最少字数的要求，不然会被当成没抓到正文。再补充一句，确保长度足够。";
        ArticleExtractor.Article a = ArticleExtractor.fromPlainText(para + "\n接着同一段\n\n" + para + "\r\n\r\n" + para);
        assertEquals(3, a.paragraphs().size());
        assertEquals(para + " 接着同一段", a.paragraphs().get(0));
        assertNull(a.title());
    }
}
