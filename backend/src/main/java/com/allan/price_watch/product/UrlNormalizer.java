package com.allan.price_watch.product;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.allan.price_watch.scraper.site.UniqloCatalog;

/** Canonicalizes product URLs so the same page always maps to one dedup key. */
@Component
public class UrlNormalizer {

  // Tracking/analytics params only — keep product params like colorCode/sizeCode.
  private static final Pattern TRACKING_PARAM = Pattern.compile(
      "^(utm_[a-z0-9_]+|gclid|fbclid|igshid|ref|mc_[a-z]+|spm|clickid|msclkid|twclid|yclid|pk_campaign|pk_kwd)=.*",
      Pattern.CASE_INSENSITIVE);

  private final UniqloCatalog uniqloCatalog;

  public UrlNormalizer(UniqloCatalog uniqloCatalog) {
    this.uniqloCatalog = uniqloCatalog;
  }

  /** Lowercases host, strips tracking query params, and drops trailing slashes. */
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
   * Merges colorCode/sizeCode into the URL (body values win over existing query params),
   * then normalizes. Null/blank codes leave that param unchanged.
   */
  public String normalizeWithVariant(String rawUrl, String colorCode, String sizeCode) {
    String withParams = applyVariantParams(rawUrl, colorCode, sizeCode);
    return normalize(withParams);
  }

  /** Sets or replaces colorCode/sizeCode query params when the values are non-blank. */
  public String applyVariantParams(String rawUrl, String colorCode, String sizeCode) {
    URI uri = URI.create(rawUrl.trim());
    Map<String, String> params = parseQuery(uri.getRawQuery());

    if (colorCode != null && !colorCode.isBlank()) {
      params.put("colorCode", uniqloCatalog.normalizeColorCode(colorCode));
    }
    if (sizeCode != null && !sizeCode.isBlank()) {
      params.put("sizeCode", uniqloCatalog.normalizeSizeCode(sizeCode));
    }

    String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
    String host = uri.getHost() == null ? "" : uri.getHost();
    String path = uri.getRawPath() == null ? "" : uri.getRawPath();

    StringBuilder result = new StringBuilder(scheme).append("://").append(host).append(path);
    String query = encodeQuery(params);
    if (!query.isEmpty()) {
      result.append('?').append(query);
    }
    return result.toString();
  }

  /** Reads colorCode from a (possibly already normalized) URL. */
  public String colorCode(String url) {
    return queryParam(url, "colorCode");
  }

  /** Reads sizeCode from a (possibly already normalized) URL. */
  public String sizeCode(String url) {
    return queryParam(url, "sizeCode");
  }

  /** Drops tracking params, uppercases variant codes, and sorts remaining ones for stable keys. */
  private String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "";
    }

    Map<String, String> params = parseQuery(rawQuery);
    params.entrySet().removeIf(e -> TRACKING_PARAM.matcher(e.getKey() + "=" + e.getValue()).matches());

    // Canonicalize Uniqlo variant codes so COL09, col09, and 09 share one product row.
    if (params.containsKey("colorCode")) {
      params.put("colorCode", uniqloCatalog.normalizeColorCode(params.get("colorCode")));
    }
    if (params.containsKey("sizeCode")) {
      params.put("sizeCode", uniqloCatalog.normalizeSizeCode(params.get("sizeCode")));
    }

    return params.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static Map<String, String> parseQuery(String rawQuery) {
    Map<String, String> params = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) {
      return params;
    }
    for (String part : rawQuery.split("&")) {
      if (part.isBlank()) {
        continue;
      }
      int eq = part.indexOf('=');
      String key = eq < 0 ? part : part.substring(0, eq);
      String value = eq < 0 ? "" : part.substring(eq + 1);
      key = URLDecoder.decode(key, StandardCharsets.UTF_8);
      value = URLDecoder.decode(value, StandardCharsets.UTF_8);
      if (!key.isBlank()) {
        params.put(key, value);
      }
    }
    return params;
  }

  private static String encodeQuery(Map<String, String> params) {
    return params.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String queryParam(String url, String name) {
    URI uri = URI.create(url);
    String query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return null;
    }
    return Arrays.stream(query.split("&"))
        .map(part -> {
          int eq = part.indexOf('=');
          if (eq <= 0) {
            return null;
          }
          String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
          if (!name.equalsIgnoreCase(key)) {
            return null;
          }
          String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
          if (value.isBlank()) {
            return null;
          }
          if ("colorCode".equalsIgnoreCase(name)) {
            return uniqloCatalog.normalizeColorCode(value);
          }
          if ("sizeCode".equalsIgnoreCase(name)) {
            return uniqloCatalog.normalizeSizeCode(value);
          }
          return value.toUpperCase(Locale.ROOT);
        })
        .filter(v -> v != null)
        .findFirst()
        .orElse(null);
  }
}
