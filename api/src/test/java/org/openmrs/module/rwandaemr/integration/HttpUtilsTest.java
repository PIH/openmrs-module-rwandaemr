package org.openmrs.module.rwandaemr.integration;

import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Disabled
public class HttpUtilsTest {

	protected Logger log = LoggerFactory.getLogger(getClass());

	public static final int TEST_HIE_SERVER_PORT = 8011;
	public static final String TEST_HIE_URL = "http://localhost:" + TEST_HIE_SERVER_PORT + "/hie";

	static {
		System.setProperty(IntegrationConfig.HIE_URL_PROPERTY, "http://localhost:8011/hie");
		System.setProperty(IntegrationConfig.HIE_USERNAME_PROPERTY, "hie");
		System.setProperty(IntegrationConfig.HIE_PASSWORD_PROPERTY, "test");
	}
	
	@Test
	public void shouldTimeoutEventually() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(TEST_HIE_SERVER_PORT), 0);
		ExecutorService serverExecutor = Executors.newFixedThreadPool(10);
		try {
			server.createContext("/", httpExchange -> {
				try {
					log.info("Received HTTP GET request");
					TimeUnit.HOURS.sleep(1); // Sleep indefinitely
					String response = "{\"message\": \"Success after delay\"}";
					httpExchange.sendResponseHeaders(200, response.length());
					try (OutputStream os = httpExchange.getResponseBody()) {
						os.write(response.getBytes());
					}
				}
				catch (InterruptedException e) {
					log.error("Error starting http server", e);
				}
			});
			server.setExecutor(serverExecutor);
			server.start();
			log.info("Server started on port {}", TEST_HIE_SERVER_PORT);

			long startTime = System.currentTimeMillis();
			ExecutorService executor = Executors.newFixedThreadPool(20);
			for (int i=1; i <= 50; i++) {
				final Integer threadNum = i;
				Runnable runnable = () -> {
					try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
						log.info("Thread #{}: Sending get request -> {}", threadNum, TEST_HIE_URL);
						HttpGet httpGet = new HttpGet(TEST_HIE_URL);
						try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
							int statusCode = response.getStatusLine().getStatusCode();
							HttpEntity entity = response.getEntity();
							String data = "";
							try {
								data = EntityUtils.toString(entity);
							} catch (Exception ignored) {
							}
							log.info("Thread #{}: {} -> {}", threadNum, statusCode, data);
						}
					}
					catch (Exception e) {
						log.info("Thread #{}: {}", threadNum, e.getMessage());
					}
				};
				executor.submit(runnable);
			}
			log.info("Shutting down executor");
			executor.shutdown();
			try {
				if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
					System.err.println("Timed out waiting for executor to shutdown");
				}
			}
			catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			long endTime = System.currentTimeMillis();

			assertThat((int)((endTime - startTime)/1000), equalTo(15)); // 3 batches of 5 second timeouts
		}
		finally {
			server.stop(0);
		}
	}
}
