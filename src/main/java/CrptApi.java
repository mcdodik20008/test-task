import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {

    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final int requestLimit;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        scheduledResetCounter();
    }

    private void scheduledResetCounter() {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (requestCounter) {
                requestCounter.set(0);
            }
        }, 2, 2, timeUnit);
    }


    public <T> String createJson(T document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create JSON. Object name:" + document.getClass(), e);
        }
    }

    private String sendPostRequest(Map<String, String> body) {
        try {
            waitForPermission();
            requestCounter.incrementAndGet();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.get("body")))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            requestCounter.decrementAndGet();
            synchronized (requestCounter) {
                requestCounter.notifyAll();
            }
        }
    }

    private void waitForPermission() throws InterruptedException {
        synchronized (requestCounter) {
            while (requestCounter.get() >= requestLimit) {
                requestCounter.wait();
            }
        }
    }

    @SneakyThrows
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1);
        Document doc = new Document();
        Runnable task = () -> {
            String json = api.createJson(doc);
            Map<String, String> body = Map.of("body", json);
            String s = api.sendPostRequest(body);
            System.out.println(s);
        };
        for (int i = 0; i < 100; i++) {
            new Thread(task).start();
        }
    }


    @Getter
    @Setter
    static class Document {
        private String regNumber = "123";
        private LocalDate productionDate = LocalDate.now();
        private Description description = new Description();
        private String docType = "Type";
        private String docid = "UUID2103-1245235";
        private String ownerInn = "888333";
        private List<Product> products = new ArrayList<>();
        private LocalDate regDate = LocalDate.now();
        private String participantInn = "777333111";
        private String docStatus = "GOOD";
        private boolean importRequest = false;
        private String productionType = "ttt";
        private String producerInn= "66666333111";
    }

    @Getter
    @Setter
    static class Description {
        private String participantInn = "33211";
    }

    @Getter
    @Setter
    static class Product {
        private String uituCode;
        private LocalDate certificateDocumentDate;
        private LocalDate productionDate;
        private String certificateDocumentNumber;
        private String tnvedCode;
        private String certificateDocument;
        private String producerInn;
        private String ownerInn;
        private String uitCode;
    }

}