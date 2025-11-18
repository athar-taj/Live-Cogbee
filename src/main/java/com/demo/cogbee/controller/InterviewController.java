package com.demo.cogbee.controller;

import com.demo.cogbee.model.EvaluationResult;
import com.demo.cogbee.model.response.InterviewFeedbackResponse;
import com.demo.cogbee.service.InterviewService;
import com.demo.cogbee.service.SpeechToTextService;
import com.demo.cogbee.service.live.AsrService;
import com.demo.cogbee.service.live.EvaluationService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@AllArgsConstructor
@RequestMapping("/api/interview")
public class InterviewController {

	private final InterviewService interviewService;

	@Autowired
	private AsrService asrService;

	@Autowired
	private EvaluationService evaluationService;

    @Autowired
    private SpeechToTextService speechToTextService;

    private static final String SESSION_DIR = "/tmp/stt-sessions/";


//	@PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//	public ResponseEntity<InterviewFeedbackResponse> analyzeCandidate(
//			@RequestParam("question") String question,
//			@RequestParam("photo") MultipartFile candidatePhoto,
//			@RequestParam("video") MultipartFile answerVideo) throws IOException {
//
//		InterviewFeedbackResponse response =
//				interviewService.analyzeCandidate(question, candidatePhoto, answerVideo);
//
//		return ResponseEntity.ok(response);
//	}

    @GetMapping("/test")
    public String test() {
        return "testing";
    }

	@PostMapping("/upload")
	public ResponseEntity<?> uploadAnswer(@RequestParam("sessionId") String sessionId,
										  @RequestParam("file") MultipartFile file) throws IOException {

		Path tmpDir = Files.createTempDirectory("answers");
		Path filePath = tmpDir.resolve(file.getOriginalFilename());
		Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

		String transcript = asrService.transcribeFile(filePath.toFile());

		EvaluationResult result = evaluationService.evaluateTranscript(transcript);

		String topic = "/topic/feedback/" + sessionId;
//		messagingTemplate.convertAndSend(topic, new FeedbackPayload(result.getCorrectness(), result.getFeedback()));

		return ResponseEntity.ok().build();
	}


    @PostMapping(value = "/answer-chunk", consumes = "application/octet-stream")
    public ResponseEntity<String> receiveChunk(
            @RequestParam String sessionId,
            InputStream body
    ) throws IOException {

        File dir = new File(SESSION_DIR + sessionId);
        dir.mkdirs();

        File wavFile = new File(dir, "recording.wav");

        try (OutputStream out = new FileOutputStream(wavFile, true)) {
            body.transferTo(out); // APPEND
        }

        System.out.println("⬆ WAV chunk stored for: " + sessionId);
        return ResponseEntity.ok("Chunk OK");
    }

    @PostMapping("/answer-finish")
    public ResponseEntity<InterviewFeedbackResponse> finish(
            @RequestParam String sessionId
    ) throws Exception {

        File wavFile = new File(SESSION_DIR + sessionId + "/recording.wav");

        if (!wavFile.exists()) {
            return ResponseEntity.badRequest()
                    .body(new InterviewFeedbackResponse(false, 0, 0, "No WAV found", "", ""));
        }

        System.out.println("📁 Finalizing WAV session: " + sessionId);

        InterviewFeedbackResponse response =
                interviewService.analyzeCandidate(wavFile);

        return ResponseEntity.ok(response);
    }

    public static class FeedbackPayload {
		private double score;
		private String feedback;
		public FeedbackPayload(double score, String feedback) { this.score = score; this.feedback = feedback; }
		public double getScore() { return score; }
		public String getFeedback() { return feedback; }
	}
}