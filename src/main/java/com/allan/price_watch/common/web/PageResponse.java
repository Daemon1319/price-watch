package com.allan.price_watch.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/** Stable paginated API response shape (content, page, size, totalElements). */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}