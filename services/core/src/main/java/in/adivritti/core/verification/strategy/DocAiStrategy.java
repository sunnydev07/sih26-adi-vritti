package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
    private final Set<String> allowedHosts;

    public DocAiStrategy(@Qualifier("aiServiceClient") WebClient aiServiceClient,
        @Value("${app.verification.evidence-fetch-timeout-seconds:20}") long fetchTimeoutSeconds,
        @Value("${app.verification.doc-ai-confidence-floor:" + CONFIDENCE_FLOOR + "}")
        double confidenceFloor,
        @Value("${app.verification.evidence-allowed-hosts:govsim,minio}")
        String allowedHostsCsv) {
        this.aiServiceClient = aiServiceClient;
        this.fetchTimeout = Duration.ofSeconds(fetchTimeoutSeconds);
        this.confidenceFloor = confidenceFloor;
        this.allowedHosts = java.util.Arrays.stream(allowedHostsCsv.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String tier() {
        return "assisted";
    }

    @Override
    public Optional<TierResult> attempt(VerificationAttempt attempt) {
        VerifyRequest req = attempt.request();
        String evidenceRef = req.evidenceRef();
        if (!isAllowedEvidenceUrl(evidenceRef, allowedHosts)) {
            // No document to read, or a target this service must not fetch:
            // this tier cannot decide.
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

    /**
     * Whether Core may fetch this evidence URL server-side.
     *
     * <p>{@code evidenceRef} is caller-supplied, so the fetch is SSRF-shaped by
     * construction. The gate fails closed: http(s) only, no credentials in the
     * URL, loopback / link-local (cloud metadata lives at 169.254.169.254) /
     * unspecified / multicast always rejected, and site-local (RFC 1918, ULA)
     * addresses rejected unless the host is on the operator-configured
     * {@code app.verification.evidence-allowed-hosts} list (exact or
     * dot-boundary subdomain — {@code evil-example.gov.in} does not match
     * {@code example.gov.in}). Unresolvable hosts are rejected rather than
     * retried.
     *
     * <p>Residual limitation, stated plainly: the address is checked at gate
     * time and fetched moments later, so a hostile DNS that answers differently
     * per query (rebinding) could still slip a private address through. The
     * allow-list plus the IP-class gate removes the practical attacks —
     * metadata theft, localhost probing, intranet scanning — short of pinning
     * DNS inside the WebClient.
     */
    static boolean isAllowedEvidenceUrl(String ref, Set<String> allowedHosts) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(ref.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        if (h.equals("localhost")) {
            return false;
        }
        for (String allowed : allowedHosts) {
            if (h.equals(allowed) || h.endsWith("." + allowed)) {
                return true;
            }
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(h);
        } catch (UnknownHostException e) {
            return false;
        }
        for (InetAddress a : addrs) {
            if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                || a.isMulticastAddress() || isPrivateAddress(a)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Site-local plus the ranges {@link InetAddress#isSiteLocalAddress} misses:
     * {@code fc00::/7} unique-local IPv6 (Java only knows the deprecated
     * {@code fec0::/10}), and IPv4-mapped IPv6 ({@code ::ffff:10.0.0.1}) whose
     * embedded v4 address is private.
     */
    private static boolean isPrivateAddress(InetAddress a) {
        if (a.isSiteLocalAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 16) {
            if ((b[0] & 0xfe) == 0xfc) {
                return true;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && b[10] == (byte) 0xff && b[11] == (byte) 0xff
                && isPrivateV4(b[12], b[13], b[14], b[15])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivateV4(byte b0, byte b1, byte b2, byte b3) {
        int o0 = b0 & 0xff, o1 = b1 & 0xff;
        if (o0 == 10 || o0 == 127) {
            return true;
        }
        if (o0 == 172 && o1 >= 16 && o1 <= 31) {
            return true;
        }
        if (o0 == 192 && o1 == 168) {
            return true;
        }
        return o0 == 169 && o1 == 254;
    }

    /**
     * Fetch with the size cap enforced DURING the transfer, not after it.
     *
     * <p>The previous {@code bodyToMono(byte[].class)} buffered the entire
     * response and only then compared against {@code MAX_EVIDENCE_BYTES}: a 2 GB
     * response was fully materialised before being rejected. Here the
     * subscription is cancelled the moment the running total passes the cap,
     * and pooled buffers are always released. Returns null past the cap (or on
     * an empty body), which {@link #parse} already treats as unreadable.
     */
    private byte[] fetch(String url) {
        AtomicLong total = new AtomicLong();
        java.util.List<DataBuffer> kept;
        try {
            kept = aiServiceClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .takeUntil(buf -> total.addAndGet(buf.readableByteCount()) > MAX_EVIDENCE_BYTES)
                .collectList()
                .block(fetchTimeout);
        } catch (RuntimeException e) {
            return null;
        }
        if (kept == null || total.get() == 0 || total.get() > MAX_EVIDENCE_BYTES) {
            if (kept != null) {
                kept.forEach(DataBufferUtils::release);
            }
            return null;
        }
        byte[] out = new byte[(int) total.get()];
        int off = 0;
        for (DataBuffer buf : kept) {
            int n = buf.readableByteCount();
            buf.asByteBuffer().get(out, off, n);
            off += n;
            DataBufferUtils.release(buf);
        }
        return out;
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

        if (response == null) {
            return null;
        }
        return validConfidence(response.get("confidence"));
    }

    /**
     * The model supplies {@code confidence} and it flows into
     * {@code claim.confidence}, which the schema constrains to [0, 1]. Only the
     * floor used to be checked: a model returning a percentage ({@code 95}) or
     * {@code NaN} passed the floor, then violated the CHECK at INSERT time and
     * 500'd a <em>successful</em> verification (misreported downstream as a lost
     * idempotency race). Anything outside [0, 1] is an unreadable document.
     */
    static Double validConfidence(Object raw) {
        if (!(raw instanceof Number n)) {
            return null;
        }
        double c = n.doubleValue();
        if (Double.isNaN(c) || c < 0.0 || c > 1.0) {
            log.warn("AI service returned out-of-range confidence {}; treating as unreadable",
                Double.isNaN(c) ? "NaN" : c);
            return null;
        }
        return c;
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
