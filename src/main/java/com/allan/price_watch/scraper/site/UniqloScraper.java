package com.allan.price_watch.scraper.site;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

/**
 * Uniqlo product pages are a client-side rendered SPA — price/stock never
 * appear in the server-rendered HTML at all (confirmed by fetching a live
 * page directly: full description, images, colors present, price nowhere).
 * A JSoup-selector approach is architecturally the wrong tool for this
 * site, so this scraper instead calls the same internal JSON API the
 * site's own frontend uses, confirmed live against:
 *
 * <pre>
 * https://www.uniqlo.com/ph/api/commerce/v3/en/products?productIds=E484203-000&amp;isV2Review=false&amp;imageRatio=3x4
 * </pre>
 *
 * <p>The locale ({@code ph}) and language ({@code en}) segments are parsed
 * out of the submitted product URL rather than hardcoded, so this works
 * across Uniqlo's other country sites without changes, on the assumption
 * they follow the same {@code /{locale}/{language}/products/{id}/...}
 * path shape observed on the PH site.
 *
 * <p><b>Known gap: stock status is always {@code UNKNOWN}.</b> The
 * product-detail response includes colors/sizes with a {@code states}
 * array (e.g. {@code ["display", "sale"]}), but every example captured so
 * far shows identical states regardless of actual availability — nothing
 * in this response has been confirmed to indicate "sold out." A separate
 * endpoint (possibly a {@code withStocks=true}-style parameter, per
 * publicly documented reverse-engineering of Uniqlo's API) likely carries
 * per-size inventory; this needs a real sold-out product's network trace
 * to confirm and implement properly.
 */
@Component
public class UniqloScraper implements Scraper {

  private static final Pattern LOCALE_AND_PRODUCT_ID_PATTERN =
      Pattern.compile("^/([a-z]{2})/([a-z]{2})/products/([A-Z0-9-]+)");

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
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
    String host = url.getHost();
    return host != null && host.contains("uniqlo.com");
  }

  @Override
  public ScrapeResult fetch(URI url) {
    Matcher matcher = LOCALE_AND_PRODUCT_ID_PATTERN.matcher(url.getPath());
    if (!matcher.find()) {
      throw new ScrapeFailedException("Could not parse locale/product id from Uniqlo URL: " + url);
    }
    String locale = matcher.group(1);
    String language = matcher.group(2);
    String productId = matcher.group(3);

    URI apiUrl = URI.create("https://" + url.getHost() + "/" + locale + "/api/commerce/v3/"
        + language + "/products?productIds=" + productId + "&isV2Review=false&imageRatio=3x4");

    JsonNode item = fetchProductItem(apiUrl, productId);

    return new ScrapeResult(
        item.path("name").asString(null),
        extractPrice(item, url),
        StockStatus.UNKNOWN, // see class Javadoc — not available in this response
        extractThumbnailUrl(item));
  }

  private JsonNode fetchProductItem(URI apiUrl, String productId) {
    HttpRequest request = HttpRequest.newBuilder(apiUrl)
        .header("Accept", "application/json")
        .timeout(Duration.ofSeconds(10))
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

    JsonNode items;
    try {
      items = jsonMapper.readTree(response.body()).path("result").path("items");
    } catch (JacksonException e) {
      throw new ScrapeFailedException("Could not parse Uniqlo API response for " + productId);
    }

    if (!items.isArray() || items.size() == 0) {
      throw new ScrapeFailedException("Uniqlo API returned no items for product " + productId);
    }

    return items.get(0);
  }

  /** Prefers the promo (sale) price over base when both are present — that's the price a shopper actually pays. */
  private BigDecimal extractPrice(JsonNode item, URI url) {
    JsonNode prices = item.path("prices");
    JsonNode promo = prices.path("promo");
    JsonNode priceNode = (!promo.isMissingNode() && !promo.isNull()) ? promo : prices.path("base");

    String rawValue = priceNode.path("value").asString(null);
    if (rawValue == null) {
      throw new ScrapeFailedException("Could not find a price in Uniqlo API response for " + url);
    }
    return new BigDecimal(rawValue);
  }

  private String extractThumbnailUrl(JsonNode item) {
    JsonNode mainImages = item.path("images").path("main");
    if (mainImages.isArray() && mainImages.size() > 0) {
      return mainImages.get(0).path("url").asString(null);
    }
    return null;
  }
}