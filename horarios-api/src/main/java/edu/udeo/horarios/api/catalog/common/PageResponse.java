package edu.udeo.horarios.api.catalog.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
