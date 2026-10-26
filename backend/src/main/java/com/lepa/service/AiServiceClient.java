package com.lepa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class AiServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AiServiceClient(@Value("${lepa.ai-service-url}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        log.info("[LEPA] AI service configured at {} using MVC RestTemplate bridge", this.baseUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(MultipartFile file, String eventType) throws IOException {
        String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload.bin"
                : file.getOriginalFilename();
        String requestedEvent = eventType == null || eventType.isBlank() ? "AUTO" : eventType;

        log.info("[LEPA] Sending document to AI service name={} bytes={} contentType={} requestedEvent={}",
                filename, file.getSize(), file.getContentType(), requestedEvent);

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MediaType fileType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            try {
                fileType = MediaType.parseMediaType(file.getContentType());
            } catch (IllegalArgumentException ignored) {
                log.warn("[LEPA] Unrecognized upload content type '{}'; using application/octet-stream",
                        file.getContentType());
            }
        }

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(fileType);
        fileHeaders.setContentDispositionFormData("file", filename);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);

        HttpHeaders eventHeaders = new HttpHeaders();
        eventHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> eventPart = new HttpEntity<>(requestedEvent, eventHeaders);

        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", filePart);
        multipart.add("event_type", eventPart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(multipart, requestHeaders);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/analyze",
                request,
                Map.class
        );

        Map<String, Object> result = response.getBody();
        log.info("[LEPA] AI analysis received event={} provider={} obligationCount={} recommendationCount={}",
                result == null ? null : result.get("eventType"),
                result == null ? null : result.get("provider"),
                sizeOf(result == null ? null : result.get("obligations")),
                sizeOf(result == null ? null : result.get("recommendations")));
        return result;
    }

    private int sizeOf(Object value) {
        return value instanceof java.util.Collection<?> collection ? collection.size() : 0;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(Map<String, Object> body) {
        log.info("[LEPA] Sending journey-aware Copilot request");
        return restTemplate.postForObject(baseUrl + "/chat", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> health() {
        return restTemplate.getForObject(baseUrl + "/health", Map.class);
    }
}
