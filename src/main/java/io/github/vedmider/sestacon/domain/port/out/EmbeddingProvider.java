package io.github.vedmider.sestacon.domain.port.out;

/** Outbound port for turning text into an embedding vector. */
public interface EmbeddingProvider {

    float[] embed(String text);

    int dimensions();
}
