package br.com.gemini.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.gemini.model.DocumentEmbedding;

@Service
public class EmbeddingService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    public List<Double> embedRetrievalQuery(String queryText) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> part = new HashMap<>();
        part.put("text", queryText);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> body = new HashMap<>();
        body.put("model", "models/text-embedding-004");
        body.put("content", content);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        JsonNode responseJson = objectMapper.readTree(response.getBody());

        JsonNode embeddingNode = responseJson.path("embedding");
        List<Double> embeddings = new ArrayList<>();
        if (embeddingNode.isObject()) {
            JsonNode valuesNode = embeddingNode.path("values");
            if (valuesNode.isArray()) {
                for (JsonNode value : valuesNode) {
                    embeddings.add(value.asDouble());
                }
            }
        }
        if (embeddings.isEmpty()) {
            throw new Exception("Nenhum valor de embedding encontrado na resposta.");
        }

        return embeddings;
    }

    public List<DocumentEmbedding> incorporarDocumentos(List<String> docTexts) throws Exception {
        List<DocumentEmbedding> embeddings = new ArrayList<>();
        for (String docText : docTexts) {
            List<Double> embeddingValues = embedRetrievalQuery(docText);
            DocumentEmbedding embedding = new DocumentEmbedding(docText, embeddingValues);
            embeddings.add(embedding);
        }
        return embeddings;
    }

    public List<String> leArquivos(List<String> arquivos) {
        List<String> documentos = new ArrayList<>();
        for (String filePath : arquivos) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                documentos.add(content);
            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo: " + filePath);
                e.printStackTrace();
            }
        }
        return documentos;
    }

    public double similaridadeCosseno(List<Double> a, List<Double> b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += Math.pow(a.get(i), 2);
            normB += Math.pow(b.get(i), 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    public DocumentEmbedding incorporarPergunta(String queryText, List<DocumentEmbedding> docs) throws Exception {
    	if (queryText == null || queryText.trim().isEmpty()) {
            throw new Exception("O texto da consulta está vazio.");
        }

        List<Double> queryValues = embedRetrievalQuery(queryText);
        if (queryValues == null || queryValues.isEmpty()) {
            throw new Exception("Não foi possível gerar os valores de embedding para a query.");
        }

        DocumentEmbedding bestDoc = null;
        double maxSimilarity = -1.0;
        for (DocumentEmbedding doc : docs) {
            if (doc.getValues() == null || doc.getValues().isEmpty()) {
                System.out.println("Documento ignorado, sem valores de embedding: " + doc.getText());
                continue;
            }
            
            double similarity = similaridadeCosseno(doc.getValues(), queryValues);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestDoc = doc;
            }
        }
        
        if (bestDoc == null) {
            throw new Exception("Nenhum documento relevante encontrado.");
        }
        return bestDoc;
    }
}
