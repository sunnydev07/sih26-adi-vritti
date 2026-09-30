package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Tier 3: document AI (OCR, field extraction, tamper signals) -&gt; {@code assisted}.
 *
 * <p>Assisted means "a model read the document, but a human still owns the decision".
 * This strategy therefore fetches the evidence, sends it to the AI service, and only
 * reports {@code verified=true} when the model actually returns a confident verdict.
 * Anything short of that returns empty so the chain degrades to officer review.
 *
 * <p>Failures stay soft, because an assist tier must never break verification, but
 * they are logged. A rejected call — most often a missing or stale
 * {@code app.ai-service.token} — was otherwise indistinguishable from an unreadable
 * document, and switched the whole tier off with no trace.
 *
 * <p>The previous implementation returned a hard-coded 0.72 confidence for any
 * non-null {@code evidenceRef}, which meant a typo'd or arbitrary string was accepted
 * as a machine-verified claim.
 */
@Component
public class DocAiStrategy implements VerificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(DocAiStrategy.class);

    /** Below this the model is not confident enough to pre-fill an officer worksheet. */
    private static final double CONFIDENCE_FLOOR = 0.60;

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_DETAIL_CHARS = 200;

    private final WebClient aiServiceClient;
    private final Duration fetchTimeout;
    private final double confidenceFloor;

    public DocAiStrategy(@Qualifier("aiServiceClient") WebClient aiServiceClient,
        @Value("${app.verification.evidence-fetch-timeout-seconds:20}") long fetchTimeoutSeconds,
        @Value("${app.verification.doc-ai-confidence-floor:" + CONFIDENCE_FLOOR + "}")
        double confidenceFloor) {
        this.aiServiceClient = aiServiceClient;
        this.fetchTimeout = Duration.ofSeconds(fetchTimeoutSeconds);
        this.confidenceFloor = confidenceFloor;
    }

    @Override
    public String tier() {
        return "assisted";
    }

    @Override
    public Optional<TierResult> attempt(VerificationAttempt attempt) {
        VerifyRequest req = attempt.request();
        String evidenceRef = req.evidenceRef();
        if (evidenceRef == null || evidenceRef.isBlank()) {
            // No document to read: this tier cannot decide.
            return Optional.empty();
        }
        if (!evidenceRef.startsWith("http://") && !evidenceRef.startsWith("https://")) {
            return Optional.empty();
        }
        try {
            byte[] bytes = fetch(evidenceRef);
            Double confidence = parse(bytes, req.claimType());
            if (confidence == null || confidence < confidenceFloor) {
                return Optional.empty();
            }
            return Optional.of(new TierResult(true, confidence, "doc-ai", "ocr+tamper-check",
                "Document parsed at " + confidence + " confidence", null));
        } catch (WebClientResponseException e) {
            // The AI service answered, and said no. That is not the same as "this
            // document is hard to read", so it earns a line in the log: an unset or
            // stale service token turns off this entire tier silently.
            HttpRequest request = e.getRequest();
            log.warn("AI service rejected the document call status={} method={} path={} detail={}",
                e.getStatusCode().value(), request == null ? "-" : request.getMethod().name(),
                request == null ? "-" : request.getURI().getPath(), detail(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            // Model unavailable or evidence unreadable -> fall through to a human.
            return Optional.empty();
        }
    }

    private byte[] fetch(String url) {
        return aiServiceClient.get()
            .uri(java.net.URI.create(url))
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(fetchTimeout)
            .block(fetchTimeout);
    }

    @SuppressWarnings("unchecked")
    private Double parse(byte[] evidence, String claimType) {
        if (evidence == null || evidence.length == 0 || evidence.length > MAX_EVIDENCE_BYTES) {
            return null;
        }
        HttpHeaders textHeaders = new HttpHeaders();
        textHeaders.setContentType(MediaType.TEXT_PLAIN);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
            .name("file")
            .filename("evidence")
            .build());

        MultiValueMap<String, HttpEntity<?>> form = new LinkedMultiValueMap<>();
        form.add("claim_type",
            new HttpEntity<>(claimType == null ? "generic" : claimType, textHeaders));
        form.add("file", new HttpEntity<>(new ByteArrayResource(evidence), fileHeaders));

        Map<String, Object> response = aiServiceClient.post()
            .uri("/docai/parse")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(form))
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(TIMEOUT)
            .block(TIMEOUT);

        if (response == null || !(response.get("confidence") instanceof Number n)) {
            return null;
        }
        return n.doubleValue();
    }

    /** The downstream error, flattened and capped so a stray HTML page cannot flood the log. */
    private static String detail(WebClientResponseException e) {
        String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return "-";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_DETAIL_CHARS
            ? flat : flat.substring(0, MAX_DETAIL_CHARS) + "...";
    }
}
