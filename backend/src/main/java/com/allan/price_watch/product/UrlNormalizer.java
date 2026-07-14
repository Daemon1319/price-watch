package com.allan.price_watch.product;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.allan.price_watch.scraper.site.UniqloCatalog;

/**
 * Canonicalizes product URLs so the same page always maps to one dedup key.
 *
 * <p>Supports legacy Uniqlo query params ({@code colorCode}/{@code sizeCode}) and the
 * newer storefront shape:
 * {@code /products/E…-000/00?colorDisplayCode=18&sizeDisplayCode=005}.
 */
@Component
public class UrlNormalizer {

  // Tracking/analytics params only — keep product params like colorCode/sizeCode.
  private static final Pattern TRACKING_PARAM = Pattern.compile(
      "^(utm_[a-z0-9_]+|gclid|fbclid|igshid|ref|mc_[a-z]+|spm|clickid|msclkid|twclid|yclid|pk_campaign|pk_kwd)=.*",
      Pattern.CASE_INSENSITIVE);

  /**
   * Uniqlo product path with optional price-group suffix:
   * {@code /ph/en/products/E475367-000} or {@code /ph/en/products/E475367-000/00}.
   */
  private static final Pattern UNIQLO_PRODUCT_PATH = Pattern.compile(
      "^(/(?i)[a-z]{2}/[a-z]{2}/products/[A-Z0-9-]+)(?:/\\d{1,4})?/?$",
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
    path = stripUniqloPriceGroupSuffix(path);

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

    // Drop new-style display params; we always persist canonical colorCode/sizeCode.
    params.remove("colorDisplayCode");
    params.remove("sizeDisplayCode");
    params.remove("colordisplaycode");
    params.remove("sizedisplaycode");

    if (colorCode != null && !colorCode.isBlank()) {
      params.put("colorCode", uniqloCatalog.normalizeColorCode(colorCode));
    }
    if (sizeCode != null && !sizeCode.isBlank()) {
      params.put("sizeCode", uniqloCatalog.normalizeSizeCode(sizeCode));
    }

    String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
    String host = uri.getHost() == null ? "" : uri.getHost();
    String path = uri.getRawPath() == null ? "" : uri.getRawPath();
    path = stripUniqloPriceGroupSuffix(path);

    StringBuilder result = new StringBuilder(scheme).append("://").append(host).append(path);
    String query = encodeQuery(params);
    if (!query.isEmpty()) {
      result.append('?').append(query);
    }
    return result.toString();
  }

  /** Reads colorCode from a URL (also maps {@code colorDisplayCode}). */
  public String colorCode(String url) {
    String fromCode = queryParam(url, "colorCode");
    if (fromCode != null) {
      return uniqloCatalog.normalizeColorCode(fromCode);
    }
    String display = queryParam(url, "colorDisplayCode");
    return display != null ? uniqloCatalog.normalizeColorCode(display) : null;
  }

  /** Reads sizeCode from a URL (also maps {@code sizeDisplayCode}). */
  public String sizeCode(String url) {
    String fromCode = queryParam(url, "sizeCode");
    if (fromCode != null) {
      return uniqloCatalog.normalizeSizeCode(fromCode);
    }
    String display = queryParam(url, "sizeDisplayCode");
    return display != null ? uniqloCatalog.normalizeSizeCode(display) : null;
  }

  /**
   * Drops {@code /00}-style price-group suffixes so
   * {@code .../products/E…-000/00} and {@code .../products/E…-000} share one key.
   */
  static String stripUniqloPriceGroupSuffix(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }
    Matcher m = UNIQLO_PRODUCT_PATH.matcher(path);
    if (m.matches()) {
      return m.group(1);
    }
    return path;
  }

  /** Drops tracking params, maps display codes → colorCode/sizeCode, sorts for stable keys. */
  private String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "";
    }

    Map<String, String> params = parseQuery(rawQuery);
    params.entrySet().removeIf(e -> TRACKING_PARAM.matcher(e.getKey() + "=" + e.getValue()).matches());

    // New Uniqlo storefront: colorDisplayCode / sizeDisplayCode → canonical codes.
    String displayColor = firstParam(params, "colorDisplayCode");
    String displaySize = firstParam(params, "sizeDisplayCode");
    removeParamIgnoreCase(params, "colorDisplayCode");
    removeParamIgnoreCase(params, "sizeDisplayCode");

    if (!params.containsKey("colorCode") && displayColor != null) {
      params.put("colorCode", displayColor);
    }
    if (!params.containsKey("sizeCode") && displaySize != null) {
      params.put("sizeCode", displaySize);
    }

    // Canonicalize so COL09, col09, 09, and colorDisplayCode=9 share one product row.
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

  private static String firstParam(Map<String, String> params, String name) {
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isBlank()) {
        return e.getValue();
      }
    }
    return null;
  }

  private static void removeParamIgnoreCase(Map<String, String> params, String name) {
    params.keySet().removeIf(k -> k.equalsIgnoreCase(name));
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
          return value;
        })
        .filter(v -> v != null)
        .findFirst()
        .orElse(null);
  }
}
