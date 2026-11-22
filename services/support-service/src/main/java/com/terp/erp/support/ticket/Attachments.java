package com.terp.erp.support.ticket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Attachments of tickets and replies: media-service file ids of the ticket's company, checked with the caller's token
 * and kept as JSON [{fileId, name}] on the row. Downloading goes through media-service (its own permission check).
 */
@Component
public class Attachments {
    public static final int MAX = 10;

    public record Attachment(String fileId, String name) {
    }

    private static final TypeReference<List<Attachment>> LIST = new TypeReference<>() {
    };

    private final MediaClient media;
    private final ObjectMapper mapper;

    public Attachments(MediaClient media, ObjectMapper mapper) {
        this.media = media;
        this.mapper = mapper;
    }

    /** Checks every id (duplicates dropped) against media-service: SUP_008 above MAX, SUP_007 for a foreign file. */
    public List<Attachment> resolve(List<Long> fileIds, Long companyId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>(fileIds);
        ids.remove(null);
        if (ids.size() > MAX) {
            throw ApiException.badRequest(ESupportError.SUP_008, MAX);
        }
        List<Attachment> out = new ArrayList<>();
        for (Long id : ids) {
            MediaClient.MediaFile f = media.requireCompanyFile(id, companyId);
            out.add(new Attachment(String.valueOf(id), f.name()));
        }
        return out;
    }

    public String write(List<Attachment> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("attachments", e);
        }
    }

    public List<Attachment> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("attachments", e);
        }
    }
}
