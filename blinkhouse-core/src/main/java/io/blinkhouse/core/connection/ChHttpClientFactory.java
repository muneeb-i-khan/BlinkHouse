package io.blinkhouse.core.connection;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Factory that builds a fully configured {@link CloseableHttpClient} backed by
 * a {@link org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager}.
 *
 * <p>The returned client is meant to be shared across all operations on a single
 * {@link io.blinkhouse.core.template.ChTemplate} instance, including its
 * {@link io.blinkhouse.core.write.BatchWriter} children.
 *
 * <p>Callers are responsible for closing the returned client when the template
 * is closed. {@code ChTemplate.close()} handles this.
 */
public final class ChHttpClientFactory {

    private ChHttpClientFactory() {
    }

    /**
     * Creates a new {@link CloseableHttpClient} from the given pool config.
     *
     * @param config pool configuration
     * @return a ready-to-use, thread-safe HTTP client
     */
    public static CloseableHttpClient create(ChConnectionPoolConfig config) {
        ConnectionConfig connConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.of(
                config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .setSocketTimeout(Timeout.of(
                config.socketTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .setValidateAfterInactivity(TimeValue.of(
                config.validateAfterInactivity().toMillis(), TimeUnit.MILLISECONDS))
            .build();

        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager cm =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(config.maxTotal())
                .setMaxConnPerRoute(config.maxPerRoute())
                .setDefaultConnectionConfig(connConfig)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(
                Timeout.of(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .setResponseTimeout(
                Timeout.of(config.socketTimeout().toMillis(), TimeUnit.MILLISECONDS))
            .build();

        if (!config.evictorInterval().isZero()) {
            return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(
                    TimeValue.of(config.idleEvictAfter().toMillis(), TimeUnit.MILLISECONDS))
                .build();
        }

        return HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }
}
