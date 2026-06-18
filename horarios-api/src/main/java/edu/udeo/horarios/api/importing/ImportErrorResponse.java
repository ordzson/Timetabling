package edu.udeo.horarios.api.importing;

record ImportErrorResponse(
    long id,
    String sheetName,
    Integer rowNumber,
    String columnName,
    String rawValue,
    String code,
    String message,
    String suggestedAction) {}
