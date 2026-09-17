package com.mocksupplier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockSupplierController {

    private static final Set<String> SUPPLIERS = Set.of("a", "b");
    private static final Set<String> MODES = Set.of("normal", "error", "no-response", "slow");

    private final Map<String, String> modes = new ConcurrentHashMap<>(Map.of("a", "normal", "b", "normal"));

    // ---- 모드 제어 ----

    @PostMapping(value = "/control/{supplier}/mode", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> setMode(@PathVariable String supplier, @RequestParam String value) {
        if (!SUPPLIERS.contains(supplier) || !MODES.contains(value)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown supplier or mode"));
        }
        modes.put(supplier, value);
        return ResponseEntity.ok(Map.of("supplier", supplier, "mode", value));
    }

    @GetMapping(value = "/control/{supplier}/mode", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getMode(@PathVariable String supplier) {
        if (!SUPPLIERS.contains(supplier)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown supplier"));
        }
        return ResponseEntity.ok(Map.of("supplier", supplier, "mode", modes.get(supplier)));
    }

    // ---- Supplier A ----

    @GetMapping(value = "/a/v1/hotels", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hotelsA() {
        return respondA(A_HOTELS);
    }

    @GetMapping(value = "/a/v1/availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> availabilityA() {
        return respondA(A_AVAILABILITY);
    }

    // ---- Supplier B ----

    @GetMapping(value = "/b/api/properties", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> propertiesB() {
        return respondB(B_PROPERTIES);
    }

    @GetMapping(value = "/b/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchB() {
        return respondB(B_SEARCH);
    }

    // ---- 모드별 응답 ----

    private ResponseEntity<String> respondA(String normalBody) {
        return switch (modes.get("a")) {
            case "error" -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(A_ERROR);
            case "no-response" -> noResponse();
            case "slow" -> slow(normalBody);
            default -> ResponseEntity.ok(normalBody);
        };
    }

    private ResponseEntity<String> respondB(String normalBody) {
        return switch (modes.get("b")) {
            // B 는 장애 상황에서도 HTTP 200 이다
            case "error" -> ResponseEntity.ok(B_ERROR);
            case "no-response" -> noResponse();
            case "slow" -> slow(normalBody);
            default -> ResponseEntity.ok(normalBody);
        };
    }

    private ResponseEntity<String> noResponse() {
        sleep(600_000);
        return ResponseEntity.ok("{}");
    }

    private ResponseEntity<String> slow(String normalBody) {
        sleep(2_000);
        return ResponseEntity.ok(normalBody);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- 고정 응답 (요청 파라미터 무시, 2026-09-01 ~ 2026-09-04 기준) ----

    private static final String A_ERROR = """
            {"error":"SERVICE_UNAVAILABLE","message":"temporarily unavailable"}""";

    private static final String B_ERROR = """
            {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""";

    private static final String A_HOTELS = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypes": [
                    { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypes": [
                    { "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double", "maxOccupancy": 2 }
                  ]
                }
              ]
            }
            """;

    private static final String A_AVAILABILITY = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypeCode": "DLX-TWN",
                  "roomTypeName": "Deluxe Twin",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                    { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
                    { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypeCode": "STD-DBL",
                  "roomTypeName": "Standard Double",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 2, "nightlyRate": 88000, "taxAmount": 8800 },
                    { "date": "2026-09-02", "remainingRooms": 0, "nightlyRate": 99000, "taxAmount": 9900 },
                    { "date": "2026-09-03", "remainingRooms": 4, "nightlyRate": 88000, "taxAmount": 8800 }
                  ]
                }
              ]
            }
            """;

    private static final String B_PROPERTIES = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "rooms": [
                      { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
                    ]
                  }
                ]
              }
            }
            """;

    private static final String B_SEARCH = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "roomId": "R-401",
                    "roomName": "Deluxe Twin Room",
                    "maxOccupancy": 2,
                    "breakfastIncluded": true,
                    "currency": "KRW",
                    "totalPrice": 452000,
                    "taxIncluded": true,
                    "inventory": [
                      { "date": "2026-09-01", "remainingRooms": 3 },
                      { "date": "2026-09-02", "remainingRooms": 1 },
                      { "date": "2026-09-03", "remainingRooms": 5 }
                    ]
                  }
                ]
              }
            }
            """;
}
