package com.housingplatform.exhibition.dto;

import java.util.List;
import java.util.UUID;

/** Which stored destinations to push a live broadcast to. Empty/null = all enabled targets. */
public record SimulcastStartRequest(List<UUID> targetIds) {}
