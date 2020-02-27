package in.projecteka.consentmanager.dataflow;

import in.projecteka.consentmanager.common.TokenUtils;
import in.projecteka.consentmanager.dataflow.model.DataFlowRequestResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class DataFlowRequestController {
    private DataFlowRequest dataFlowRequest;

    @PostMapping("/health-information/request")
    public Mono<DataFlowRequestResponse> linkCareContexts(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestBody in.projecteka.consentmanager.dataflow.model.DataFlowRequest dataFlowRequest) {
        String hiuId = TokenUtils.getCallerId(authorization);
        return this.dataFlowRequest.validateDataTransferRequest(hiuId, dataFlowRequest);
    }
}