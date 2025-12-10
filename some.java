package com.example.advice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;

@ControllerAdvice
public class UtcTimestampResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper mapper;
    // Use America/New_York so DST is handled. Change if your DB timezone is truly fixed EST.
    private final ZoneId sourceZone = ZoneId.of("America/New_York");
    private final ZoneId targetZone = ZoneOffset.UTC;

    // Some common formatters (add more if your DB returns different formatted strings)
    private final DateTimeFormatter[] formatters = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_INSTANT, // e.g. 2025-12-10T10:15:30Z
            DateTimeFormatter.ISO_OFFSET_DATE_TIME, // e.g. 2025-12-10T05:15:30-05:00
            DateTimeFormatter.ISO_LOCAL_DATE_TIME, // e.g. 2025-12-10T05:15:30
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), // common DB format
            DateTimeFormatter.ISO_LOCAL_DATE // fallback date-only
    };

    public UtcTimestampResponseAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // Only apply when Jackson will write the response
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body == null) return null;

        // Convert the body to a JsonNode tree so we can walk & modify it
        JsonNode root = mapper.valueToTree(body);

        JsonNode processed = processNode(root);

        // Returning JsonNode is fine; Jackson converter will serialize it
        return processed;
    }

    private JsonNode processNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // We must iterate over a copy of field entries because we'll add fields while iterating
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            // Collect fields first to avoid concurrent modification
            Map.Entry<String, JsonNode>[] entries = toArray(it);
            for (Map.Entry<String, JsonNode> entry : entries) {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();

                // Recurse into nested structures first
                if (value.isObject() || value.isArray()) {
                    processNode(value); // modifies in place
                    continue;
                }

                // Try detect timestamps in textual fields
                if (value.isTextual()) {
                    String text = value.asText();
                    Instant instant = parseToInstant(text);
                    if (instant != null) {
                        // add new field with _utc suffix
                        String utcString = instant.toString(); // ISO_INSTANT
                        obj.put(fieldName + "_utc", utcString);
                    }
                } else if (value.isNumber()) {
                    // Could be epoch seconds or milliseconds - try to detect
                    long num = value.asLong();
                    Instant instant = parseEpochToInstant(num);
                    if (instant != null) {
                        obj.put(fieldName + "_utc", instant.toString());
                    }
                }
            }
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode item = arr.get(i);
                JsonNode processed = processNode(item);
                // replace with processed (sometimes same)
                arr.set(i, processed);
            }
            return arr;
        } else {
            // primitives: nothing to do
            return node;
        }
    }

    // Helper to convert epoch-like numbers to Instant.
    // Heuristic: if num > 10^12 => ms; if between 10^9 and 10^12 => seconds (but could be ms)
    private Instant parseEpochToInstant(long num) {
        if (num <= 0) return null;
        if (num > 1_000_000_000_000L) { // > ~Sat Nov 2001 in ms -> treat as millis
            return Instant.ofEpochMilli(num).atZone(sourceZone).toInstant();
        } else if (num > 1_000_000_000L) { // seconds since epoch (after 2001)
            return Instant.ofEpochSecond(num).atZone(sourceZone).toInstant();
        } else {
            return null; // too small to be a reasonable epoch
        }
    }

    // Try parsing textual timestamps with multiple formatters and fallback to epoch parsing if numeric string
    private Instant parseToInstant(String text) {
        if (text == null || text.isBlank()) return null;

        // If numeric string, try epoch
        if (text.matches("^\\d+$")) {
            try {
                long v = Long.parseLong(text);
                return parseEpochToInstant(v);
            } catch (NumberFormatException ignored) {}
        }

        // Try parsing using known formatters
        for (DateTimeFormatter fmt : formatters) {
            try {
                if (fmt == DateTimeFormatter.ISO_INSTANT) {
                    Instant inst = Instant.parse(text);
                    return inst.atZone(sourceZone).withZoneSameInstant(targetZone).toInstant(); // if ISO_INSTANT already has zone, this is ok
                } else if (fmt == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    OffsetDateTime odt = OffsetDateTime.parse(text, fmt);
                    return odt.toInstant();
                } else if (fmt == DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    // no offset -> treat as local date-time in sourceZone
                    LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                    return ldt.atZone(sourceZone).withZoneSameInstant(targetZone).toInstant();
                } else if (fmt.getZone() == null) {
                    // custom pattern like yyyy-MM-dd HH:mm:ss
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                        return ldt.atZone(sourceZone).withZoneSameInstant(targetZone).toInstant();
                    } catch (DateTimeParseException ignored) {}
                } else {
                    // fallback
                    TemporalAccessor ta = fmt.parse(text);
                    Instant inst = Instant.from(ta);
                    return inst;
                }
            } catch (DateTimeParseException ex) {
                // try next
            } catch (Exception ex) {
                // ignore and continue
            }
        }

        // Last resort: try parsing with OffsetDateTime parse (handles many ISO forms)
        try {
            OffsetDateTime odt = OffsetDateTime.parse(text);
            return odt.toInstant();
        } catch (DateTimeParseException ignored) {}

        return null; // couldn't parse
    }

    // utility to copy iterator content into array
    @SuppressWarnings("unchecked")
    private Map.Entry<String, JsonNode>[] toArray(Iterator<Map.Entry<String, JsonNode>> it) {
        // naive collection to array to avoid concurrent modification when adding fields
        java.util.List<Map.Entry<String, JsonNode>> list = new java.util.ArrayList<>();
        it.forEachRemaining(list::add);
        return list.toArray(new Map.Entry[0]);
    }
}
