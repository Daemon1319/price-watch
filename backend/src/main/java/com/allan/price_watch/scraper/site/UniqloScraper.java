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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.InvalidVariantException;
import com.allan.price_watch.common.exception.ScrapeFailedException;
import com.allan.price_watch.product.UrlNormalizer;
import com.allan.price_watch.product.entity.ScrapeFailureReason;
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
 * Scrapes Uniqlo product pages via their commerce API (v5).
 *
 * <p>Product metadata comes from {@code /api/commerce/v5/{lang}/products/{id}}. Per-SKU stock and
 * price maps come from
 * {@code /api/commerce/v5/{lang}/products/{id}/price-groups/{priceGroup}/l2s}. Both require the
 * {@code x-fr-clientid} header used by Uniqlo's web SPA.
 *
 * <p>Color/size display labels live in {@link UniqloCatalog}
 * ({@code classpath:scraper/uniqlo/catalog.json}), not here.
 */
@Component
public class UniqloScraper implements Scraper {

  private static final Pattern LOCALE_AND_PRODUCT_ID_PATTERN =
      Pattern.compile("^/([a-z]{2})/([a-z]{2})/products/([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

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
    JsonNode product = fetchProduct(url, parsed);
    String priceGroup = resolvePriceGroup(url, product);
    L2Catalog l2Catalog = fetchL2Catalog(url, parsed, priceGroup);

    String colorCode = parsed.colorCode();
    String sizeCode = parsed.sizeCode();
    // Prefer L2s for the resolved price group (e.g. /01 → COL02). product.l2s is default-group only.
    List<JsonNode> relevantL2s = filterL2s(l2Catalog.l2s(), colorCode, sizeCode);
    if (relevantL2s.isEmpty()) {
      relevantL2s = filterL2s(product.path("l2s"), colorCode, sizeCode);
    }

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
      colorName = colorNameFromCatalog(product.path("colors"), colorCode);
    }
    if (sizeName == null && sizeCode != null) {
      sizeName = sizeNameFromCatalog(product.path("sizes"), sizeCode);
    }

    return new ScrapeResult(
        product.path("name").asString(null),
        extractPrice(product, relevantL2s, l2Catalog, url),
        extractStockStatus(relevantL2s, l2Catalog),
        buildThumbnailUrl(parsed.locale(), parsed.productId(), colorCode, product),
        catalog.normalizeVariantCode(colorCode),
        colorName,
        catalog.normalizeVariantCode(sizeCode),
        sizeName);
  }

  /**
   * Lists every color/size SKU for a product page (ignores colorCode/sizeCode on the URL).
   * Uses the URL path price group when present ({@code /01} vs default {@code /00}).
   * Used by the UI to let the user pick a variant before tracking.
   */
  public ProductVariantsResponse listVariants(URI url) {
    ParsedProductUrl parsed = parseProductUrl(url);
    JsonNode product = fetchProduct(url, parsed);
    String priceGroup = resolvePriceGroup(url, product);
    L2Catalog l2Catalog = fetchL2Catalog(url, parsed, priceGroup);

    // Prefer L2s for the resolved price group — product.l2s/colors are default-group only.
    JsonNode l2s = l2Catalog.l2s();
    if (!l2s.isArray() || l2s.size() == 0) {
      l2s = product.path("l2s");
    }

    List<ProductVariantOption> variants = new ArrayList<>();
    if (l2s.isArray()) {
      for (JsonNode l2 : l2s) {
        String colorCode = catalog.normalizeVariantCode(l2.path("color").path("code").asString(null));
        String sizeCode = catalog.normalizeVariantCode(l2.path("size").path("code").asString(null));
        if (colorCode == null || sizeCode == null) {
          continue;
        }
        String l2Id = blankToNull(l2.path("l2Id").asString(null));
        BigDecimal price = priceForL2(l2, l2Id, l2Catalog);
        if (price == null) {
          price = priceFromNode(product.path("prices"));
        }
        String colorName = blankToNull(l2.path("color").path("name").asString(null));
        if (colorName == null) {
          colorName = colorNameFromCatalog(product.path("colors"), colorCode);
        }
        String sizeName = blankToNull(l2.path("size").path("name").asString(null));
        if (sizeName == null) {
          sizeName = sizeNameFromCatalog(product.path("sizes"), sizeCode);
        }
        variants.add(new ProductVariantOption(
            colorCode,
            colorName,
            sizeCode,
            sizeName,
            price,
            stockStatusForL2(l2, l2Id, l2Catalog),
            buildThumbnailUrl(parsed.locale(), parsed.productId(), colorCode, product)));
      }
    }

    // Colors/sizes for the dropdown must match this price group (not product.colors alone).
    List<ColorOption> colors = colorsForPriceGroup(product, variants, l2s);
    List<SizeOption> sizes = sizesForPriceGroup(product, variants, l2s);

    String baseUrl = "https://" + url.getHost().toLowerCase(Locale.ROOT)
        + "/" + parsed.locale() + "/" + parsed.language() + "/products/" + parsed.productId();
    if (parsed.priceGroup() != null) {
      baseUrl = baseUrl + "/" + parsed.priceGroup();
    }

    return new ProductVariantsResponse(
        product.path("name").asString(null),
        parsed.productId(),
        baseUrl,
        List.copyOf(colors),
        List.copyOf(sizes),
        List.copyOf(variants));
  }

  private ParsedProductUrl parseProductUrl(URI url) {
    String path = url.getPath() == null ? "" : url.getPath();
    Matcher matcher = LOCALE_AND_PRODUCT_ID_PATTERN.matcher(path);
    if (!matcher.find()) {
      throw new ScrapeFailedException("Could not parse locale/product id from Uniqlo URL: " + url);
    }
    String colorCode = firstNonBlank(
        queryParam(url, "colorCode"),
        queryParam(url, "colorDisplayCode"));
    String sizeCode = firstNonBlank(
        queryParam(url, "sizeCode"),
        queryParam(url, "sizeDisplayCode"));
    return new ParsedProductUrl(
        matcher.group(1).toLowerCase(Locale.ROOT),
        matcher.group(2).toLowerCase(Locale.ROOT),
        matcher.group(3).toUpperCase(Locale.ROOT),
        UrlNormalizer.uniqloPriceGroupFromPath(path),
        catalog.normalizeColorCode(colorCode),
        catalog.normalizeSizeCode(sizeCode));
  }

  /**
   * Path price group wins ({@code /01}); otherwise the product document default ({@code 00}).
   */
  private String resolvePriceGroup(URI url, JsonNode product) {
    String fromPath = UrlNormalizer.uniqloPriceGroupFromPath(url.getPath());
    if (fromPath != null) {
      return fromPath;
    }
    return priceGroupOf(product);
  }

  /** Distinct colors sold in this price group (L2s first; product.colors as name source). */
  private List<ColorOption> colorsForPriceGroup(
      JsonNode product, List<ProductVariantOption> variants, JsonNode l2s) {
    LinkedHashMap<String, ColorOption> byCode = new LinkedHashMap<>();
    if (l2s != null && l2s.isArray()) {
      for (JsonNode l2 : l2s) {
        String code = catalog.normalizeVariantCode(l2.path("color").path("code").asString(null));
        if (code == null || byCode.containsKey(code)) {
          continue;
        }
        String name = blankToNull(l2.path("color").path("name").asString(null));
        if (name == null) {
          name = colorNameFromCatalog(product.path("colors"), code);
        }
        String display = blankToNull(l2.path("color").path("displayCode").asString(null));
        if (display == null) {
          display = catalog.colorDigitsOf(code);
        }
        byCode.put(code, new ColorOption(code, name, display));
      }
    }
    if (byCode.isEmpty()) {
      for (ProductVariantOption v : variants) {
        if (v.colorCode() == null || byCode.containsKey(v.colorCode())) {
          continue;
        }
        byCode.put(
            v.colorCode(),
            new ColorOption(
                v.colorCode(),
                v.colorName(),
                catalog.colorDigitsOf(v.colorCode())));
      }
    }
    if (byCode.isEmpty()) {
      JsonNode colorsNode = product.path("colors");
      if (colorsNode.isArray()) {
        for (JsonNode c : colorsNode) {
          String code = catalog.normalizeVariantCode(c.path("code").asString(null));
          if (code == null) {
            continue;
          }
          byCode.put(
              code,
              new ColorOption(
                  code,
                  blankToNull(c.path("name").asString(null)),
                  blankToNull(c.path("displayCode").asString(null))));
        }
      }
    }
    return new ArrayList<>(byCode.values());
  }

  /** Distinct sizes sold in this price group. */
  private List<SizeOption> sizesForPriceGroup(
      JsonNode product, List<ProductVariantOption> variants, JsonNode l2s) {
    LinkedHashMap<String, SizeOption> byCode = new LinkedHashMap<>();
    if (l2s != null && l2s.isArray()) {
      for (JsonNode l2 : l2s) {
        String code = catalog.normalizeVariantCode(l2.path("size").path("code").asString(null));
        if (code == null || byCode.containsKey(code)) {
          continue;
        }
        String name = blankToNull(l2.path("size").path("name").asString(null));
        if (name == null) {
          name = sizeNameFromCatalog(product.path("sizes"), code);
        }
        String display = blankToNull(l2.path("size").path("displayCode").asString(null));
        byCode.put(code, new SizeOption(code, name, display));
      }
    }
    if (byCode.isEmpty()) {
      for (ProductVariantOption v : variants) {
        if (v.sizeCode() == null || byCode.containsKey(v.sizeCode())) {
          continue;
        }
        byCode.put(
            v.sizeCode(),
            new SizeOption(v.sizeCode(), v.sizeName(), null));
      }
    }
    if (byCode.isEmpty()) {
      JsonNode sizesNode = product.path("sizes");
      if (sizesNode.isArray()) {
        for (JsonNode s : sizesNode) {
          String code = catalog.normalizeVariantCode(s.path("code").asString(null));
          if (code == null) {
            continue;
          }
          byCode.put(
              code,
              new SizeOption(
                  code,
                  blankToNull(s.path("name").asString(null)),
                  blankToNull(s.path("displayCode").asString(null))));
        }
      }
    }
    return new ArrayList<>(byCode.values());
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
  }

  private URI productApiUrl(URI pageUrl, ParsedProductUrl parsed) {
    return URI.create("https://" + pageUrl.getHost() + "/" + parsed.locale() + "/api/commerce/v5/"
        + parsed.language() + "/products/" + parsed.productId()
        + "?isV2Review=true&withStocks=true");
  }

  private URI l2sApiUrl(URI pageUrl, ParsedProductUrl parsed, String priceGroup) {
    return URI.create("https://" + pageUrl.getHost() + "/" + parsed.locale() + "/api/commerce/v5/"
        + parsed.language() + "/products/" + parsed.productId()
        + "/price-groups/" + priceGroup
        + "/l2s?withPrices=true&withStocks=true&includePreviousPrice=false"
        + "&withMemberPricing=false&httpFailure=true");
  }

  /** Client id used by Uniqlo's web SPA for the given locale (e.g. {@code uq.ph.web-spa}). */
  static String clientIdForLocale(String locale) {
    String loc = (locale == null || locale.isBlank()) ? "ph" : locale.toLowerCase(Locale.ROOT);
    return "uq." + loc + ".web-spa";
  }

  private static String priceGroupOf(JsonNode product) {
    String priceGroup = blankToNull(product.path("priceGroup").asString(null));
    return priceGroup != null ? priceGroup : "00";
  }

  /** Product document from commerce v5 ({@code result} object — no {@code items} wrapper). */
  private JsonNode fetchProduct(URI pageUrl, ParsedProductUrl parsed) {
    JsonNode result = fetchOkResult(productApiUrl(pageUrl, parsed), parsed);
    if (result.isMissingNode() || result.isNull() || result.isEmpty()) {
      throw new ScrapeFailedException(
          ScrapeFailureReason.PRODUCT_UNAVAILABLE,
          "Uniqlo API returned empty product for " + parsed.productId());
    }
    return result;
  }

  /**
   * Price-group L2 catalog: parallel maps of {@code stocks} and {@code prices} keyed by
   * {@code l2Id}, plus an optional {@code l2s} array.
   */
  private L2Catalog fetchL2Catalog(URI pageUrl, ParsedProductUrl parsed, String priceGroup) {
    JsonNode result = fetchOkResult(l2sApiUrl(pageUrl, parsed, priceGroup), parsed);
    return new L2Catalog(
        result.path("l2s"),
        result.path("stocks"),
        result.path("prices"));
  }

  private JsonNode fetchOkResult(URI apiUrl, ParsedProductUrl parsed) {
    String productId = parsed.productId();
    String host = apiUrl.getHost();
    String origin = "https://" + host;
    HttpRequest request = HttpRequest.newBuilder(apiUrl)
        .version(HttpClient.Version.HTTP_1_1)
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("User-Agent", USER_AGENT)
        .header("Referer", origin + "/" + parsed.locale() + "/" + parsed.language() + "/")
        .header("Origin", origin)
        .header("x-fr-clientid", clientIdForLocale(parsed.locale()))
        .timeout(Duration.ofSeconds(20))
        .GET()
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ScrapeFailedException(
          ScrapeFailureReason.NETWORK,
          "Could not reach Uniqlo API for " + productId + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScrapeFailedException(
          ScrapeFailureReason.NETWORK,
          "Interrupted while calling Uniqlo API for " + productId);
    }

    int status = response.statusCode();
    if (status != 200) {
      throw new ScrapeFailedException(
          reasonFromHttpStatus(status),
          "Uniqlo API returned status " + status + " for " + productId);
    }

    JsonNode root;
    try {
      root = jsonMapper.readTree(response.body());
    } catch (JacksonException e) {
      throw new ScrapeFailedException(
          ScrapeFailureReason.PARSE,
          "Could not parse Uniqlo API response for " + productId);
    }

    if (!"ok".equals(root.path("status").asString(null))) {
      throw new ScrapeFailedException(
          ScrapeFailureReason.PRODUCT_UNAVAILABLE,
          "Uniqlo API returned non-ok status for " + productId);
    }

    return root.path("result");
  }

  /** Maps HTTP status to a failure reason (404 → product gone, 5xx → transient, etc.). */
  static ScrapeFailureReason reasonFromHttpStatus(int status) {
    if (status == 404) {
      return ScrapeFailureReason.PRODUCT_UNAVAILABLE;
    }
    if (status >= 500) {
      return ScrapeFailureReason.HTTP_5XX;
    }
    if (status >= 400) {
      return ScrapeFailureReason.HTTP_4XX;
    }
    return ScrapeFailureReason.UNKNOWN;
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

    // Normalize both sides — API may return "69" while the URL has "COL69".
    List<JsonNode> filtered = all.stream()
        .filter(l2 -> colorCode == null
            || colorCode.equalsIgnoreCase(
                catalog.normalizeVariantCode(l2.path("color").path("code").asString(null))))
        .filter(l2 -> sizeCode == null
            || sizeCode.equalsIgnoreCase(
                catalog.normalizeVariantCode(l2.path("size").path("code").asString(null))))
        .toList();

    // Exact color+size must match; partial filters fall back only when empty
    // so a bad single filter does not silently use every SKU.
    if (colorCode != null && sizeCode != null) {
      return filtered;
    }
    return filtered.isEmpty() ? List.of() : filtered;
  }

  /** Prefers variant price maps / L2 prices, otherwise top-level product price. */
  private BigDecimal extractPrice(
      JsonNode product, List<JsonNode> relevantL2s, L2Catalog l2Catalog, URI url) {
    for (JsonNode l2 : relevantL2s) {
      BigDecimal fromL2 = priceForL2(l2, blankToNull(l2.path("l2Id").asString(null)), l2Catalog);
      if (fromL2 != null) {
        return fromL2;
      }
    }
    BigDecimal topLevel = priceFromNode(product.path("prices"));
    if (topLevel != null) {
      return topLevel;
    }
    throw new ScrapeFailedException("Could not find a price in Uniqlo API response for " + url);
  }

  private BigDecimal priceForL2(JsonNode l2, String l2Id, L2Catalog l2Catalog) {
    if (l2Id != null) {
      BigDecimal fromMap = priceFromNode(l2Catalog.prices().path(l2Id));
      if (fromMap != null) {
        return fromMap;
      }
    }
    return priceFromNode(l2.path("prices"));
  }

  private BigDecimal priceFromNode(JsonNode prices) {
    if (prices == null || prices.isMissingNode() || prices.isNull()) {
      return null;
    }
    JsonNode promo = prices.path("promo");
    JsonNode priceNode = (!promo.isMissingNode() && !promo.isNull()) ? promo : prices.path("base");
    String rawValue = priceNode.path("value").asString(null);
    if (rawValue == null && priceNode.path("value").isNumber()) {
      rawValue = priceNode.path("value").asString();
    }
    // Jackson may expose numeric values without string form depending on parser settings.
    if (rawValue == null && priceNode.has("value") && !priceNode.path("value").isNull()) {
      rawValue = priceNode.get("value").toString();
    }
    return rawValue != null ? new BigDecimal(rawValue) : null;
  }

  /** Aggregates stock across relevant variants (any in stock → IN_STOCK). */
  private StockStatus extractStockStatus(List<JsonNode> relevantL2s, L2Catalog l2Catalog) {
    if (relevantL2s.isEmpty()) {
      return StockStatus.UNKNOWN;
    }

    boolean sawAny = false;
    boolean anyAvailable = false;

    for (JsonNode l2 : relevantL2s) {
      StockStatus status = stockStatusForL2(
          l2, blankToNull(l2.path("l2Id").asString(null)), l2Catalog);
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

  private StockStatus stockStatusForL2(JsonNode l2, String l2Id, L2Catalog l2Catalog) {
    if (l2Id != null) {
      StockStatus fromMap = stockStatusFromNode(l2Catalog.stocks().path(l2Id));
      if (fromMap != StockStatus.UNKNOWN) {
        return fromMap;
      }
    }
    return stockStatusFromNode(l2.path("stock"));
  }

  private static StockStatus stockStatusFromNode(JsonNode stock) {
    if (stock == null || stock.isMissingNode() || stock.isNull() || stock.isEmpty()) {
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

  /**
   * Thumbnail for the product/color. Prefers commerce API {@code images.main} (full CDN URL;
   * may be {@code ph/phgoods_…} or {@code AsianCommon/goods_…}), then chip, then a locale-based
   * guess. Never fails the scrape if images are missing.
   */
  private String buildThumbnailUrl(String locale, String productId, String colorCode, JsonNode item) {
    String colorDigits = resolveColorDigits(colorCode, item);

    String fromApi = thumbnailFromApiImages(item.path("images"), colorDigits);
    if (fromApi != null) {
      return fromApi;
    }

    return synthesizedThumbnailUrl(locale, productId, colorDigits);
  }

  /** Picks display color digits from the requested code, representative color, or first color. */
  private String resolveColorDigits(String colorCode, JsonNode item) {
    String colorDigits = colorDigitsFromCode(colorCode);
    if (colorDigits == null) {
      colorDigits = colorDigitsFromCode(
          item.path("representative").path("color").path("code").asString(null));
    }
    if (colorDigits == null) {
      JsonNode colors = item.path("colors");
      if (colors.isArray() && colors.size() > 0) {
        colorDigits = colorDigitsFromCode(colors.get(0).path("code").asString(null));
      }
    }
    return colorDigits != null ? colorDigits : "00";
  }

  /**
   * Reads full image URLs from Uniqlo's {@code images.main[color]} / {@code images.chip[color]}
   * maps. Keys are zero-padded color digits (e.g. {@code "00"}, {@code "69"}).
   */
  static String thumbnailFromApiImages(JsonNode images, String colorDigits) {
    if (images == null || images.isMissingNode() || images.isNull() || colorDigits == null) {
      return null;
    }
    String fromMain = blankToNull(images.path("main").path(colorDigits).path("image").asString(null));
    if (fromMain != null) {
      return fromMain;
    }
    // Some colors only ship a chip asset under AsianCommon / regional CDN.
    return blankToNull(images.path("chip").path(colorDigits).asString(null));
  }

  /** Legacy guess when the product payload has no {@code images} map (should be rare on v5). */
  private static String synthesizedThumbnailUrl(String locale, String productId, String colorDigits) {
    String goodsId = goodsIdFromProductId(productId);
    if (goodsId == null) {
      return null;
    }
    String digits = colorDigits != null ? colorDigits : "00";
    String loc = (locale == null || locale.isBlank()) ? "ph" : locale;
    return "https://image.uniqlo.com/UQ/ST3/" + loc + "/imagesgoods/" + goodsId
        + "/item/phgoods_" + digits + "_" + goodsId + "_3x4.jpg?width=369";
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

  /** Parsed locale/language/productId, optional path price group, and variant query codes. */
  private record ParsedProductUrl(
      String locale,
      String language,
      String productId,
      /** Path segment like {@code "01"}, or null when absent / default stripped. */
      String priceGroup,
      String colorCode,
      String sizeCode) {
  }

  /** Parallel L2 price/stock maps from the price-groups endpoint. */
  private record L2Catalog(JsonNode l2s, JsonNode stocks, JsonNode prices) {
  }
}
