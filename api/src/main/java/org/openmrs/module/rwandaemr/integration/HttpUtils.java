package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.openmrs.util.ConfigUtil;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class HttpUtils {

    private static final Log log = LogFactory.getLog(HttpUtils.class);

    public static final int CONNECT_TIMEOUT = 5000; // default ms to establish connection with remote host
    public static final int SOCKET_TIMEOUT = 5000; // default ms to wait for data after establishing connection
    public static final int CONNECTION_REQUEST_TIMEOUT = 5000; // default ms to wait for a connection from the pool
    public static final String FHIR_JSON_CONTENT_TYPE = "application/fhir+json";

    // Global properties to override default HIE HTTP timeouts (milliseconds)
    public static final String HIE_CONNECT_TIMEOUT_MS_GP = "rwandaemr.hie.connectTimeoutMs";
    public static final String HIE_SOCKET_TIMEOUT_MS_GP = "rwandaemr.hie.socketTimeoutMs";
    public static final String HIE_CONNECTION_REQUEST_TIMEOUT_MS_GP = "rwandaemr.hie.connectionRequestTimeoutMs";
    public static final String HIE_MAX_TOTAL_CONNECTIONS_GP = "rwandaemr.hie.maxTotalConnections";
    public static final String HIE_MAX_CONNECTIONS_PER_ROUTE_GP = "rwandaemr.hie.maxConnectionsPerRoute";

    private static final int DEFAULT_HIE_MAX_TOTAL_CONNECTIONS = 20;
    private static final int DEFAULT_HIE_MAX_CONNECTIONS_PER_ROUTE = 10;
    private static final int HIE_IDLE_CONNECTION_EVICT_SECONDS = 30;

    private static CloseableHttpClient pooledHieClient;
    private static CloseableHttpClient pooledHieClientFacade;
    private static String pooledHieClientKey;

    public static CloseableHttpClient getHttpClient(String username, String password, boolean trustAllCertificates) {
        try {
            int connectTimeout = getConfiguredTimeout(HIE_CONNECT_TIMEOUT_MS_GP, CONNECT_TIMEOUT);
            int socketTimeout = getConfiguredTimeout(HIE_SOCKET_TIMEOUT_MS_GP, SOCKET_TIMEOUT);
            int connectionRequestTimeout = getConfiguredTimeout(HIE_CONNECTION_REQUEST_TIMEOUT_MS_GP, CONNECTION_REQUEST_TIMEOUT);
            return buildHttpClient(username, password, trustAllCertificates, connectTimeout, socketTimeout,
                    connectionRequestTimeout, false);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the http client to use to interact with the HIE, or null if no HIE credentials are configured
     */
    public static synchronized CloseableHttpClient getHieClient() {
        String username = ConfigUtil.getProperty(IntegrationConfig.HIE_USERNAME_PROPERTY);
        String password = ConfigUtil.getProperty(IntegrationConfig.HIE_PASSWORD_PROPERTY);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            int connectTimeout = getConfiguredTimeout(HIE_CONNECT_TIMEOUT_MS_GP, CONNECT_TIMEOUT);
            int socketTimeout = getConfiguredTimeout(HIE_SOCKET_TIMEOUT_MS_GP, SOCKET_TIMEOUT);
            int connectionRequestTimeout = getConfiguredTimeout(HIE_CONNECTION_REQUEST_TIMEOUT_MS_GP, CONNECTION_REQUEST_TIMEOUT);
            int maxTotalConnections = getConfiguredPositiveInt(HIE_MAX_TOTAL_CONNECTIONS_GP, DEFAULT_HIE_MAX_TOTAL_CONNECTIONS);
            int maxConnectionsPerRoute = getConfiguredPositiveInt(HIE_MAX_CONNECTIONS_PER_ROUTE_GP, DEFAULT_HIE_MAX_CONNECTIONS_PER_ROUTE);
            String clientKey = username + "\n" + password + "\n" + connectTimeout + "\n" + socketTimeout + "\n" +
                    connectionRequestTimeout + "\n" + maxTotalConnections + "\n" + maxConnectionsPerRoute;

            if (pooledHieClient != null && clientKey.equals(pooledHieClientKey)) {
                return pooledHieClientFacade;
            }

            closePooledHieClient();
            pooledHieClient = buildHttpClient(username, password, true, connectTimeout, socketTimeout,
                    connectionRequestTimeout, true);
            pooledHieClientFacade = new NonClosingCloseableHttpClient(pooledHieClient);
            pooledHieClientKey = clientKey;
            return pooledHieClientFacade;
        }
        closePooledHieClient();
        return null;
    }

    private static CloseableHttpClient buildHttpClient(String username, String password, boolean trustAllCertificates,
                                                       int connectTimeout, int socketTimeout,
                                                       int connectionRequestTimeout, boolean pooled) {
        try {
            HttpClientBuilder builder = HttpClients.custom();

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

            if (pooled) {
                PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                connectionManager.setMaxTotal(getConfiguredPositiveInt(HIE_MAX_TOTAL_CONNECTIONS_GP,
                        DEFAULT_HIE_MAX_TOTAL_CONNECTIONS));
                connectionManager.setDefaultMaxPerRoute(getConfiguredPositiveInt(HIE_MAX_CONNECTIONS_PER_ROUTE_GP,
                        DEFAULT_HIE_MAX_CONNECTIONS_PER_ROUTE));
                connectionManager.setValidateAfterInactivity(socketTimeout);
                builder.setConnectionManager(connectionManager)
                        .evictExpiredConnections()
                        .evictIdleConnections(HIE_IDLE_CONNECTION_EVICT_SECONDS, TimeUnit.SECONDS);
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

    private static void closePooledHieClient() {
        if (pooledHieClient != null) {
            try {
                pooledHieClient.close();
            } catch (IOException e) {
                log.warn("Unable to close existing pooled HIE HTTP client", e);
            }
        }
        pooledHieClient = null;
        pooledHieClientFacade = null;
        pooledHieClientKey = null;
    }

    private static int getConfiguredTimeout(String gpName, int defaultValue) {
        return getConfiguredPositiveInt(gpName, defaultValue);
    }

    private static int getConfiguredPositiveInt(String gpName, int defaultValue) {
        try {
            String configured = ConfigUtil.getProperty(gpName);
            if (StringUtils.isNotBlank(configured)) {
                int parsed = Integer.parseInt(configured.trim());
                if (parsed > 0) {
                    return parsed;
                }
                log.warn("Ignoring non-positive value for " + gpName + ": " + configured);
            }
        } catch (Exception e) {
            log.warn("Invalid value for " + gpName + ", using default " + defaultValue);
        }
        return defaultValue;
    }

    private static class NonClosingCloseableHttpClient extends CloseableHttpClient {

        private final CloseableHttpClient delegate;

        private NonClosingCloseableHttpClient(CloseableHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
                throws IOException, ClientProtocolException {
            return delegate.execute(target, request, context);
        }

        @Override
        public HttpParams getParams() {
            return delegate.getParams();
        }

        @Override
        public ClientConnectionManager getConnectionManager() {
            return delegate.getConnectionManager();
        }

        @Override
        public void close() {
            // Callers commonly use try-with-resources; keep the shared pooled HIE client alive.
        }
    }
}
