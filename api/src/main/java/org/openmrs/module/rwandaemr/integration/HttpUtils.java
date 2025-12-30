package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.lang3.StringUtils;
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

    // Timeout configuration constants (in milliseconds)
    private static final int CONNECTION_TIMEOUT = 10000;  // 10 seconds to establish connection
    private static final int SOCKET_TIMEOUT = 30000;     // 30 seconds to read data
    private static final int CONNECTION_REQUEST_TIMEOUT = 10000; // 10 seconds to get connection from pool

    public static CloseableHttpClient getHttpClient(String username, String password, boolean trustAllCertificates) {
        try {
            HttpClientBuilder builder = HttpClients.custom();
            
            // Configure timeouts to prevent indefinite hangs
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .build();
            builder.setDefaultRequestConfig(requestConfig);
            
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
}
