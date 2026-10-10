package com.nebula.common.oss.service;

import com.nebula.common.oss.api.BucketConfig;
import com.nebula.common.oss.properties.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.Signer;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinioServiceImplTest {

    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";

    @Test
    void signsBrowserDomainLocallyWithConfiguredRegionAndExpiry() throws Exception {
        MinioClient internalClient = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties();
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setRegion("cn-north-1");
        properties.getBuckets().put("private", new BucketConfig(
                "actual-private", "https://files.example.invalid:9443", null));

        MinioServiceImpl service = new MinioServiceImpl(internalClient, properties);
        HttpUrl url = HttpUrl.get(service.getPresignedUrl("private", "images/封面 1.png", 86400));

        assertEquals("https", url.scheme());
        assertEquals("files.example.invalid", url.host());
        assertEquals(9443, url.port());
        assertEquals("/actual-private/images/封面 1.png", "/" + String.join("/", url.pathSegments()));
        assertEquals("86400", url.queryParameter("X-Amz-Expires"));
        assertTrue(url.queryParameter("X-Amz-Credential").contains("/cn-north-1/s3/aws4_request"));
        assertEquals("host", url.queryParameter("X-Amz-SignedHeaders"));

        // 固定原签名时间重新计算，验证签名覆盖实际外部 Host（包含端口）。
        String originalSignature = url.queryParameter("X-Amz-Signature");
        HttpUrl unsignedUrl = url.newBuilder().query(null).build();
        Request request = new Request.Builder().url(unsignedUrl)
                .header("Host", "files.example.invalid:9443")
                .header("x-amz-date", url.queryParameter("X-Amz-Date"))
                .build();
        HttpUrl expected = Signer.presignV4(request, "cn-north-1", ACCESS_KEY, SECRET_KEY, 86400);
        assertEquals(expected.queryParameter("X-Amz-Signature"), originalSignature);

        Request internalHostRequest = request.newBuilder().header("Host", "minio:9000").build();
        HttpUrl internalSignature = Signer.presignV4(
                internalHostRequest, "cn-north-1", ACCESS_KEY, SECRET_KEY, 86400);
        assertNotEquals(internalSignature.queryParameter("X-Amz-Signature"), originalSignature);
        verifyNoInteractions(internalClient);
    }

    @Test
    void retainsInternalClientWhenBrowserDomainIsNotConfigured() throws Exception {
        MinioClient internalClient = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties();
        properties.getBuckets().put("private", new BucketConfig("actual-private"));
        when(internalClient.getPresignedObjectUrl(any())).thenReturn("http://localhost:9000/signed");

        String url = new MinioServiceImpl(internalClient, properties)
                .getPresignedUrl("private", "images/cover.png", 7200);

        assertEquals("http://localhost:9000/signed", url);
        ArgumentCaptor<GetPresignedObjectUrlArgs> args = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(internalClient).getPresignedObjectUrl(args.capture());
        assertEquals("actual-private", args.getValue().bucket());
        assertEquals("images/cover.png", args.getValue().object());
        assertEquals(7200, args.getValue().expiry());
    }
}
