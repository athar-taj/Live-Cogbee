package com.demo.cogbee.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFeedbackResponse {
	private boolean isSamePersonThroughout;
	private double averageFaceMatchScore;
	private double correctness;
	private String feedback;
	private String improvement;
	private String transcript;
}