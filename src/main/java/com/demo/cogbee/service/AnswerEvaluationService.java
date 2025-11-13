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
						You are an intelligent interview evaluator.
						
						Your task:
						Evaluate the candidate’s spoken answer (converted to text) for **conceptual correctness**, **clarity of thought**, and **coverage of the topic** — while **ignoring grammar mistakes, filler words, pronunciation issues, or informal phrasing**.
						
						Guidelines:
						- Focus on whether the candidate understood and explained the correct concept.
						- Minor language or structure issues should not reduce correctness.
						- Accept partially correct or simplified answers if they convey the main idea.
						- Be objective and concise in your judgment.
						
						Response format (JSON only, no markdown or code fences):
						{
						  "correctness": <number between 0 and 100>,
						  "feedback": "<short feedback on clarity or correctness>",
						  "improvementTopic": "<specific topic or concept to improve, or empty if not needed>"
						}
						
						Scoring rules:
						- correctness >= 70 → The answer is conceptually correct; give brief positive feedback.
						- correctness < 70 → The answer is incomplete or incorrect; suggest one key improvement.
						
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
