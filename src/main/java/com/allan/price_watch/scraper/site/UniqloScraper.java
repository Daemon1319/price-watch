package com.allan.price_watch.scraper.site;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.InvalidVariantException;
import com.allan.price_watch.common.exception.ScrapeFailedException;
import com.allan.price_watch.product.dto.ProductVariantOption;
import com.allan.price_watch.product.dto.ProductVariantsResponse;
import com.allan.price_watch.product.dto.ProductVariantsResponse.ColorOption;
import com.allan.price_watch.product.dto.ProductVariantsResponse.SizeOption;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.scraper.ScrapeResult;
import com.allan.price_watch.scraper.Scraper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Scrapes Uniqlo product pages via their commerce API.
 *
 * <p>Color/size display labels live in {@link UniqloCatalog}
 * ({@code classpath:scraper/uniqlo/catalog.json}), not here.
 */
@Component
public class UniqloScraper implements Scraper {

  private static final Pattern LOCALE_AND_PRODUCT_ID_PATTERN =
      Pattern.compile("^/([a-z]{2})/([a-z]{2})/products/([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

  /** Allowed Uniqlo apex domains (exact host or subdomain only). */
  private static final Set<String> ALLOWED_HOST_ROOTS = Set.of(
      "uniqlo.com",
      "uniqlo.co.jp",
      "uniqlo.co.uk",
      "uniqlo.com.cn",
      "uniqlo.kr",
      "uniqlo.tw",
      "uniqlo.hk",
      "uniqlo.sg",
      "uniqlo.my",
      "uniqlo.th",
      "uniqlo.ph",
      "uniqlo.id",
      "uniqlo.vn",
      "uniqlo.in",
      "uniqlo.fr",
      "uniqlo.de",
      "uniqlo.es",
      "uniqlo.it",
      "uniqlo.nl",
      "uniqlo.be",
      "uniqlo.se",
      "uniqlo.dk",
      "uniqlo.au",
      "uniqlo.ca");

  private final HttpClient httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private final JsonMapper jsonMapper;
  private final UniqloCatalog catalog;

  public UniqloScraper(JsonMapper jsonMapper, UniqloCatalog catalog) {
    this.jsonMapper = jsonMapper;
    this.catalog = catalog;
  }

  @Override
  public Site getSite() {
    return Site.UNIQLO;
  }

  @Override
  public boolean supports(URI url) {
    return isAllowedUniqloHost(url.getHost());
  }

  /** True if host is a known Uniqlo domain or subdomain. */
  static boolean isAllowedUniqloHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    String normalized = host.toLowerCase(Locale.ROOT);
    for (String root : ALLOWED_HOST_ROOTS) {
      if (normalized.equals(root) || normalized.endsWith("." + root)) {
        return true;
      }
    }
    return false;
  }

  /** Calls the commerce API and returns name, price, stock, thumbnail, and variant labels. */
  @Override
  public ScrapeResult fetch(URI url) {
    ParsedProductUrl parsed = parseProductUrl(url);
    JsonNode item = fetchProductItem(apiUrl(url, parsed), parsed.productId());

    String colorCode = parsed.colorCode();
    String sizeCode = parsed.sizeCode();
    List<JsonNode> relevantL2s = filterL2s(item.path("l2s"), colorCode, sizeCode);

    if (colorCode != null && sizeCode != null && relevantL2s.isEmpty()) {
      throw new InvalidVariantException(colorCode, sizeCode);
    }

    String colorName = null;
    String sizeName = null;
    if (!relevantL2s.isEmpty()) {
      JsonNode l2 = relevantL2s.get(0);
      colorName = blankToNull(l2.path("color").path("name").asString(null));
      sizeName = blankToNull(l2.path("size").path("name").asString(null));
      if (colorCode == null) {
        colorCode = blankToNull(l2.path("color").path("code").asString(null));
      }
      if (sizeCode == null) {
        sizeCode = blankToNull(l2.path("size").path("code").asString(null));
      }
    }
    if (colorName == null && colorCode != null) {
      colorName = colorNameFromCatalog(item.path("colors"), colorCode);
    }
    if (sizeName == null && sizeCode != null) {
      sizeName = sizeNameFromCatalog(item.path("sizes"), sizeCode);
    }

    return new ScrapeResult(
        item.path("name").asString(null),
        extractPrice(item, relevantL2s, url),
        extractStockStatus(relevantL2s),
        buildThumbnailUrl(parsed.locale(), parsed.productId(), colorCode, item),
        catalog.normalizeVariantCode(colorCode),
        colorName,
        catalog.normalizeVariantCode(sizeCode),
        sizeName);
  }

  /**
   * Lists every color/size SKU for a product page (ignores colorCode/sizeCode on the URL).
   * Used by the UI to let the user pick a variant before tracking.
   */
  public ProductVariantsResponse listVariants(URI url) {
    ParsedProductUrl parsed = parseProductUrl(url);
    JsonNode item = fetchProductItem(apiUrl(url, parsed), parsed.productId());

    List<ColorOption> colors = new ArrayList<>();
    JsonNode colorsNode = item.path("colors");
    if (colorsNode.isArray()) {
      for (JsonNode c : colorsNode) {
        String code = catalog.normalizeVariantCode(c.path("code").asString(null));
        if (code == null) {
          continue;
        }
        colors.add(new ColorOption(
            code,
            blankToNull(c.path("name").asString(null)),
            blankToNull(c.path("displayCode").asString(null))));
      }
    }

    List<SizeOption> sizes = new ArrayList<>();
    JsonNode sizesNode = item.path("sizes");
    if (sizesNode.isArray()) {
      for (JsonNode s : sizesNode) {
        String code = catalog.normalizeVariantCode(s.path("code").asString(null));
        if (code == null) {
          continue;
        }
        sizes.add(new SizeOption(
            code,
            blankToNull(s.path("name").asString(null)),
            blankToNull(s.path("displayCode").asString(null))));
      }
    }

    List<ProductVariantOption> variants = new ArrayList<>();
    JsonNode l2s = item.path("l2s");
    if (l2s.isArray()) {
      for (JsonNode l2 : l2s) {
        String colorCode = catalog.normalizeVariantCode(l2.path("color").path("code").asString(null));
        String sizeCode = catalog.normalizeVariantCode(l2.path("size").path("code").asString(null));
        if (colorCode == null || sizeCode == null) {
          continue;
        }
        BigDecimal price = priceFromNode(l2.path("prices"));
        if (price == null) {
          price = priceFromNode(item.path("prices"));
        }
        variants.add(new ProductVariantOption(
            colorCode,
            blankToNull(l2.path("color").path("name").asString(null)),
            sizeCode,
            blankToNull(l2.path("size").path("name").asString(null)),
            price,
            stockStatusFromL2(l2),
            buildThumbnailUrl(parsed.locale(), parsed.productId(), colorCode, item)));
      }
    }

    String baseUrl = "https://" + url.getHost().toLowerCase(Locale.ROOT)
        + "/" + parsed.locale() + "/" + parsed.language() + "/products/" + parsed.productId();

    return new ProductVariantsResponse(
        item.path("name").asString(null),
        parsed.productId(),
        baseUrl,
        List.copyOf(colors),
        List.copyOf(sizes),
        List.copyOf(variants));
  }

  private ParsedProductUrl parseProductUrl(URI url) {
    Matcher matcher = LOCALE_AND_PRODUCT_ID_PATTERN.matcher(url.getPath() == null ? "" : url.getPath());
    if (!matcher.find()) {
      throw new ScrapeFailedException("Could not parse locale/product id from Uniqlo URL: " + url);
    }
    return new ParsedProductUrl(
        matcher.group(1).toLowerCase(Locale.ROOT),
        matcher.group(2).toLowerCase(Locale.ROOT),
        matcher.group(3).toUpperCase(Locale.ROOT),
        catalog.normalizeVariantCode(queryParam(url, "colorCode")),
        catalog.normalizeVariantCode(queryParam(url, "sizeCode")));
  }

  private URI apiUrl(URI pageUrl, ParsedProductUrl parsed) {
    return URI.create("https://" + pageUrl.getHost() + "/" + parsed.locale() + "/api/commerce/v3/"
        + parsed.language() + "/products/" + parsed.productId() + "?isV2Review=true&withStocks=true");
  }

  /** GETs the product JSON item from Uniqlo's commerce API. */
  private JsonNode fetchProductItem(URI apiUrl, String productId) {
    String referer = "https://" + apiUrl.getHost() + "/";
    HttpRequest request = HttpRequest.newBuilder(apiUrl)
        .version(HttpClient.Version.HTTP_1_1)
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        .header("Referer", referer)
        .header("Origin", "https://" + apiUrl.getHost())
        .timeout(Duration.ofSeconds(20))
        .GET()
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ScrapeFailedException("Could not reach Uniqlo API for " + productId + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScrapeFailedException("Interrupted while calling Uniqlo API for " + productId);
    }

    if (response.statusCode() != 200) {
      throw new ScrapeFailedException(
          "Uniqlo API returned status " + response.statusCode() + " for " + productId);
    }

    JsonNode root;
    try {
      root = jsonMapper.readTree(response.body());
    } catch (JacksonException e) {
      throw new ScrapeFailedException("Could not parse Uniqlo API response for " + productId);
    }

    if (!"ok".equals(root.path("status").asString(null))) {
      throw new ScrapeFailedException("Uniqlo API returned non-ok status for " + productId);
    }

    JsonNode items = root.path("result").path("items");
    if (!items.isArray() || items.size() == 0) {
      throw new ScrapeFailedException("Uniqlo API returned no items for product " + productId);
    }

    return items.get(0);
  }

  /** Restricts L2 variants to the color/size from the product URL when set. */
  private List<JsonNode> filterL2s(JsonNode l2s, String colorCode, String sizeCode) {
    List<JsonNode> all = new ArrayList<>();
    if (!l2s.isArray()) {
      return all;
    }
    for (JsonNode l2 : l2s) {
      all.add(l2);
    }
    if (colorCode == null && sizeCode == null) {
      return all;
    }

    List<JsonNode> filtered = all.stream()
        .filter(l2 -> colorCode == null
            || colorCode.equalsIgnoreCase(l2.path("color").path("code").asString("")))
        .filter(l2 -> sizeCode == null
            || sizeCode.equalsIgnoreCase(l2.path("size").path("code").asString("")))
        .toList();

    // Exact color+size must match; partial filters fall back only when empty
    // so a bad single filter does not silently use every SKU.
    if (colorCode != null && sizeCode != null) {
      return filtered;
    }
    return filtered.isEmpty() ? List.of() : filtered;
  }

  /** Prefers variant price when available, otherwise top-level product price. */
  private BigDecimal extractPrice(JsonNode item, List<JsonNode> relevantL2s, URI url) {
    for (JsonNode l2 : relevantL2s) {
      BigDecimal fromL2 = priceFromNode(l2.path("prices"));
      if (fromL2 != null) {
        return fromL2;
      }
    }
    BigDecimal topLevel = priceFromNode(item.path("prices"));
    if (topLevel != null) {
      return topLevel;
    }
    throw new ScrapeFailedException("Could not find a price in Uniqlo API response for " + url);
  }

  private BigDecimal priceFromNode(JsonNode prices) {
    if (prices == null || prices.isMissingNode() || prices.isNull()) {
      return null;
    }
    JsonNode promo = prices.path("promo");
    JsonNode priceNode = (!promo.isMissingNode() && !promo.isNull()) ? promo : prices.path("base");
    String rawValue = priceNode.path("value").asString(null);
    return rawValue != null ? new BigDecimal(rawValue) : null;
  }

  /** Aggregates stock across relevant variants (any in stock → IN_STOCK). */
  private StockStatus extractStockStatus(List<JsonNode> relevantL2s) {
    if (relevantL2s.isEmpty()) {
      return StockStatus.UNKNOWN;
    }

    boolean sawAny = false;
    boolean anyAvailable = false;

    for (JsonNode l2 : relevantL2s) {
      StockStatus status = stockStatusFromL2(l2);
      if (status == StockStatus.UNKNOWN) {
        continue;
      }
      sawAny = true;
      if (status == StockStatus.IN_STOCK) {
        anyAvailable = true;
        break;
      }
    }

    if (!sawAny) {
      return StockStatus.UNKNOWN;
    }
    return anyAvailable ? StockStatus.IN_STOCK : StockStatus.OUT_OF_STOCK;
  }

  private static StockStatus stockStatusFromL2(JsonNode l2) {
    JsonNode stock = l2.path("stock");
    if (stock.isMissingNode() || stock.isNull()) {
      return StockStatus.UNKNOWN;
    }
    String code = stock.path("statusCode").asString("");
    if ("IN_STOCK".equals(code) || "LOW_STOCK".equals(code)) {
      return StockStatus.IN_STOCK;
    }
    if ("STOCK_OUT".equals(code) || "OUT_OF_STOCK".equals(code) || "NOT_FOR_SALE".equals(code)) {
      return StockStatus.OUT_OF_STOCK;
    }
    return StockStatus.UNKNOWN;
  }

  /** Builds a Uniqlo CDN thumbnail URL for the product/color. */
  private String buildThumbnailUrl(String locale, String productId, String colorCode, JsonNode item) {
    String goodsId = goodsIdFromProductId(productId);
    if (goodsId == null) {
      return null;
    }

    String colorDigits = colorDigitsFromCode(colorCode);
    if (colorDigits == null) {
      colorDigits = colorDigitsFromCode(item.path("representative").path("color").path("code").asString(null));
    }
    if (colorDigits == null) {
      JsonNode colors = item.path("colors");
      if (colors.isArray() && colors.size() > 0) {
        colorDigits = colorDigitsFromCode(colors.get(0).path("code").asString(null));
      }
    }
    if (colorDigits == null) {
      colorDigits = "00";
    }

    return "https://image.uniqlo.com/UQ/ST3/" + locale + "/imagesgoods/" + goodsId
        + "/item/phgoods_" + colorDigits + "_" + goodsId + "_3x4.jpg?width=369";
  }

  /** Extracts numeric goods id from Uniqlo product ids like E471809-000. */
  private static String goodsIdFromProductId(String productId) {
    if (productId == null || productId.isBlank()) {
      return null;
    }
    Matcher m = Pattern.compile("E?(\\d+)", Pattern.CASE_INSENSITIVE).matcher(productId);
    return m.find() ? m.group(1) : null;
  }

  /** Extracts color digits from codes like COL03 (zero-padded when numeric). */
  String colorDigitsFromCode(String colorCode) {
    return catalog.colorDigitsOf(colorCode);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Prefers product API color name; falls back to classpath catalog labels. */
  private String colorNameFromCatalog(JsonNode colors, String colorCode) {
    if (colors.isArray()) {
      for (JsonNode c : colors) {
        if (colorCode.equalsIgnoreCase(c.path("code").asString(""))) {
          String fromApi = blankToNull(c.path("name").asString(null));
          if (fromApi != null) {
            return fromApi;
          }
        }
      }
    }
    return catalog.colorDisplayName(colorCode).orElse(null);
  }

  /** Prefers product API size name; falls back to classpath catalog labels. */
  private String sizeNameFromCatalog(JsonNode sizes, String sizeCode) {
    if (sizes.isArray()) {
      for (JsonNode s : sizes) {
        if (sizeCode.equalsIgnoreCase(s.path("code").asString(""))) {
          String fromApi = blankToNull(s.path("name").asString(null));
          if (fromApi != null) {
            return fromApi;
          }
        }
      }
    }
    return catalog.sizeDisplayName(sizeCode).orElse(null);
  }

  static String queryParam(URI url, String name) {
    String query = url.getRawQuery();
    if (query == null || query.isBlank()) {
      return null;
    }
    for (String part : query.split("&")) {
      int eq = part.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
      if (name.equalsIgnoreCase(key)) {
        String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }

  /** Parsed locale/language/productId and optional variant query codes. */
  private record ParsedProductUrl(
      String locale,
      String language,
      String productId,
      String colorCode,
      String sizeCode) {
  }
}
