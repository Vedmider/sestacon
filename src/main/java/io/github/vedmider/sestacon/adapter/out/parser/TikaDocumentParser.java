package io.github.vedmider.sestacon.adapter.out.parser;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link DocumentParser} using Apache Tika for extraction and a token-based splitter. */
public class TikaDocumentParser implements DocumentParser {

    private final TokenTextSplitter splitter = new TokenTextSplitter();

    @Override
    public List<DocumentChunk> parse(String filename, byte[] content) {
        Resource resource = new NamedByteArrayResource(content, filename);
        List<Document> documents = new TikaDocumentReader(resource).get();
        AtomicInteger index = new AtomicInteger();
        return splitter.apply(documents).stream()
                .map(d -> new DocumentChunk(filename, "chunk-" + index.getAndIncrement(),
                        d.getText(), Map.of("source", filename), List.of(DocumentChunk.PUBLIC)))
                .toList();
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
