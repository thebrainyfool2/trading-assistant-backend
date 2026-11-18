package com.tradingassistant.broker.config;

import tools.jackson.databind.ObjectMapper;
import com.tradingassistant.broker.angelone.AngelOneBrokerProvider;
import com.tradingassistant.broker.angelone.AngelOneSessionManager;
import com.tradingassistant.broker.angelone.auth.AngelOneAuthenticator;
import com.tradingassistant.broker.angelone.auth.JwtExpiryParser;
import com.tradingassistant.broker.angelone.auth.TotpCodeGenerator;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.angelone.market.AngelOneMarketDataProvider;
import com.tradingassistant.broker.angelone.ws.AngelOneMarketFeedClient;
import com.tradingassistant.broker.api.BrokerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Wires the SmartAPI adapter only when {@code SMARTAPI_ENABLED=true}. When the
 * flag is absent, the application remains fully operational without broker
 * credentials, which is required for deployments that only expose Phase 1
 * operational endpoints.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class BrokerConfiguration {

    @Bean
    public BrokerProvider angelOneBrokerProvider(AngelOneProperties properties, ObjectMapper objectMapper) {
        properties.validate();

        Clock clock = Clock.systemUTC();
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();

        AngelOneHttpClient httpClient = new AngelOneHttpClient(properties, restClient, objectMapper);
        TotpCodeGenerator totpCodeGenerator = new TotpCodeGenerator(properties.totpSecret());
        JwtExpiryParser jwtExpiryParser = new JwtExpiryParser(objectMapper);
        AngelOneAuthenticator authenticator = new AngelOneAuthenticator(
                httpClient, properties, totpCodeGenerator, jwtExpiryParser, clock);
        AngelOneSessionManager sessionManager = new AngelOneSessionManager(authenticator, properties, clock);
        AngelOneMarketFeedClient feedClient = new AngelOneMarketFeedClient(properties, sessionManager, objectMapper);
        AngelOneMarketDataProvider marketDataProvider = new AngelOneMarketDataProvider(
                httpClient, sessionManager, properties, clock, feedClient);

        return new AngelOneBrokerProvider(authenticator, sessionManager, marketDataProvider);
    }
}
