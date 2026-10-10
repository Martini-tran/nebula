package com.nebula.common.security.apikey;

import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import cn.dev33.satoken.apikey.error.SaApiKeyErrorCode;
import cn.dev33.satoken.apikey.exception.ApiKeyException;
import cn.dev33.satoken.apikey.exception.ApiKeyScopeException;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import com.nebula.common.core.context.UserContext;
import jakarta.servlet.DispatcherType;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAccessInterceptorTest {

    private static final String KEY = "AK-0123456789abcdefghijklmnopqrstuvwxyz";

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void requestWithoutApiKeyPassesUntouched() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(null, false, Set.of());

        assertTrue(interceptor.preHandle(request, response, handler(PlainController.class, "open")));

        assertNull(UserContext.get());
        assertFalse(interceptor.keyChecked);
    }

    @Test
    void loggedInRequestIsNotRestrictedByApiKey() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, true, Set.of());

        assertTrue(interceptor.preHandle(request, response, handler(PlainController.class, "open")));

        assertNull(UserContext.get());
        assertFalse(interceptor.keyChecked);
    }

    @Test
    void apiKeyCannotReachEndpointWithoutAnnotation() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:article:publish"));

        ApiKeyScopeException e = assertThrows(ApiKeyScopeException.class,
                () -> interceptor.preHandle(request, response, handler(PlainController.class, "open")));

        assertEquals(SaApiKeyErrorCode.CODE_12311, e.getCode());
        assertNull(e.getScope());
        assertNull(UserContext.get());
    }

    @Test
    void apiKeyCannotReachNonControllerHandler() {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:article:publish"));

        assertThrows(ApiKeyScopeException.class,
                () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void invalidKeyIsRejectedEvenOnAnnotatedEndpoint() {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:article:publish"));
        interceptor.keyValid = false;

        ApiKeyException e = assertThrows(ApiKeyException.class,
                () -> interceptor.preHandle(request, response, handler(PlainController.class, "keyed")));

        assertEquals(SaApiKeyErrorCode.CODE_12301, e.getCode());
        assertNull(UserContext.get());
    }

    @Test
    void annotatedEndpointBindsKeyOwnerAndClearsAfterwards() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:article:publish"));
        HandlerMethod handler = handler(PlainController.class, "keyed");

        assertTrue(interceptor.preHandle(request, response, handler));
        assertEquals(42L, UserContext.getUserId());

        interceptor.afterCompletion(request, response, handler, null);
        assertNull(UserContext.get());
    }

    @Test
    void keyWithoutRequiredScopeIsRejected() {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:series:edit"));

        ApiKeyScopeException e = assertThrows(ApiKeyScopeException.class,
                () -> interceptor.preHandle(request, response, handler(PlainController.class, "keyed")));

        assertEquals("blog:article:publish", e.getScope());
        assertNull(UserContext.get());
    }

    @Test
    void orModeNeedsOnlyOneScope() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("b"));

        assertTrue(interceptor.preHandle(request, response, handler(PlainController.class, "keyedOr")));
    }

    @Test
    void classAndMethodAnnotationsMustBothBeSatisfied() throws Exception {
        HandlerMethod both = handler(KeyedController.class, "both");

        assertThrows(ApiKeyScopeException.class,
                () -> new StubInterceptor(KEY, false, Set.of("class:scope")).preHandle(request, response, both));
        assertTrue(new StubInterceptor(KEY, false, Set.of("class:scope", "method:scope"))
                .preHandle(request, response, both));
    }

    @Test
    void classLevelAnnotationOpensEveryMethod() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("class:scope"));

        assertTrue(interceptor.preHandle(request, response, handler(KeyedController.class, "any")));
    }

    @Test
    void numericLoginIdFromRedisIsConvertedToLong() throws Exception {
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of("blog:article:publish"));
        interceptor.loginId = 7;

        interceptor.preHandle(request, response, handler(PlainController.class, "keyed"));

        assertEquals(7L, UserContext.getUserId());
    }

    @Test
    void asyncRedispatchIsSkipped() throws Exception {
        request.setDispatcherType(DispatcherType.ASYNC);
        StubInterceptor interceptor = new StubInterceptor(KEY, false, Set.of());

        assertTrue(interceptor.preHandle(request, response, handler(PlainController.class, "open")));
        assertFalse(interceptor.keyChecked);
    }

    private static HandlerMethod handler(Class<?> type, String method) throws Exception {
        return new HandlerMethod(type.getDeclaredConstructor().newInstance(), type.getDeclaredMethod(method));
    }

    static class PlainController {

        PlainController() {
        }

        public void open() {
        }

        @SaCheckApiKey(scope = "blog:article:publish")
        public void keyed() {
        }

        @SaCheckApiKey(scope = {"a", "b"}, mode = SaMode.OR)
        public void keyedOr() {
        }
    }

    @SaCheckApiKey(scope = "class:scope")
    static class KeyedController {

        KeyedController() {
        }

        public void any() {
        }

        @SaCheckApiKey(scope = "method:scope")
        public void both() {
        }
    }

    /**
     * 把依赖 Sa-Token 上下文与 Redis 的四个探针换成内存实现，scope 判定按框架同样的 AND/OR 语义模拟。
     */
    private static class StubInterceptor extends ApiKeyAccessInterceptor {

        private final String apiKey;
        private final boolean login;
        private final Set<String> grantedScopes;
        private boolean keyValid = true;
        private Object loginId = "42";
        private boolean keyChecked;

        StubInterceptor(String apiKey, boolean login, Set<String> grantedScopes) {
            this.apiKey = apiKey;
            this.login = login;
            this.grantedScopes = grantedScopes;
        }

        @Override
        protected String readApiKey() {
            return apiKey;
        }

        @Override
        protected boolean isLogin() {
            return login;
        }

        @Override
        protected ApiKeyModel checkApiKey(String apiKey) {
            keyChecked = true;
            if (!keyValid) {
                throw new ApiKeyException("invalid").setApiKey(apiKey).setCode(SaApiKeyErrorCode.CODE_12301);
            }
            return new ApiKeyModel().setApiKey(apiKey).setLoginId(loginId);
        }

        @Override
        protected void checkScope(String apiKey, String[] scopes, SaMode mode) {
            if (mode == SaMode.OR) {
                for (String scope : scopes) {
                    if (grantedScopes.contains(scope)) {
                        return;
                    }
                }
                throw new ApiKeyScopeException("missing").setApiKey(apiKey).setScope(scopes[0]);
            }
            for (String scope : scopes) {
                if (!grantedScopes.contains(scope)) {
                    throw new ApiKeyScopeException("missing").setApiKey(apiKey).setScope(scope);
                }
            }
        }
    }
}
