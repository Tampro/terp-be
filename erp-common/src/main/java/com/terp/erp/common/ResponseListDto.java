package com.terp.erp.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Page shape the FE reads as {@code IApiPagination<T>}. */
public record ResponseListDto<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                                 boolean last) {
    public static <T> ResponseListDto<T> of(Page<T> p) {
        return new ResponseListDto<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                p.getTotalPages(), p.isLast());
    }
}
