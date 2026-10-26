package com.shopchain.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GeminiEmbeddingModel implements EmbeddingModel {
    private final GeminiApiClient gemini;

    public GeminiEmbeddingModel(GeminiApiClient gemini) {
        this.gemini = gemini;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());
        for (int index = 0; index < request.getInstructions().size(); index++) {
            embeddings.add(new Embedding(gemini.embed(request.getInstructions().get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return gemini.embed(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return gemini.dimensions();
    }
}
