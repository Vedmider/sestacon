package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;

import java.util.List;

/** Outbound port for extracting and chunking text from an uploaded document. */
public interface DocumentParser {

    List<DocumentChunk> parse(String filename, byte[] content);
}
