package com.tradingassistant.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guardrails for two product invariants that must not regress:
 * no AI/ML dependency and no automatic order execution wired into the live or
 * HTTP application.
 */
class ArchitectureGuardrailTest {

    private static final List<String> AI_MARKERS = List.of(
            "openai", "langchain", "tensorflow", "pytorch", "deeplearning4j",
            "nd4j", "onnxruntime", "onnx", "weka", "encog", "h2o.ai", "ai.djl",
            "com.anthropic", "generativeai", "spring-ai", "ollama", "huggingface",
            "com.cohere", "mistral", "bedrock");

    private static final List<String> ENGINE_PACKAGES = List.of(
            "indicator", "intelligence", "options", "strategy",
            "qualification", "risk", "pipeline", "recommendation", "monitoring");

    @Test
    void noAiOrMlDependencyOrImportIsPresent() throws IOException {
        String pom = Files.readString(path("pom.xml")).toLowerCase(Locale.ROOT);
        assertThat(AI_MARKERS).allSatisfy(marker ->
                assertThat(pom).doesNotContain(marker));

        for (Path source : mainSources()) {
            String content = Files.readString(source).toLowerCase(Locale.ROOT);
            assertThat(AI_MARKERS).allSatisfy(marker -> assertThat(content)
                    .as("AI/ML marker in %s", source)
                    .doesNotContain(marker));
        }
    }

    @Test
    void enginePackagesContainNoRandomnessOrWallClockCalls() throws IOException {
        for (Path source : mainSources()) {
            String packagePath = source.toString().replace('\\', '/');
            if (ENGINE_PACKAGES.stream().noneMatch(packagePath::contains)) {
                continue;
            }
            String content = Files.readString(source);
            assertThat(content)
                    .as("randomness in %s", source)
                    .doesNotContain("java.util.Random")
                    .doesNotContain("ThreadLocalRandom")
                    .doesNotContain("Math.random")
                    .doesNotContain("Instant.now()")
                    .doesNotContain("LocalDate.now()")
                    .doesNotContain("LocalDateTime.now()")
                    .doesNotContain("System.currentTimeMillis()");
        }
    }

    @Test
    void noHttpControllerCanSubmitAnOrderOrExposeAnExecutionEndpoint() throws IOException {
        for (Path source : mainSources("platform/api")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("execution dependency in %s", source)
                    .doesNotContain("com.tradingassistant.execution")
                    .doesNotContain("@PostMapping(\"/orders")
                    .doesNotContain("@RequestMapping(\"/api/v1/orders")
                    .doesNotContain("/portfolio")
                    .doesNotContain("/positions");
        }
    }

    @Test
    void liveSupervisorDoesNotDependOnExecutionOrBrokerOrderApis() throws IOException {
        for (Path source : mainSources("platform/live")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("execution dependency in %s", source)
                    .doesNotContain("com.tradingassistant.execution")
                    .doesNotContain("BrokerAdapter")
                    .doesNotContain("ExecutionService")
                    .doesNotContain("ExecutionEngine");
        }
    }

    @Test
    void recommendationDoesNotDependOnExecutionBrokerAndroidOrHttp() throws IOException {
        for (Path source : mainSources("recommendation")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("forbidden dependency in %s", source)
                    .doesNotContain("com.tradingassistant.execution")
                    .doesNotContain("com.tradingassistant.broker")
                    .doesNotContain("android.")
                    .doesNotContain("java.net.http")
                    .doesNotContain("RestTemplate")
                    .doesNotContain("WebClient")
                    .doesNotContain("HttpClient")
                    .doesNotContain("placeOrder")
                    .doesNotContain("submitOrder")
                    .doesNotContain("OrderRepository")
                    .doesNotContain("@RestController")
                    .doesNotContain("@GetMapping")
                    .doesNotContain("@PostMapping");
        }
    }

    @Test
    void recommendationDoesNotRecalculateIndicatorsOrMarketAnalytics() throws IOException {
        for (Path source : mainSources("recommendation")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("recalculation dependency in %s", source)
                    .doesNotContain("com.tradingassistant.indicator.calculator")
                    .doesNotContain("com.tradingassistant.indicator.engine")
                    .doesNotContain("com.tradingassistant.options.engine")
                    .doesNotContain("com.tradingassistant.risk.engine")
                    .doesNotContain("com.tradingassistant.risk.math")
                    .doesNotContain("BlackScholes")
                    .doesNotContain("GreeksCalculator");
        }
    }

    @Test
    void monitoringDoesNotDependOnExecutionBrokerAndroidOrHttpClients() throws IOException {
        for (Path source : mainSources("monitoring")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("forbidden dependency in %s", source)
                    .doesNotContain("com.tradingassistant.execution")
                    .doesNotContain("com.tradingassistant.broker")
                    .doesNotContain("android.")
                    .doesNotContain("java.net.http")
                    .doesNotContain("RestTemplate")
                    .doesNotContain("WebClient")
                    .doesNotContain("HttpClient")
                    .doesNotContain("placeOrder")
                    .doesNotContain("submitOrder")
                    .doesNotContain("OrderRepository");
        }
    }

    @Test
    void monitoringDoesNotRecalculateIndicatorsOptionsOrRecommendations() throws IOException {
        for (Path source : mainSources("monitoring")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("recalculation dependency in %s", source)
                    .doesNotContain("com.tradingassistant.indicator.calculator")
                    .doesNotContain("com.tradingassistant.indicator.engine")
                    .doesNotContain("com.tradingassistant.options.engine")
                    .doesNotContain("com.tradingassistant.risk.engine")
                    .doesNotContain("com.tradingassistant.risk.math")
                    .doesNotContain("com.tradingassistant.recommendation.engine")
                    .doesNotContain("RecommendationEngine")
                    .doesNotContain("BlackScholes")
                    .doesNotContain("GreeksCalculator");
        }
    }

    @Test
    void monitoringIsReadOnlyAndItsDeterministicCoreHasNoClockOrRandomness() throws IOException {
        for (Path source : mainSources("monitoring")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("write endpoint in %s", source)
                    .doesNotContain("@PostMapping")
                    .doesNotContain("@PutMapping")
                    .doesNotContain("@PatchMapping")
                    .doesNotContain("@DeleteMapping");
        }
        for (Path source : List.of(mainSources("monitoring/engine"),
                mainSources("monitoring/domain"), mainSources("monitoring/validation"))
                .stream().flatMap(List::stream).toList()) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("clock or randomness in deterministic monitoring source %s", source)
                    .doesNotContain("Clock")
                    .doesNotContain("java.util.Random")
                    .doesNotContain("ThreadLocalRandom")
                    .doesNotContain("Math.random")
                    .doesNotContain("Instant.now")
                    .doesNotContain("System.currentTimeMillis");
        }
    }

    @Test
    void actionableWorkflowHasNoExecutionOrBrokerOrderPath() throws IOException {
        List<Path> sources = Stream.concat(mainSources("platform/trade").stream(),
                        mainSources("platform/instruments").stream())
                .toList();
        for (Path source : sources) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("execution or order dependency in %s", source)
                    .doesNotContain("com.tradingassistant.execution")
                    .doesNotContain("BrokerAdapter")
                    .doesNotContain("ExecutionService")
                    .doesNotContain("ExecutionEngine")
                    .doesNotContain("placeOrder")
                    .doesNotContain("submitOrder")
                    .doesNotContain("OrderRepository")
                    .doesNotContain("android.");
        }
    }

    @Test
    void platformConfigurationDoesNotWireExecutionBeans() throws IOException {
        for (Path source : mainSources("platform/config")) {
            String content = Files.readString(source);
            assertThat(content)
                    .as("execution bean in %s", source)
                    .doesNotContain("ExecutionEngine")
                    .doesNotContain("ExecutionService")
                    .doesNotContain("BrokerAdapter")
                    .doesNotContain("ExecutionConfig");
        }
    }

    private static List<Path> mainSources() throws IOException {
        return mainSources("");
    }

    private static List<Path> mainSources(String suffix) throws IOException {
        Path root = path("src/main/java/com/tradingassistant");
        Path target = suffix.isBlank() ? root : root.resolve(suffix);
        if (!Files.exists(target)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(target)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path path(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("backend").resolve(relative);
    }
}
