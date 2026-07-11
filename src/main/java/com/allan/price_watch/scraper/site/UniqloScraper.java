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

import com.allan.price_watch.common.exception.ScrapeFailedException;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.scraper.ScrapeResult;
import com.allan.price_watch.scraper.Scraper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Scrapes Uniqlo product pages via their commerce API. */
@Component
public class UniqloScraper implements Scraper {

  private static final Pattern LOCALE_AND_PRODUCT_ID_PATTERN =
      Pattern.compile("^/([a-z]{2})/([a-z]{2})/products/([A-Z0-9-]+)");

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

  public UniqloScraper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
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

  /** Calls the commerce API and returns name, price, stock, and thumbnail. */
  @Override
  public ScrapeResult fetch(URI url) {
    Matcher matcher = LOCALE_AND_PRODUCT_ID_PATTERN.matcher(url.getPath());
    if (!matcher.find()) {
      throw new ScrapeFailedException("Could not parse locale/product id from Uniqlo URL: " + url);
    }
    String locale = matcher.group(1);
    String language = matcher.group(2);
    String productId = matcher.group(3);

    String colorCode = queryParam(url, "colorCode");
    String sizeCode = queryParam(url, "sizeCode");

    URI apiUrl = URI.create("https://" + url.getHost() + "/" + locale + "/api/commerce/v3/"
        + language + "/products/" + productId + "?isV2Review=true&withStocks=true");

    JsonNode item = fetchProductItem(apiUrl, productId);
    List<JsonNode> relevantL2s = filterL2s(item.path("l2s"), colorCode, sizeCode);

    return new ScrapeResult(
        item.path("name").asString(null),
        extractPrice(item, relevantL2s, url),
        extractStockStatus(relevantL2s),
        buildThumbnailUrl(locale, productId, colorCode, item));
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
        .filter(l2 -> colorCode == null || colorCode.equalsIgnoreCase(l2.path("color").path("code").asString("")))
        .filter(l2 -> sizeCode == null || sizeCode.equalsIgnoreCase(l2.path("size").path("code").asString("")))
        .toList();

    if (colorCode != null && sizeCode != null) {
      return filtered;
    }
    return filtered.isEmpty() ? all : filtered;
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
      JsonNode stock = l2.path("stock");
      if (stock.isMissingNode() || stock.isNull()) {
        continue;
      }
      sawAny = true;
      String code = stock.path("statusCode").asString("");
      if ("IN_STOCK".equals(code) || "LOW_STOCK".equals(code)) {
        anyAvailable = true;
        break;
      }
    }

    if (!sawAny) {
      return StockStatus.UNKNOWN;
    }
    return anyAvailable ? StockStatus.IN_STOCK : StockStatus.OUT_OF_STOCK;
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

  /** Extracts color digits from codes like COL03. */
  private static String colorDigitsFromCode(String colorCode) {
    if (colorCode == null || colorCode.isBlank()) {
      return null;
    }
    String upper = colorCode.trim().toUpperCase(Locale.ROOT);
    if (upper.startsWith("COL")) {
      String digits = upper.substring(3);
      return digits.isBlank() ? null : digits;
    }
    if (upper.matches("\\d+")) {
      return upper;
    }
    return null;
  }

  private static String queryParam(URI url, String name) {
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
}
