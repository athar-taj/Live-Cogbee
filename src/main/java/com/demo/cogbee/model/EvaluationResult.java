package com.demo.cogbee.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EvaluationResult {
	private double correctness;
	private String feedback;
	private String improvement;
}