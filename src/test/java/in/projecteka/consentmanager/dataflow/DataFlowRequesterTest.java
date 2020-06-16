package in.projecteka.consentmanager.dataflow;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.ConsentManagerClient;
import in.projecteka.consentmanager.dataflow.model.*;
import in.projecteka.consentmanager.link.discovery.model.patient.response.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Objects;

import static in.projecteka.consentmanager.dataflow.TestBuilders.consentArtefactRepresentation;
import static in.projecteka.consentmanager.dataflow.TestBuilders.dataFlowRequest;
import static in.projecteka.consentmanager.dataflow.TestBuilders.healthInformationResponseBuilder;
import static in.projecteka.consentmanager.dataflow.Utils.toDate;
import static in.projecteka.consentmanager.dataflow.Utils.toDateWithMilliSeconds;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DataFlowRequesterTest {
    @Mock
    private DataFlowRequestRepository dataFlowRequestRepository;

    @Mock
    private ConsentManagerClient consentManagerClient;

    @Mock
    private PostDataFlowRequestApproval postDataFlowRequestApproval;

    private DataFlowRequester dataFlowRequester;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        dataFlowRequester = new DataFlowRequester(consentManagerClient, dataFlowRequestRepository,
                postDataFlowRequestApproval);
    }

    @Test
    void shouldAcceptDataFlowRequest() {
        String hiuId = "10000005";
        var request = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-15T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();
        var consentArtefactRepresentation = consentArtefactRepresentation().build();
        consentArtefactRepresentation.setStatus(ConsentStatus.GRANTED);
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        consentArtefactRepresentation.getConsentDetail().getPermission().setDataEraseAt(toDateWithMilliSeconds("253379772420000"));
        consentArtefactRepresentation.getConsentDetail().getPermission().
                setDateRange(AccessPeriod.builder()
                        .fromDate(toDate("2020-01-15T08:47:48"))
                        .toDate(toDate("2020-01-20T08:47:48"))
                        .build());

        when(consentManagerClient.getConsentArtefact(request.getConsent().getId()))
                .thenReturn(Mono.just(consentArtefactRepresentation));
        when(dataFlowRequestRepository.addDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class)))
                .thenReturn(Mono.create(MonoSink::success));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class))).thenReturn(Mono.empty());

        StepVerifier.create(dataFlowRequester.requestHealthData(hiuId, request))
                .expectNextMatches(Objects::nonNull)
                .verifyComplete();
    }

    @Test
    public void shouldThrowInvalidHIU() {
        in.projecteka.consentmanager.dataflow.model.DataFlowRequest request = dataFlowRequest().build();
        ConsentArtefactRepresentation consentArtefactRepresentation = consentArtefactRepresentation().build();

        when(consentManagerClient.getConsentArtefact(request.getConsent().getId()))
                .thenReturn(Mono.just(consentArtefactRepresentation));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class))).thenReturn(Mono.empty());

        StepVerifier.create(dataFlowRequester.requestHealthData("1", request))
                .expectErrorMatches(e -> (e instanceof ClientError) && ((ClientError) e).getHttpStatus().is4xxClientError());
    }

    @Test
    void shouldUpdateHealtInfoStatus() {
        var healthInformationResponse = healthInformationResponseBuilder().build();

        when(dataFlowRequestRepository.updateDataFlowRequestStatus(healthInformationResponse.getHiRequest().getTransactionId(),
                healthInformationResponse.getHiRequest().getSessionStatus())).thenReturn(Mono.create(MonoSink::success));

        StepVerifier.create(dataFlowRequester.updateDataflowRequestStatus(healthInformationResponse))
                .verifyComplete();
    }

    @Test
    void shouldLogErrorWhenAcknowledgementIsAbsent() {
        var gatewayResponse = GatewayResponse.builder().requestId("requestId").build();
        var healthInformationResponse = healthInformationResponseBuilder()
                .resp(gatewayResponse)
                .hiRequest(null)
                .build();

        Logger logger = (Logger) LoggerFactory.getLogger(DataFlowRequester.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        StepVerifier.create(dataFlowRequester.updateDataflowRequestStatus(healthInformationResponse))
                .verifyComplete();
        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals("DataFlowRequest failed for request id requestId",logsList.get(0).getFormattedMessage());
    }
}
