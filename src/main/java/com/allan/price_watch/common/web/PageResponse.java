package com.allan.price_watch.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A deliberately narrow, stable pagination shape for API responses —
 * matches the {@code { content, page, size, totalElements }} examples in
 * the REST endpoint reference doc. Used instead of serializing Spring
 * Data's {@code Page} directly, which would leak internal fields
 * ({@code pageable}, {@code sort}, {@code numberOfElements}, ...) that
 * aren't meant to be part of this API's public contract.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}