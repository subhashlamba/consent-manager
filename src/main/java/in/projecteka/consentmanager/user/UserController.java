package in.projecteka.consentmanager.user;

import in.projecteka.consentmanager.user.model.OtpVerification;
import in.projecteka.consentmanager.user.model.PatientRequest;
import in.projecteka.consentmanager.user.model.SignUpSession;
import in.projecteka.consentmanager.user.model.Token;
import in.projecteka.consentmanager.user.model.User;
import in.projecteka.consentmanager.user.model.UserSignUpEnquiry;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.validation.Valid;

import static in.projecteka.consentmanager.common.Constants.V_1_PATIENTS_FIND;
import static org.springframework.http.HttpStatus.CREATED;

@RestController
@AllArgsConstructor
public class UserController {
    private final UserService userService;

    // TODO: Should not return phone number from this API.
    @GetMapping("/users/{userName}")
    public Mono<User> userWith(@PathVariable String userName) {
        return userService.userWith(userName);
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(V_1_PATIENTS_FIND)
    public Mono<Void> userWith(@Valid @RequestBody PatientRequest patientRequest) {
        return userService.user(patientRequest.getQuery().getPatient().getId(),
                patientRequest.getQuery().getRequester(),
                patientRequest.getRequestId());
    }

    @PostMapping("/users/verify")
    @ResponseStatus(CREATED)
    public Mono<SignUpSession> sendOtp(@RequestBody UserSignUpEnquiry request) {
        return userService.sendOtp(request);
    }

    @PostMapping("/users/permit")
    public Mono<Token> permitOtp(@RequestBody OtpVerification request) {
        return userService.verifyOtpForRegistration(request);
    }

    // TODO: should be moved to patients and need to make sure only consent manager service uses it.
    // Not patient themselves
    @GetMapping("/internal/users/{userName}")
    public Mono<User> internalUserWith(@PathVariable String userName) {
        return userService.userWith(userName);
    }
}
