package io.github.arbiter.server;

import io.github.arbiter.api.config.ArbiterKeys;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = ArbiterServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "arbiter.server.host=127.0.0.1",
                "arbiter.server.port=0",
                "arbiter.api.prefix=/api/v1",
                "arbiter.transport=http",
                "arbiter.server.tls.enabled=true",
                "arbiter.server.tls.keystore.password=changeit"
        })
class TlsBootContextTest {
    private static final Path HOME = createHomeAndKeyStore();

    @DynamicPropertySource
    static void arbiterHome(DynamicPropertyRegistry registry) {
        System.setProperty(ArbiterKeys.SystemProperty.HOME, HOME.toString());
    }

    @LocalServerPort
    private int port;

    @AfterAll
    static void clearHome() {
        System.clearProperty(ArbiterKeys.SystemProperty.HOME);
    }

    @Test
    void servesActuatorOverHttps() throws Exception {
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{new TrustAll()}, new SecureRandom());
        HttpClient client = HttpClient.newBuilder().sslContext(ssl).build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://127.0.0.1:" + port + "/actuator/health"))
                .GET().build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
    }

    private static Path createHomeAndKeyStore() {
        try {
            Path home = Files.createTempDirectory("arbiter-tls-test");
            Path security = Files.createDirectories(home.resolve("conf/security"));
            Path keyStore = security.resolve("keystore.p12");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process process = new ProcessBuilder(
                    keytool.toString(),
                    "-genkeypair",
                    "-alias", "server",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-storetype", "PKCS12",
                    "-keystore", keyStore.toString(),
                    "-storepass", "changeit",
                    "-dname", "CN=localhost",
                    "-ext", "san=dns:localhost,ip:127.0.0.1",
                    "-validity", "2")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("keytool failed");
            }
            return home;
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static final class TrustAll implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
