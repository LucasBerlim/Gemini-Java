package br.com.gemini.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
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

import br.com.gemini.model.ChatMessage;
import br.com.gemini.model.DocumentEmbedding;
import br.com.gemini.model.ResponseData;
import jakarta.annotation.PostConstruct;

@Service
public class GeminiService {

	@Autowired
	private EmbeddingService embeddingService;

	@Value("${gemini.api.key}")
	private String apiKey;

	private List<String> caminhoDocs = buscarCaminhosDocumentos("src/main/java/br/com/gemini/documents/");

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private List<ChatMessage> chatHistory;

	public GeminiService() {
		this.restTemplate = new RestTemplate();
		this.objectMapper = new ObjectMapper();
	}

	@PostConstruct
	public void initialize() {
		chatHistory = new ArrayList<>();
		chatHistory.add(new ChatMessage("user",
				"Você é Berlim, um chatbot super amigável. Você representa a empresa 'Berlim games wiki', sua função é tirar dúvidas sobre jogos de videogame."));
		chatHistory.add(new ChatMessage("ai",
				"Olá! Obrigado por entrar em contato. Gostaria de tirar dúvidas sobre qual jogo?"));
	}

	public ResponseData generateResponse(String prompt) throws Exception {
		List<String> arquivosLidos = embeddingService.leArquivos(caminhoDocs);
		List<DocumentEmbedding> documentos = embeddingService.incorporarDocumentos(arquivosLidos);

		DocumentEmbedding doc = embeddingService.incorporarPergunta(prompt, documentos);

		String mensagem = prompt
				+ " - Os seguintes dados foram forncecidos por Berlim Wiki Games, só fale sobre esses dados se for perguntado sobre e considere as informações a seguir como fontes confiáveis: "
				+ doc.getText();

		chatHistory.add(new ChatMessage("user", mensagem));

		String historyString = chatHistory.stream().map(ChatMessage::toString).reduce((s1, s2) -> s1 + "\n" + s2)
				.orElse("");

		String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key="
				+ apiKey;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> part = new HashMap<>();
		part.put("text", historyString);

		Map<String, Object> content = new HashMap<>();
		content.put("parts", List.of(part));

		Map<String, Object> generationConfig = new HashMap<>();
		generationConfig.put("maxOutputTokens", 1000);

		Map<String, Object> body = new HashMap<>();
		body.put("contents", List.of(content));
		body.put("generationConfig", generationConfig);

		HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

		JsonNode responseJson = objectMapper.readTree(response.getBody());

		String aiResponse = responseJson.path("candidates").path(0).path("content").path("parts").path(0).path("text")
				.asText();

		chatHistory.add(new ChatMessage("ai", aiResponse));
		ResponseData responseData = new ResponseData(aiResponse);
		return responseData;
	}

	public void limparHistorico() {
		chatHistory.clear();
	}

	public List<String> buscarCaminhosDocumentos(String directoryPath) {
		List<String> filePaths = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(Paths.get(directoryPath))) {
			filePaths = paths.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.toList());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return filePaths;
	}
}
