package io.github.vedmider.sestacon.adapter.out.parser;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link DocumentParser} using Apache Tika for extraction and a token-based splitter with chunk overlap. */
public class TikaDocumentParser implements DocumentParser {

    /** Chars of the previous chunk prepended to the next, so a fact straddling a boundary stays intact in one chunk. */
    static final int DEFAULT_OVERLAP_CHARS = 200;

    private final TokenTextSplitter splitter = new TokenTextSplitter();
    private final int overlapChars;

    public TikaDocumentParser() {
        this(DEFAULT_OVERLAP_CHARS);
    }

    public TikaDocumentParser(int overlapChars) {
        this.overlapChars = Math.max(overlapChars, 0);
    }

    @Override
    public List<DocumentChunk> parse(String filename, byte[] content) {
        Resource resource = new NamedByteArrayResource(content, filename);
        List<Document> documents;
        try {
            documents = new TikaDocumentReader(resource).get();
        }
        catch (RuntimeException unreadable) {
            documents = List.of();   // binary / image with no OCR backend available
        }
        List<String> texts = splitter.apply(documents).stream()
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        if (texts.isEmpty()) {
            // No extractable text (e.g. an image when no tesseract OCR backend is present). Keep the
            // document retrievable by name with a surrogate chunk + extracted=false, not zero chunks.
            return List.of(new DocumentChunk(filename, "chunk-0", "[" + filename + "]",
                    Map.of("source", filename, "extracted", false), List.of(DocumentChunk.PUBLIC)));
        }
        List<String> overlapped = withOverlap(texts, overlapChars);
        List<DocumentChunk> out = new ArrayList<>(overlapped.size());
        for (int i = 0; i < overlapped.size(); i++) {
            out.add(new DocumentChunk(filename, "chunk-" + i, overlapped.get(i),
                    Map.of("source", filename), List.of(DocumentChunk.PUBLIC)));
        }
        return out;
    }

    /** Prepend the tail of each chunk to the next so boundary-spanning facts survive in at least one chunk. */
    static List<String> withOverlap(List<String> texts, int overlapChars) {
        if (overlapChars <= 0 || texts.size() < 2) {
            return texts;
        }
        List<String> out = new ArrayList<>(texts.size());
        String prevTail = "";
        for (String text : texts) {
            out.add(prevTail.isEmpty() ? text : prevTail + "\n" + text);
            prevTail = text.length() > overlapChars ? text.substring(text.length() - overlapChars) : text;
        }
        return out;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
