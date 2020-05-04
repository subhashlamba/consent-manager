package in.projecteka.consentmanager.clients;

import in.projecteka.consentmanager.clients.model.Error;
import in.projecteka.consentmanager.clients.model.ErrorCode;
import in.projecteka.consentmanager.clients.model.ErrorRepresentation;
import in.projecteka.consentmanager.clients.model.KeycloakUser;
import in.projecteka.consentmanager.clients.model.Session;
import in.projecteka.consentmanager.clients.properties.IdentityServiceProperties;
import in.projecteka.consentmanager.user.model.KeyCloakError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class IdentityServiceClient {

    private final WebClient.Builder webClientBuilder;

    public IdentityServiceClient(WebClient.Builder webClientBuilder,
                                 IdentityServiceProperties identityServiceProperties) {
        this.webClientBuilder = webClientBuilder;
        this.webClientBuilder.baseUrl(identityServiceProperties.getBaseUrl());
    }

    public Mono<Void> createUser(Session session, KeycloakUser request) {
        String accessToken = String.format("Bearer %s", session.getAccessToken());
        return webClientBuilder.build()
                .post()
                .uri(uriBuilder ->
                        uriBuilder.path("/admin/realms/consent-manager/users").build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), KeycloakUser.class)
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> Mono.error(ClientError.networkServiceCallFailed()))
                .toBodilessEntity()
                .then();
    }

    public Mono<Session> getToken(MultiValueMap<String, String> formData) {
        return webClientBuilder.build()
                .post()
                .uri(uriBuilder ->
                        uriBuilder.path("/realms/consent-manager/protocol/openid-connect/token").build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(httpStatus -> httpStatus.value() == 401 , clientResponse -> {
                    return clientResponse.bodyToMono(KeyCloakError.class)
                            .flatMap(keyCloakError -> {
                                Error.ErrorBuilder errorBuilder = Error.builder().message(keyCloakError.getErrorDescription());
                                if (keyCloakError.getError().equals("1002")) {
                                    errorBuilder.code(ErrorCode.OTP_INVALID);
                                }else if (keyCloakError.getError().equals("1003")) {
                                    errorBuilder.code(ErrorCode.OTP_EXPIRED);
                                }else {
                                    errorBuilder.code(ErrorCode.UNKNOWN_ERROR_OCCURRED);
                                }
                                return  Mono.error(new ClientError(clientResponse.statusCode(), new ErrorRepresentation(errorBuilder.build())));});
                })
                .onStatus(HttpStatus::isError, clientResponse -> Mono.error(ClientError.networkServiceCallFailed()))
                .bodyToMono(Session.class);
    }

    public Mono<Void> logout(MultiValueMap<String, String> formData) {
        return webClientBuilder.build()
                .post()
                .uri(uriBuilder ->
                        uriBuilder.path("/realms/consent-manager/protocol/openid-connect/logout").build())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> Mono.error(ClientError.networkServiceCallFailed()))
                .bodyToMono(Void.class);
    }
}