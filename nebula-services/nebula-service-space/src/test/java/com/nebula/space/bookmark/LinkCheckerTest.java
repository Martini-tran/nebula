package com.nebula.space.bookmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkCheckerTest {

    @Test
    void onlyNotFoundAndGoneCountAsDead() {
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(404).verdict());
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(410).verdict());
        for (int code : new int[]{200, 204, 301, 302, 401, 403, 405, 406, 429}) {
            LinkChecker.Outcome outcome = LinkChecker.judge(code);
            assertEquals(LinkChecker.Verdict.ALIVE, outcome.verdict(), String.valueOf(code));
            assertNull(outcome.reason());
        }
        for (int code : new int[]{400, 418, 451, 500, 502, 503}) {
            assertEquals(LinkChecker.Verdict.UNKNOWN, LinkChecker.judge(code).verdict(), String.valueOf(code));
        }
    }

    @Test
    void internalAndNonWebAddressesAreNotJudged() throws Exception {
        LinkChecker checker = new LinkChecker();
        try {
            List<LinkChecker.Outcome> outcomes = checker.checkAll(List.of(
                    "http://127.0.0.1:9/admin", "http://localhost/", "javascript:void(0)", "chrome://settings", "not a url", "http:///x"));
            assertEquals(6, outcomes.size());
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "内网地址，服务器不检查"), outcomes.get(0));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "内网地址，服务器不检查"), outcomes.get(1));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "不是网页地址"), outcomes.get(2));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "不是网页地址"), outcomes.get(3));
            assertEquals(LinkChecker.Verdict.UNKNOWN, outcomes.get(4).verdict());
            assertEquals(LinkChecker.Verdict.UNKNOWN, outcomes.get(5).verdict());
        } finally {
            checker.destroy();
        }
    }
}
