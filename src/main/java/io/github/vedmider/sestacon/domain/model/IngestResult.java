package io.github.vedmider.sestacon.domain.model;

/** Outcome of an ingest: how many chunks were stored and what changed vs the prior version. */
public record IngestResult(int chunks, DocumentDiff diff) {
}
