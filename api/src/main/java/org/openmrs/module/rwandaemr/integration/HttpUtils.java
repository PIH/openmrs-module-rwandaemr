package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.openmrs.util.ConfigUtil;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

public class HttpUtils {

    private static final Log log = LogFactory.getLog(HttpUtils.class);

    public static final int CONNECT_TIMEOUT = 5000; // default ms to establish connection with remote host
    public static final int SOCKET_TIMEOUT = 5000; // default ms to wait for data after establishing connection
    public static final int CONNECTION_REQUEST_TIMEOUT = 5000; // default ms to wait for a connection from the pool

    // Global properties to override default HIE HTTP timeouts (milliseconds)
    public static final String HIE_CONNECT_TIMEOUT_MS_GP = "rwandaemr.hie.connectTimeoutMs";
    public static final String HIE_SOCKET_TIMEOUT_MS_GP = "rwandaemr.hie.socketTimeoutMs";
    public static final String HIE_CONNECTION_REQUEST_TIMEOUT_MS_GP = "rwandaemr.hie.connectionRequestTimeoutMs";

    public static CloseableHttpClient getHttpClient(String username, String password, boolean trustAllCertificates) {
        try {
            HttpClientBuilder builder = HttpClients.custom();
            int connectTimeout = getConfiguredTimeout(HIE_CONNECT_TIMEOUT_MS_GP, CONNECT_TIMEOUT);
            int socketTimeout = getConfiguredTimeout(HIE_SOCKET_TIMEOUT_MS_GP, SOCKET_TIMEOUT);
            int connectionRequestTimeout = getConfiguredTimeout(HIE_CONNECTION_REQUEST_TIMEOUT_MS_GP, CONNECTION_REQUEST_TIMEOUT);
            
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                builder.setDefaultCredentialsProvider(credentialsProvider);
            }
            if (trustAllCertificates) {
                SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustAllStrategy()).build();
                HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
                builder.setSSLSocketFactory(sslFactory);
            }

            builder.setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(connectTimeout)
                    .setSocketTimeout(socketTimeout)
                    .setConnectionRequestTimeout(connectionRequestTimeout)
                    .build()
            );

            return builder.build();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the http client to use to interact with the HIE, or null if no HIE credentials are configured
     */
    public static CloseableHttpClient getHieClient() {
        String username = ConfigUtil.getProperty(IntegrationConfig.HIE_USERNAME_PROPERTY);
        String password = ConfigUtil.getProperty(IntegrationConfig.HIE_PASSWORD_PROPERTY);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            return getHttpClient(username, password, true);
        }
        return null;
    }

    private static int getConfiguredTimeout(String gpName, int defaultValue) {
        try {
            String configured = ConfigUtil.getProperty(gpName);
            if (StringUtils.isNotBlank(configured)) {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed > 0) {
                    return parsed;
                }
                log.warn("Ignoring non-positive timeout value for " + gpName + ": " + configured);
            }
        } catch (Exception e) {
            log.warn("Invalid timeout value for " + gpName + ", using default " + defaultValue + " ms");
        }
        return defaultValue;
    }
}
