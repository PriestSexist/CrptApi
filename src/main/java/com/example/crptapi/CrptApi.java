package com.example.crptapi;

import com.google.gson.*;
import lombok.*;
import org.springframework.http.HttpEntity;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final int threadsLimit;
    private final long interval;

    // Atomic классы thread-safe
    private final AtomicInteger threadsCount;
    private long lastAskTime;
    private final ReentrantLock lock;
    private final HttpClient httpClient;
    private final Gson gson;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.threadsLimit = requestLimit;
        this.interval = timeUnit.toMillis(3);
        this.threadsCount = new AtomicInteger(0);
        this.lastAskTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
        this.httpClient = HttpClient.newBuilder().build();
        this.gson = new GsonBuilder().registerTypeAdapter(LocalDate.class, new LocalDateTypeAdapter()).setPrettyPrinting().create();
    }

    public String postDocument(DocumentDto documentDto, String signature) {
        if (apiCallsCountCheck()) {

            String jsonDocument = gson.toJson(documentDto);
            URI uri;
            HttpResponse.BodyHandler<String > responseBodyHandler = HttpResponse.BodyHandlers.ofString();

            try {
                uri = new URI(URL);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            try {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .method("POST", HttpRequest.BodyPublishers.ofString(jsonDocument))
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Signature", signature)
                        .build();

                HttpResponse<String> response = httpClient.send(request, responseBodyHandler);

                return response.body();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            
        } else {
            System.out.println("Превышено максимально количество запросов к API за единицу времени. Вызов заблокирован");
        }
        return null;
    }

    private boolean apiCallsCountCheck() {
        lock.lock();
        long currentTime;
        try {
            currentTime = System.currentTimeMillis();
            if (currentTime - lastAskTime > interval) {
                threadsCount.set(0);
                lastAskTime = currentTime;
            }
            return threadsCount.getAndIncrement() < threadsLimit;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {

        CrptApi crptApi = new CrptApi(TimeUnit.MILLISECONDS, 3);

        ProductDto productDto = ProductDto.builder()
                .certificateDocument("certificateDocument")
                .certificateDocumentDate(LocalDate.now())
                .certificateDocumentNumber("certificateDocumentNumber")
                .ownerInn("ownerInn")
                .producerInn("producerInn")
                .productionDate(LocalDate.now())
                .tnvedCode("tnvedCode")
                .uituCode("uituCode")
                .build();

        DocumentDto documentDto = DocumentDto.builder()
                .descriptionDto(new DescriptionDto("description"))
                .docId("docId")
                .docStatus("docStatus")
                .docType(DocType.LP_INTRODUCE_GOODS)
                .importRequest(true)
                .ownerInn("ownerInn")
                .participantInn("participantInn")
                .producerInn("producerInn")
                .productionDate(LocalDate.now())
                .productionType("productionType")
                .products(List.of(productDto))
                .regDate(LocalDate.now())
                .regNumber("regNumber")
                .build();

        System.out.println("Dto на отправку: " + documentDto);

        System.out.println(crptApi.postDocument(documentDto, "signature"));
    }

    public static class LocalDateTypeAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public JsonElement serialize(final LocalDate date, final Type typeOfSrc,
                                     final JsonSerializationContext context) {
            return new JsonPrimitive(date.format(formatter));
        }

        @Override
        public LocalDate deserialize(final JsonElement json, final Type typeOfT,
                                     final JsonDeserializationContext context) throws JsonParseException {
            return LocalDate.parse(json.getAsString(), formatter);
        }
    }

    public enum DocType {
        LP_INTRODUCE_GOODS
    }

    @Data
    @Builder
    public static class DescriptionDto {
        private final String participantInn;
    }

    @Data
    @Builder
    private static class DocumentDto {
        private final DescriptionDto descriptionDto;
        private final String docId;
        private final String docStatus;
        private final DocType docType;
        private final boolean importRequest;
        private final String ownerInn;
        private final String participantInn;
        private final String producerInn;
        private final LocalDate productionDate;
        private final String productionType;
        private final List<ProductDto> products;
        private final LocalDate regDate;
        private final String regNumber;
    }

    @Data
    @Builder
    private static class ProductDto {
        private final String certificateDocument;
        private final LocalDate certificateDocumentDate;
        private final String certificateDocumentNumber;
        private final String ownerInn;
        private final String producerInn;
        private final LocalDate productionDate;
        private final String tnvedCode;
        private final String uit_Code;
        private final String uituCode;
    }
}
