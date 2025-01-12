package br.com.gemini.controller;

import br.com.gemini.model.ResponseData;
import br.com.gemini.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/gemini")
public class GeminiController {

	@Autowired
	private GeminiService geminiService;

	@PostMapping("/conversa")
	public ResponseEntity<ResponseData> startConversation(@RequestBody Map<String, String> request) throws Exception {
		String prompt = request.get("prompt");
		if (prompt == null || prompt.isEmpty()) {
			return ResponseEntity.badRequest().body(new ResponseData("Prompt não pode estar vazio"));
		}

		try {
			ResponseData response = geminiService.generateResponse(prompt);
			return ResponseEntity.ok(new ResponseData(response.getResponse()));
		} catch (IOException e) {
			return ResponseEntity.status(500).body(new ResponseData("Erro ao gerar a resposta: " + e.getMessage()));
		}
	}

	@DeleteMapping("/limpar-historico")
	public ResponseEntity<Map<String, String>> limparHistorico() {
		try {
			geminiService.limparHistorico();

			Map<String, String> response = new HashMap<>();
			response.put("message", "Histórico de mensagens apagado com sucesso");
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			e.printStackTrace();
			Map<String, String> response = new HashMap<>();
			response.put("error", "Erro ao tentar apagar o histórico");
			return ResponseEntity.status(500).body(response);
		}
	}
}
