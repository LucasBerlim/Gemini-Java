package br.com.gemini.model;

import java.util.List;

public class DocumentEmbedding {
	private String text;
    private List<Double> values;

    public DocumentEmbedding(String text, List<Double> values) {
        this.text = text;
        this.values = values;
    }

    public String getText() {
        return text;
    }

    public List<Double> getValues() {
        return values;
    }
}
