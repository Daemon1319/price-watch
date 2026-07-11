package com.allan.price_watch.dashboard;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse;

/** Dashboard summary endpoint for the authenticated user. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /** Returns counts and recent price drops for the home dashboard. */
  @GetMapping("/summary")
  public DashboardSummaryResponse summary(@AuthenticationPrincipal UUID userId) {
    return dashboardService.getSummary(userId);
  }
}
