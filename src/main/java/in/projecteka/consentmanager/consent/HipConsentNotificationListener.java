package in.projecteka.consentmanager.consent;

import in.projecteka.consentmanager.DestinationsConfig;
import in.projecteka.consentmanager.MessageListenerContainerFactory;
import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.ConsentArtefactNotifier;
import in.projecteka.consentmanager.common.CentralRegistry;
import in.projecteka.consentmanager.consent.model.HIPConsentArtefactRepresentation;
import in.projecteka.consentmanager.consent.model.request.HIPNotificationRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.UUID;

import static in.projecteka.consentmanager.ConsentManagerConfiguration.HIP_CONSENT_NOTIFICATION_QUEUE;

@AllArgsConstructor
public class HipConsentNotificationListener {
    private static final Logger logger = LoggerFactory.getLogger(HipConsentNotificationListener.class);
    private final MessageListenerContainerFactory messageListenerContainerFactory;
    private final DestinationsConfig destinationsConfig;
    private final Jackson2JsonMessageConverter converter;
    private final ConsentArtefactNotifier consentArtefactNotifier;
    private final CentralRegistry centralRegistry;

    @PostConstruct
    public void subscribe() throws ClientError {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(HIP_CONSENT_NOTIFICATION_QUEUE);

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            try {
                HIPConsentArtefactRepresentation consentArtefact =
                        (HIPConsentArtefactRepresentation) converter.fromMessage(message);
                logger.info("Received notify consent to hip for consent artefact: {}",
                        consentArtefact.getConsentId());

                sendConsentArtefactToHIP(consentArtefact).block();
            } catch (Exception e) {
                throw new AmqpRejectAndDontRequeueException(e.getMessage(),e);
            }
        };
        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    /**
     * @deprecated (We are not directly notifying the HIP, instead using new gateway API v1/consents/hip/notify )
     * **/
    @Deprecated
    private void sendConsentArtefact(HIPConsentArtefactRepresentation consentArtefact) {
        String hipId = consentArtefact.getConsentDetail().getHip().getId();
        getProviderUrl(hipId)
                .flatMap(providerUrl -> sendArtefactTo(consentArtefact, providerUrl))
                .block();
    }

    private Mono<Void> sendArtefactTo(HIPConsentArtefactRepresentation consentArtefact, String providerUrl) {
        return consentArtefactNotifier.sendConsentArtefactTo(consentArtefact, providerUrl);
    }

    private Mono<String> getProviderUrl(String hipId) {
        return centralRegistry.providerWith(hipId).flatMap(provider -> Mono.just(provider.getProviderUrl()));
    }

    private Mono<Void> sendConsentArtefactToHIP(HIPConsentArtefactRepresentation consentArtefact) {
        String hipId = consentArtefact.getConsentDetail().getHip().getId();
        HIPNotificationRequest notificationRequest = HIPNotificationRequest.builder()
                .notification(consentArtefact)
                .requestId(UUID.randomUUID())
                .timestamp(LocalDateTime.now())
                .build();

        return consentArtefactNotifier.sendConsentArtefactToHIP(notificationRequest, hipId);
    }
}
