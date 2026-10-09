package in.adivritti.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse.DuplicateFlag;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * Concurrent first-time resolves for the same person or caller key collide on
 * a unique index and the loser's transaction rolls back. Re-reading the winner
 * inside that same (aborted) transaction cannot work on PostgreSQL, so the
 * controller retries once: the retry replays against the winner's committed
 * scholar, links, or idempotency row instead of answering 500.
 */
class IdentityControllerRaceTest {

    private static IdentityResolveRequest request() {
        return new IdentityResolveRequest(List.of(), "key-1");
    }

    private static IdentityResolveResponse winner(UUID usid) {
        return new IdentityResolveResponse(usid, List.of(), 1.0, false,
            new DuplicateFlag(false, List.of()));
    }

    @Test
    @DisplayName("a lost write race retries once and returns the winner's response")
    void lostRaceRetriesAgainstCommittedRows() {
        UUID winnerUsid = UUID.randomUUID();
        IdentityService service = mock(IdentityService.class);
        when(service.resolve(any(IdentityResolveRequest.class)))
            .thenThrow(new DataIntegrityViolationException("uq_identity_resolution_key"))
            .thenReturn(winner(winnerUsid));
        IdentityController controller =
            new IdentityController(service, new ScholarAccessGuard(true));

        var response = controller.resolve(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().usid()).isEqualTo(winnerUsid);
        verify(service, times(2)).resolve(any(IdentityResolveRequest.class));
    }

    @Test
    @DisplayName("a repeat integrity failure still surfaces instead of retrying forever")
    void repeatFailureSurfaces() {
        IdentityService service = mock(IdentityService.class);
        when(service.resolve(any(IdentityResolveRequest.class)))
            .thenThrow(new DataIntegrityViolationException("uq_identity_resolution_key"));
        IdentityController controller =
            new IdentityController(service, new ScholarAccessGuard(true));

        assertThatThrownBy(() -> controller.resolve(request()))
            .isInstanceOf(DataIntegrityViolationException.class);
        verify(service, times(2)).resolve(any(IdentityResolveRequest.class));
    }
}
