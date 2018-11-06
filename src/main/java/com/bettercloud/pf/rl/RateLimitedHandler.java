package com.bettercloud.pf.rl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RateLimitedHandler {

    public static final Pattern RATE_LIMIT_PATTERN = Pattern.compile("^(\\d+)/([smh])$");
    public static final String SECOND_BUCKET = "s";
    public static final String MINUTE_BUCKET = "m";
    public static final String HOUR_BUCKET = "h";
    public static final long SECOND_MULTIPLIER = 1000L;
    public static final long MINUTE_MULTIPLIER = 1000L * 60L;
    public static final long HOUR_MULTIPLIER = 1000L * 60L * 60L;

    private final DataProviderService dataProviderService;
    private final Cache<String, AtomicInteger> rlSecondCache;
    private final Cache<String, AtomicInteger> rlMinuteCache;
    private final Cache<String, AtomicInteger> rlHourCache;

    private final Counter totalCounter;
    private final Counter exceededCounter;

    public RateLimitedHandler(DataProviderService dataProviderService, MeterRegistry meterRegistry) {
        this.dataProviderService = dataProviderService;

        this.rlSecondCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build();
        this.rlMinuteCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .build();
        this.rlHourCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build();

        this.totalCounter = meterRegistry.counter("req.total");
        this.exceededCounter = meterRegistry.counter("req.exceeded");
    }

    public Mono<ServerResponse> rateLimitHandler(ServerRequest request) {
        String path = request.path();
        if (!dataProviderService.supports(path)) {
            return ServerResponse.notFound().build();
        }

        Optional<String> rateLimit = request.queryParam("rateLimit");
        if (!rateLimit.isPresent()) {
            return ServerResponse.badRequest()
                    .header("X-ERROR", "missing `rateLimit` query parameter")
                    .build();
        }

        String rawRateLimit = rateLimit.get();
        Matcher m = RATE_LIMIT_PATTERN.matcher(rawRateLimit);
        if (!m.find()) {
            return ServerResponse.badRequest()
                    .header("X-ERROR", "invalid `rateLimit` query parameter: " + rawRateLimit)
                    .build();
        }
        int quota = Integer.parseInt(m.group(1));
        String bucket = m.group(2);
        String groupId = request.queryParam("rateLimitGroup").orElse("global");

        long limitValue;
        long resetsAt;
        long now = System.currentTimeMillis();
        Cache<String, AtomicInteger> rlCache;

        switch (bucket) {
            case HOUR_BUCKET: {
                limitValue = now / HOUR_MULTIPLIER;
                resetsAt = (limitValue + 1) * HOUR_MULTIPLIER;
                rlCache = rlHourCache;
                break;
            }
            case MINUTE_BUCKET: {
                limitValue = now / MINUTE_MULTIPLIER;
                resetsAt = (limitValue + 1) * MINUTE_MULTIPLIER;
                rlCache = rlMinuteCache;
                break;
            }
            default:
            case SECOND_BUCKET: {
                limitValue = now / SECOND_MULTIPLIER;
                resetsAt = (limitValue + 1) * SECOND_MULTIPLIER;
                rlCache = rlSecondCache;
                break;
            }
        }

        String rateLimitKey = String.format("%d##%s##%s", limitValue, path, groupId);

        AtomicInteger currentQuotaUsage = rlCache.getIfPresent(rateLimitKey);
        if (currentQuotaUsage == null) {
            currentQuotaUsage = new AtomicInteger(0);
            rlCache.put(rateLimitKey, currentQuotaUsage);
        }
        int currQuota = currentQuotaUsage.incrementAndGet();

        boolean exceeded = currQuota > quota;
        long waitUntil = -1;
        long waitMs = -1;

        if (exceeded) {
            waitUntil = resetsAt;
            waitMs = waitUntil - now;
        }

        ServerResponse.BodyBuilder res = ServerResponse.ok().contentType(MediaType.APPLICATION_JSON_UTF8)
                .header("X-RATE-LIMIT-RAW", rawRateLimit)
                .header("X-RATE-LIMIT-GROUP", groupId)
                .header("X-RATE-LIMIT-KEY", rateLimitKey)
                .header("X-RATE-LIMIT-QUOTA-TOTAL", Integer.toString(quota))
                .header("X-RATE-LIMIT-QUOTA-CURR", Integer.toString(currQuota))
                .header("X-RATE-LIMIT-QUOTA-REMAINING", Integer.toString(quota - currQuota))
                .header("X-RATE-LIMIT-RESETS-AT", Long.toString(resetsAt))
                .header("X-RATE-LIMIT-WAIT-UNTIL", Long.toString(waitUntil))
                .header("X-RATE-LIMIT-WAIT-MS", Long.toString(waitMs));

        totalCounter.increment();
        if (exceeded) {
            exceededCounter.increment();
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-ERROR", "quota exceeded")
                    .build();
        }
        return res.body(BodyInserters.fromObject(dataProviderService.get(path)));
    }
}
