package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.model.Message;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.TestOrder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RadiologyOrderEventListenerTest extends BaseHL7TranslatorTest {

	private RadiologyOrderEventListener listener;
	private Ris ris;

	@BeforeEach
	public void setUpListener() {
		listener = new RadiologyOrderEventListener(orderService, orderToORMTranslator);
		System.setProperty(RadiologyConfig.PROPERTY_OUTGOING_HL7_HOST, "localhost");
		System.setProperty(RadiologyOrderEventListener.PROPERTY_CONNECT_TIMEOUT, "2000");
		System.setProperty(RadiologyOrderEventListener.PROPERTY_ACK_TIMEOUT, "5000");
		System.setProperty(RadiologyOrderEventListener.PROPERTY_MIN_INTERVAL, "0");
		System.setProperty(RadiologyOrderEventListener.PROPERTY_RETRY_COUNT, "2");
		System.setProperty(RadiologyOrderEventListener.PROPERTY_RETRY_DELAY, "50");
	}

	@AfterEach
	public void tearDownListener() throws IOException {
		if (ris != null) {
			ris.stop();
		}
		System.clearProperty(RadiologyConfig.PROPERTY_OUTGOING_HL7_HOST);
		System.clearProperty(RadiologyConfig.PROPERTY_OUTGOING_HL7_PORT);
		System.clearProperty(RadiologyOrderEventListener.PROPERTY_CONNECT_TIMEOUT);
		System.clearProperty(RadiologyOrderEventListener.PROPERTY_ACK_TIMEOUT);
		System.clearProperty(RadiologyOrderEventListener.PROPERTY_MIN_INTERVAL);
		System.clearProperty(RadiologyOrderEventListener.PROPERTY_RETRY_COUNT);
		System.clearProperty(RadiologyOrderEventListener.PROPERTY_RETRY_DELAY);
	}

	@Test
	public void shouldSendOrderWhenRisAcceptsIt() throws Exception {
		ris = new Ris(0, false);

		listener.sendRadiologyOrderMessage(testOrder);

		assertThat(ris.connectionTimes.size(), equalTo(1));
		assertThat(ris.messagesAcknowledged.get(), equalTo(1));
	}

	@Test
	public void shouldRetryWhenRisRejectsTheConnection() throws Exception {
		ris = new Ris(1, false);

		listener.sendRadiologyOrderMessage(testOrder);

		assertThat(ris.connectionTimes.size(), equalTo(2));
		assertThat(ris.messagesAcknowledged.get(), equalTo(1));
	}

	@Test
	public void shouldRetryStraightAwayWhenRisClosesTheConnectionAfterReadingTheMessage() throws Exception {
		ris = new Ris(1, true);
		long start = System.currentTimeMillis();

		listener.sendRadiologyOrderMessage(testOrder);

		assertThat(ris.connectionTimes.size(), equalTo(2));
		assertThat(ris.messagesAcknowledged.get(), equalTo(1));
		// The closed connection must be detected at once, not after the 5 second acknowledgement timeout
		long elapsed = System.currentTimeMillis() - start;
		assertThat("send took " + elapsed + " ms", elapsed < 3000, is(true));
	}

	@Test
	public void shouldFailAfterAllRetriesWhenRisKeepsRejectingTheConnection() throws Exception {
		ris = new Ris(10, false);

		RuntimeException e = assertThrows(RuntimeException.class, () -> listener.sendRadiologyOrderMessage(testOrder));

		assertThat(ris.connectionTimes.size(), equalTo(3));
		assertThat(e.getMessage(), containsString("did not accept the order after 3 attempts"));
	}

	@Test
	public void shouldWaitTheMinimumIntervalBetweenConnections() throws Exception {
		ris = new Ris(0, false);
		System.setProperty(RadiologyOrderEventListener.PROPERTY_MIN_INTERVAL, "400");
		TestOrder secondXray = new TestOrder();
		secondXray.setPatient(patient);
		encounter.addOrder(secondXray);
		secondXray.setDateActivated(encounter.getEncounterDatetime());
		secondXray.setOrderer(orderingProvider);
		secondXray.setCareSetting(inpatient);
		FieldUtils.writeField(secondXray, "orderNumber", "ORD-764592", true);
		secondXray.setConcept(xrayConcept);

		listener.sendRadiologyOrderMessage(testOrder);
		listener.sendRadiologyOrderMessage(secondXray);

		assertThat(ris.connectionTimes.size(), equalTo(2));
		long gap = ris.connectionTimes.get(1) - ris.connectionTimes.get(0);
		assertThat("gap between connections was " + gap + " ms", gap >= 380, is(true));
		assertThat(ris.messagesAcknowledged.get(), equalTo(2));
	}

	/**
	 * Minimal MLLP HL7 listener that rejects the first given number of connections, like the RIS does when a connection
	 * arrives too soon after the previous one, and acknowledges messages on the connections after that
	 */
	private static class Ris implements Runnable {

		private final ServerSocket serverSocket;
		private final AtomicInteger connectionsToReject;
		private final boolean rejectAfterReading;
		private final List<Long> connectionTimes = new CopyOnWriteArrayList<Long>();
		private final AtomicInteger messagesAcknowledged = new AtomicInteger();

		/**
		 * @param rejectAfterReading if true, rejected connections are closed after the message is read, rather than
		 * as soon as they are accepted
		 */
		Ris(int connectionsToReject, boolean rejectAfterReading) throws IOException {
			this.connectionsToReject = new AtomicInteger(connectionsToReject);
			this.rejectAfterReading = rejectAfterReading;
			serverSocket = new ServerSocket(0);
			System.setProperty(RadiologyConfig.PROPERTY_OUTGOING_HL7_PORT, String.valueOf(serverSocket.getLocalPort()));
			Thread thread = new Thread(this, "test-ris");
			thread.setDaemon(true);
			thread.start();
		}

		@Override
		public void run() {
			while (!serverSocket.isClosed()) {
				try (Socket socket = serverSocket.accept()) {
					connectionTimes.add(System.currentTimeMillis());
					boolean reject = connectionsToReject.getAndDecrement() > 0;
					if (reject && !rejectAfterReading) {
						continue;
					}
					InputStream in = socket.getInputStream();
					ByteArrayOutputStream received = new ByteArrayOutputStream();
					int previous = -1;
					int b;
					while ((b = in.read()) != -1) {
						if (previous == 0x1c && b == 0x0d) {
							break;
						}
						if (b != 0x0b && b != 0x1c) {
							received.write(b);
						}
						previous = b;
					}
					if (reject) {
						continue;
					}
					Message ack = new DefaultHapiContext().getPipeParser().parse(received.toString("UTF-8")).generateACK();
					OutputStream out = socket.getOutputStream();
					out.write(0x0b);
					out.write(ack.encode().getBytes("UTF-8"));
					out.write(0x1c);
					out.write(0x0d);
					out.flush();
					messagesAcknowledged.incrementAndGet();
					while (in.read() != -1) {
						// wait for the sender to close the connection
					}
				}
				catch (Exception e) {
					// connection closed by the sender or the test
				}
			}
		}

		void stop() throws IOException {
			serverSocket.close();
		}
	}
}
