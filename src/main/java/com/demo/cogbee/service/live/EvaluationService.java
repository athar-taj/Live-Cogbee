package com.demo.cogbee.service.live;

import com.demo.cogbee.model.EvaluationResult;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class EvaluationService {

	private static final Set<String> REQUIRED_KEYWORDS = new HashSet<>(Arrays.asList(
			"design", "architecture", "scalability", "database", "latency"
	));

	public EvaluationResult evaluateTranscript(String transcript) {
		String normalized = transcript == null ? "" : transcript.toLowerCase();

		// 1) Keyword coverage
		int found = 0;
		for (String kw : REQUIRED_KEYWORDS) {
			if (normalized.contains(kw)) found++;
		}
		double kwScore = REQUIRED_KEYWORDS.isEmpty() ? 0.0 : ((double) found / REQUIRED_KEYWORDS.size());

		// 2) Filler word penalty
		int fillerCount = countOccurrences(normalized, "um") + countOccurrences(normalized, "uh") + countOccurrences(normalized, "like");
		int totalWords = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
		double fillerScore = totalWords == 0 ? 1.0 : Math.max(0.0, 1.0 - ((double) fillerCount / totalWords));

		// 3) Basic fluency (words per second) — if you don't have duration, we can't compute here.
		// For MVP we approximate fluency by words count normalization (cap at 1.0)
		double wpsScore = Math.min(1.0, totalWords / 120.0); // expects ~120 words in 1 minute as 'ideal'

		// Weighted final score
		double finalNormalized = 0.6 * kwScore + 0.25 * wpsScore + 0.15 * fillerScore;
		double percent = finalNormalized * 100.0;

		StringBuilder feedback = new StringBuilder();
		feedback.append("Keywords matched: ").append(found).append("/").append(REQUIRED_KEYWORDS.size()).append(". ");
		feedback.append("Filler words: ").append(fillerCount).append(". ");
		if (kwScore < 0.5) feedback.append("Try to mention more key concepts (e.g., design, scalability).");

		return new EvaluationResult(percent, feedback.toString(),"");
	}

	private int countOccurrences(String text, String sub) {
		int idx = 0, count = 0;
		while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
		return count;
	}
}
