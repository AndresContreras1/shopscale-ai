package co.gamestore.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rights Ley 1581 gives a person over their own data, as endpoints they can actually use: see
 * what is held, change what they agreed to, take a copy, and ask to be removed.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final ConsentService consents;
    private final PersonalDataService personalData;
    private final AuthService authService;

    @GetMapping("/consents")
    public Map<ConsentPurpose, Boolean> consents(Principal principal) {
        return consents.current(authService.requireByEmail(principal.getName()).getId());
    }

    @PutMapping("/consents")
    public Map<ConsentPurpose, Boolean> updateConsents(@Valid @RequestBody ConsentUpdate update,
                                                       Principal principal, HttpServletRequest request) {
        return consents.update(principal.getName(), update.consents(), request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    /** A copy of everything, as a file, because a right nobody can exercise is not a right. */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(Principal principal) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"my-data.json\"")
                .body(personalData.export(principal.getName()));
    }

    /**
     * Deletion, as far as the law allows. Orders and invoices stay because they have to; the person
     * behind them does not. There is no way back from this.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAccount(Principal principal) {
        personalData.anonymize(principal.getName());
    }

    public record ConsentUpdate(@NotNull Map<ConsentPurpose, Boolean> consents) {
    }
}
