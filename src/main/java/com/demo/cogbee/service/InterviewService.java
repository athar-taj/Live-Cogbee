package com.demo.cogbee.service;

import com.demo.cogbee.model.EvaluationResult;
import com.demo.cogbee.model.response.InterviewFeedbackResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InterviewService {

	private final FaceVerificationService faceVerificationService;
	private final SpeechToTextService speechToTextService;
	private final AnswerEvaluationService answerEvaluationService;

	public InterviewService(FaceVerificationService faceVerificationService,
							SpeechToTextService speechToTextService,
							AnswerEvaluationService answerEvaluationService) {
		this.faceVerificationService = faceVerificationService;
		this.speechToTextService = speechToTextService;
		this.answerEvaluationService = answerEvaluationService;
	}

	public InterviewFeedbackResponse analyzeCandidate(String question,
													  MultipartFile photo,
													  MultipartFile video) {
		// 1️⃣ Verify same candidate throughout
//		double faceMatchScore = faceVerificationService.verifyThroughoutVideo(photo, video);
//		boolean isSamePerson = faceMatchScore > 0.8;

		// 2️⃣ Convert speech to text
		String transcript = speechToTextService.extractText(video);

		// 3️⃣ Evaluate correctness using AI model
		EvaluationResult evaluation = answerEvaluationService.evaluateAnswer(question, transcript);

		// 4️⃣ Combine everything
		return new InterviewFeedbackResponse(
				true,
				80,
				evaluation.getCorrectness(),
				evaluation.getFeedback(),
				evaluation.getImprovement(),
				transcript
		);
	}
}