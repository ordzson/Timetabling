package edu.udeo.horarios.api.importing;

import java.util.Map;

record ImportResponse(
    long importBatchId, String status, String filename, Map<String, Integer> summary, int errorCount) {}
