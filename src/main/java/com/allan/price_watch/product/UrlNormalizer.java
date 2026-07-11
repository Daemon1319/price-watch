package com.allan.price_watch.product;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Generic URL cleanup so the same product reached through different links
 * (a marketing email with a {@code utm_source}, a shared link with a
 * trailing slash, a mixed-case host) still maps to the same
 * {@code normalized_url} and dedupes correctly against {@code products}.
 * Deliberately has no site-specific knowledge — that's what {@code Scraper}
 * implementations are for.
 */
@Component
public class UrlNormalizer {

  // Plan §5a: strip known tracking params; keep product-specific ones (size, color).
  private static final Pattern TRACKING_PARAM = Pattern.compile(
      "^(utm_[a-z0-9_]+|gclid|fbclid|igshid|ref|mc_[a-z]+|spm|clickid|msclkid|twclid|yclid|pk_campaign|pk_kwd)=.*",
      Pattern.CASE_INSENSITIVE);

  public String normalize(String rawUrl) {
    URI uri = URI.create(rawUrl.trim());

    String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

    if (host.isEmpty()) {
      throw new IllegalArgumentException("URL has no host: " + rawUrl);
    }

    String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    String query = normalizeQuery(uri.getRawQuery());

    StringBuilder normalized = new StringBuilder(scheme).append("://").append(host).append(path);
    if (!query.isEmpty()) {
      normalized.append('?').append(query);
    }

    return normalized.toString();
  }

  /**
   * Drops known tracking params, then sorts whatever's left so param
   * order alone never produces two different dedup keys for what's
   * actually the same URL.
   */
  private String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "";
    }

    return Arrays.stream(rawQuery.split("&"))
        .filter(param -> !TRACKING_PARAM.matcher(param).matches())
        .sorted()
        .collect(Collectors.joining("&"));
  }
}