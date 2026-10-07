package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.model.Message;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Order;
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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RadiologyOrderEventListenerTest extends BaseHL7TranslatorTest {

	private RadiologyOrderEventListener listener;
	private Ris ris;

	@BeforeEach
	public void setUpListener() {
		listener = new RadiologyOrderEventListener(orderService, orderToORMTranslator, radiologyConfig);
		// Record the accession number and fulfiller comment the way the order service would
		when(orderService.updateOrderFulfillerStatus(any(), any(), any(), any())).thenAnswer(invocation -> {
			Order order = invocation.getArgument(0);
			order.setFulfillerComment(invocation.getArgument(2));
			order.setAccessionNumber(invocation.getArgument(3));
			return order;
		});
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

	@Test
	public void shouldSendTestsOfTheSameModalityAsOneCombinedStudy() throws Exception {
		ris = new Ris(0, false);
		testOrder.setOrderId(1);
		TestOrder handXray = addOrder(2, "ORD-764592", addOrderable(crConceptSet, "X-RAY, HAND", "37001-0"));
		TestOrder headCt = addOrder(3, "ORD-764593", addOrderable(ctConceptSet, "CT, HEAD", "30799-1"));
		stubOrderLookup(testOrder, handXray, headCt);

		listener.handleOrder(testOrder.getUuid(), null);
		listener.handleOrder(handXray.getUuid(), null);
		listener.handleOrder(headCt.getUuid(), null);

		assertThat(ris.messagesReceived.size(), equalTo(2));
		String xrayStudy = ris.messagesReceived.get(0);
		assertThat(countSegments(xrayStudy, "OBR"), equalTo(1));
		assertThat(segment(xrayStudy, "OBR"), startsWith("OBR|1|ORD-764591|"));
		assertThat(segment(xrayStudy, "OBR"), containsString("|36657-5-Combine^X-RAY, FOOT_X-RAY, HAND|"));
		assertThat(segment(xrayStudy, "ORC"), equalTo("ORC|NW"));
		// The CT is a different modality, so it is its own study
		String ctStudy = ris.messagesReceived.get(1);
		assertThat(segment(ctStudy, "OBR"), startsWith("OBR|1|ORD-764593|"));
		assertThat(segment(ctStudy, "OBR"), containsString("|30799-1^CT, HEAD|"));
		// Each test keeps its own order number, and the tests of the study share its accession number
		assertThat(testOrder.getAccessionNumber(), equalTo("ORD-764591"));
		assertThat(handXray.getAccessionNumber(), equalTo("ORD-764591"));
		assertThat(headCt.getAccessionNumber(), equalTo("ORD-764593"));
		assertThat(handXray.getOrderNumber(), equalTo("ORD-764592"));
	}

	@Test
	public void shouldSendEachTestOnItsOwnWhenCombiningIsOff() throws Exception {
		ris = new Ris(0, false);
		when(administrationService.getGlobalProperty(RadiologyConfig.GP_COMBINE_ORDERS_BY_MODALITY, "true")).thenReturn("false");
		testOrder.setOrderId(1);
		TestOrder handXray = addOrder(2, "ORD-764592", addOrderable(crConceptSet, "X-RAY, HAND", "37001-0"));
		stubOrderLookup(testOrder, handXray);

		listener.handleOrder(testOrder.getUuid(), null);
		listener.handleOrder(handXray.getUuid(), null);

		assertThat(ris.messagesReceived.size(), equalTo(2));
		assertThat(segment(ris.messagesReceived.get(0), "OBR"), startsWith("OBR|1|ORD-764591|"));
		assertThat(segment(ris.messagesReceived.get(0), "OBR"), containsString("|36657-5^X-RAY, FOOT|"));
		assertThat(segment(ris.messagesReceived.get(1), "OBR"), startsWith("OBR|1|ORD-764592|"));
		assertThat(segment(ris.messagesReceived.get(1), "OBR"), containsString("|37001-0^X-RAY, HAND|"));
		assertThat(handXray.getAccessionNumber(), equalTo("ORD-764592"));
	}

	@Test
	public void shouldNotSendATestAgainWhenItIsUpdatedAfterBeingSent() throws Exception {
		ris = new Ris(0, false);
		testOrder.setOrderId(1);
		testOrder.setAccessionNumber("ORD-764591");
		stubOrderLookup(testOrder);

		listener.handleOrder(testOrder.getUuid(), null);

		assertThat(ris.messagesReceived.size(), equalTo(0));
	}

	@Test
	public void shouldCancelTheStudyAndResendTheRemainingTestsWhenOneTestIsCancelled() throws Exception {
		ris = new Ris(0, false);
		testOrder.setOrderId(1);
		TestOrder handXray = addOrder(2, "ORD-764592", addOrderable(crConceptSet, "X-RAY, HAND", "37001-0"));
		TestOrder wristXray = addOrder(3, "ORD-764593", addOrderable(crConceptSet, "X-RAY, WRIST", "37002-8"));
		testOrder.setAccessionNumber("ORD-764591");
		handXray.setAccessionNumber("ORD-764591");
		wristXray.setAccessionNumber("ORD-764591");
		testOrder.setVoided(true);
		stubOrderLookup(testOrder, handXray, wristXray);

		listener.handleOrder(testOrder.getUuid(), null);

		assertThat(ris.messagesReceived.size(), equalTo(2));
		String cancellation = ris.messagesReceived.get(0);
		assertThat(segment(cancellation, "ORC"), equalTo("ORC|CA"));
		assertThat(segment(cancellation, "OBR"), startsWith("OBR|1|ORD-764591|"));
		assertThat(segment(cancellation, "OBR"), containsString("|36657-5-Combine^"));
		String newStudy = ris.messagesReceived.get(1);
		assertThat(segment(newStudy, "ORC"), equalTo("ORC|NW"));
		assertThat(segment(newStudy, "OBR"), startsWith("OBR|1|ORD-764592|"));
		assertThat(segment(newStudy, "OBR"), containsString("|37001-0-Combine^X-RAY, HAND_X-RAY, WRIST|"));
		assertThat(handXray.getAccessionNumber(), equalTo("ORD-764592"));
		assertThat(wristXray.getAccessionNumber(), equalTo("ORD-764592"));
		assertThat(testOrder.getFulfillerComment(), equalTo(RadiologyOrderEventListener.CANCELLATION_SENT));

		// Further updates to the cancelled order do not send the cancellation again
		listener.handleOrder(testOrder.getUuid(), null);
		assertThat(ris.messagesReceived.size(), equalTo(2));
	}

	@Test
	public void shouldResendTheWholeStudyWhenSentFromTheOrderPage() throws Exception {
		ris = new Ris(0, false);
		testOrder.setOrderId(1);
		TestOrder handXray = addOrder(2, "ORD-764592", addOrderable(crConceptSet, "X-RAY, HAND", "37001-0"));
		testOrder.setAccessionNumber("ORD-764591");
		handXray.setAccessionNumber("ORD-764591");

		listener.sendRadiologyOrderMessage(handXray);

		assertThat(ris.messagesReceived.size(), equalTo(1));
		assertThat(segment(ris.messagesReceived.get(0), "OBR"), startsWith("OBR|1|ORD-764591|"));
		assertThat(segment(ris.messagesReceived.get(0), "OBR"), containsString("|36657-5-Combine^X-RAY, FOOT_X-RAY, HAND|"));
	}

	private TestOrder addOrder(Integer orderId, String orderNumber, Concept concept) throws Exception {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setPatient(patient);
		encounter.addOrder(order);
		order.setDateActivated(encounter.getEncounterDatetime());
		order.setOrderer(orderingProvider);
		order.setCareSetting(inpatient);
		FieldUtils.writeField(order, "orderNumber", orderNumber, true);
		order.setConcept(concept);
		return order;
	}

	private Concept addOrderable(Concept conceptSet, String name, String loincCode) {
		Concept concept = createConcept(name, loinc, loincCode);
		conceptSet.addSetMember(concept);
		return concept;
	}

	private void stubOrderLookup(TestOrder... orders) {
		for (TestOrder order : orders) {
			when(orderService.getOrderByUuid(order.getUuid())).thenReturn(order);
		}
	}

	private String segment(String message, String name) {
		for (String line : message.split("[\\r\\n]")) {
			if (line.startsWith(name + "|") || line.equals(name)) {
				return line;
			}
		}
		return null;
	}

	private int countSegments(String message, String name) {
		int count = 0;
		for (String line : message.split("[\\r\\n]")) {
			if (line.startsWith(name + "|")) {
				count++;
			}
		}
		return count;
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
		private final List<String> messagesReceived = new CopyOnWriteArrayList<String>();

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
					messagesReceived.add(received.toString("UTF-8"));
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
