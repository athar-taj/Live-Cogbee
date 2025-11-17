package com.demo.cogbee.service;

import com.demo.cogbee.model.EvaluationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnswerEvaluationService {

	private static final String GEMINI_API_URL =
			"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

	private static final String GEMINI_API_KEY = "AIzaSyBl6OgjJSpbwlg2kP8wnkjhoTG9DrAq6xk";

	public EvaluationResult evaluateAnswer(String question, String answerText) {
		try {
			String prompt = """
                    You are a neutral and minimalistic interview answer evaluator.
                    
                    Your ONLY task:
                    Evaluate the candidate’s spoken answer (already converted to text) STRICTLY based on:
                    - whether it answers the question asked,
                    - whether the concept explained is basically correct,
                    - whether the explanation demonstrates understanding.
                    
                    Do NOT:
                    - expect deeper details than what was asked,
                    - add nested or follow-up concepts,
                    - give multiple improvement topics,
                    - penalize simple explanations if they are correct,
                    - over-judge grammar, fillers, or speaking style.
                    
                    Evaluation behavior:
                    - If the answer is basically correct (even if short or simple), give a high score.
                    - If the answer is partially correct, give a medium score and ONE improvement suggestion.
                    - If the answer is incorrect, give a low score and ONE improvement topic.
                    - Keep feedback concise and focused ONLY on the candidate’s actual answer.
                    
                    Response format (JSON only):
                    {
                      "correctness": <0-100>,
                      "feedback": "<short feedback on clarity or correctness>",
                      "improvementTopic": "<one specific concept to improve, or empty if answer is good>"
                    }
                    
                    Scoring rules:
                    - correctness >= 70 → The answer is conceptually correct; give short positive feedback; improvementTopic must be empty.
                    - correctness < 70 → The answer is incomplete or contains misunderstanding; suggest exactly ONE improvement topic.
                    
					Example:
					If the candidate’s answer shows partial understanding, respond like:
					"Good effort, but the explanation misses the key idea of how the process works. Review the concept of synchronization in general terms."
						
					If the answer is mostly right, respond like:
					"Clear and accurate explanation overall. Just elaborate slightly on how it applies in real-world scenarios."
					""";
			
			String input = String.format("Question: %s\nAnswer: %s", question, answerText);

			Map<String, Object> textPart = Map.of("text", prompt + "\n\n" + input);
			Map<String, Object> content = Map.of("parts", List.of(textPart));

			Map<String, Object> requestBody = Map.of("contents", List.of(content));

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("x-goog-api-key", GEMINI_API_KEY);

			HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<String> response = restTemplate.exchange(
					GEMINI_API_URL, HttpMethod.POST, entity, String.class);

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(response.getBody());

			String text = root.path("candidates").get(0)
					.path("content").path("parts").get(0)
					.path("text").asText();

			// ✅ Extract the JSON part (even if wrapped in ```json ... ```)
			String jsonString = extractJson(text);

			JsonNode aiJson = mapper.readTree(jsonString);

			double correctness = aiJson.has("correctness") ? aiJson.get("correctness").asDouble() : 0.0;
			String feedback = aiJson.has("feedback") ? aiJson.get("feedback").asText() : "No feedback provided";
			String improvement = aiJson.has("improvementTopic") ? aiJson.get("improvementTopic").asText() : "";

			return new EvaluationResult(correctness, feedback, improvement);

		} catch (Exception e) {
			e.printStackTrace();
			return new EvaluationResult(70.0, "Could not evaluate properly. Try again.", "Error fallback");
		}
	}

	/**
	 * Extracts the JSON block from a text that may contain markdown code fences like ```json ... ```
	 */
	private String extractJson(String text) {
		// Match text inside triple backticks or curly braces
		Pattern pattern = Pattern.compile("(?s)```(?:json)?(.*?)```");
		Matcher matcher = pattern.matcher(text);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}

		// Fallback: try to find first '{' and last '}'
		int start = text.indexOf("{");
		int end = text.lastIndexOf("}");
		if (start != -1 && end != -1 && end > start) {
			return text.substring(start, end + 1);
		}

		// If no JSON detected, return as plain string
		return "{\"correctness\": 0, \"feedback\": \"" + text.replace("\"", "'") + "\", \"improvementTopic\": \"General\"}";
	}
}
