package com.allan.price_watch.scraper.site;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Uniqlo reference data (color/size labels) loaded from classpath — not business rules.
 *
 * <p>Data file: {@code classpath:scraper/uniqlo/catalog.json}. Edit that file to add codes;
 * this class only loads and looks up. Commerce API names always take precedence at scrape time.
 *
 * <p>Uses its own {@link JsonMapper} for resource loading so startup does not depend on
 * Spring Jackson bean wiring order.
 */
@Component
public class UniqloCatalog {

  public static final String CLASSPATH_LOCATION = "scraper/uniqlo/catalog.json";

  private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP =
      new TypeReference<>() {
      };

  private final Map<String, String> colorsByDigits;
  private final Map<String, String> sizesByCode;

  /** Spring entry point — loads default catalog from the classpath. */
  public UniqloCatalog() {
    this(load(defaultMapper(), CLASSPATH_LOCATION));
  }

  private UniqloCatalog(CatalogData data) {
    this.colorsByDigits = Collections.unmodifiableMap(new LinkedHashMap<>(data.colors()));
    this.sizesByCode = Collections.unmodifiableMap(new LinkedHashMap<>(data.sizes()));
  }

  /** Loads catalog JSON from an arbitrary classpath location (tests / alternate packs). */
  public static UniqloCatalog fromClasspath(JsonMapper jsonMapper, String classpathLocation) {
    return new UniqloCatalog(load(jsonMapper, classpathLocation));
  }

  private static JsonMapper defaultMapper() {
    return JsonMapper.builder().build();
  }

  private static CatalogData load(JsonMapper jsonMapper, String classpathLocation) {
    ClassPathResource resource = new ClassPathResource(classpathLocation);
    if (!resource.exists()) {
      throw new IllegalStateException("Missing Uniqlo catalog: classpath:" + classpathLocation);
    }
    try (InputStream in = resource.getInputStream()) {
      JsonNode root = jsonMapper.readTree(in);
      return new CatalogData(
          readStringMap(jsonMapper, root.path("colors")),
          readStringMap(jsonMapper, root.path("sizes")));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load Uniqlo catalog from " + classpathLocation, e);
    }
  }

  private static Map<String, String> readStringMap(JsonMapper jsonMapper, JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
      return Map.of();
    }
    Map<String, String> raw = jsonMapper.convertValue(node, STRING_MAP);
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, String> map = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
        map.put(key.trim(), value.trim());
      }
    });
    return map;
  }

  /**
   * Canonical API color code: {@code COL} + two-digit display code.
   * Accepts {@code COL09}, {@code col9}, {@code 09}, {@code 9}.
   */
  public String normalizeColorCode(String raw) {
    String digits = colorDigitsOf(raw);
    if (digits == null) {
      if (raw == null || raw.isBlank()) {
        return null;
      }
      return raw.trim().toUpperCase(Locale.ROOT);
    }
    return "COL" + digits;
  }

  /** Two-digit display code ({@code 09}) or null if not color-like. */
  public String colorDigitsOf(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    String numeric;
    if (upper.startsWith("COL")) {
      numeric = upper.substring(3);
    } else if (upper.matches("\\d{1,2}")) {
      numeric = upper;
    } else {
      return null;
    }
    if (!numeric.matches("\\d{1,2}")) {
      return null;
    }
    return String.format(Locale.ROOT, "%02d", Integer.parseInt(numeric));
  }

  /**
   * Uppercases size codes; inch sizes stay {@code INSxxx}.
   * Bare display digits (e.g. {@code 005} from {@code sizeDisplayCode}) map to
   * {@code SMA###} when &lt; 20 and {@code INS###} when ≥ 20 (waist/inseam style).
   */
  public String normalizeSizeCode(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    if (upper.matches("(SMA|INS|SMW|SMB)\\d+")) {
      return upper;
    }
    if (upper.matches("\\d{1,3}")) {
      int n = Integer.parseInt(upper);
      String pad = String.format(Locale.ROOT, "%03d", n);
      // Apparel size chips are small numbers; larger values are inch sizes.
      return n >= 20 ? "INS" + pad : "SMA" + pad;
    }
    return upper;
  }

  /**
   * Canonicalizes a Uniqlo variant code: colors → {@code COLxx}, sizes → uppercased.
   * Bare digit strings are treated as color display codes.
   */
  public String normalizeVariantCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    String trimmed = code.trim();
    if (colorDigitsOf(trimmed) != null) {
      return normalizeColorCode(trimmed);
    }
    return normalizeSizeCode(trimmed);
  }

  /** Fallback English color label from catalog data, if known. */
  public Optional<String> colorDisplayName(String raw) {
    String digits = colorDigitsOf(raw);
    if (digits == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(colorsByDigits.get(digits));
  }

  /** Fallback size label from catalog data, if known (not inch sizes). */
  public Optional<String> sizeDisplayName(String raw) {
    String code = normalizeSizeCode(raw);
    if (code == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(sizesByCode.get(code));
  }

  public Map<String, String> colors() {
    return colorsByDigits;
  }

  public Map<String, String> sizes() {
    return sizesByCode;
  }

  private record CatalogData(Map<String, String> colors, Map<String, String> sizes) {
  }
}
