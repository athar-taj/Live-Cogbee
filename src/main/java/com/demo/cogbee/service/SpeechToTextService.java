package com.demo.cogbee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


@Service
public class SpeechToTextService {

	private static final String API_KEY_ID = "cToXCAwEH8yIUKxo";
	private static final String API_KEY_SECRET = "V6sTL9ix794v9eKt";
	private static final String LANG = "en";
	private static final int RESULT_TYPE = 4;

	public String extractText(MultipartFile video) {
		File tempFile = null;
		try {
			// 1️⃣ Save MultipartFile to a temp file
			tempFile = File.createTempFile("speechflow_", "_" + video.getOriginalFilename());
			video.transferTo(tempFile);

			String taskId = createTranscription(tempFile);

			if (taskId == null) {
				throw new RuntimeException("Failed to create transcription task");
			}

			return queryResult(taskId);

		} catch (Exception e) {
			throw new RuntimeException("SpeechFlow transcription failed: " + e.getMessage(), e);
		} finally {
			if (tempFile != null && tempFile.exists()) {
				tempFile.delete();
			}
		}
	}

	private String createTranscription(File file) throws Exception {
		String createUrl = "https://api.speechflow.io/asr/file/v1/create";
		URL url = new URL(createUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setConnectTimeout(5 * 60 * 1000);
		connection.setReadTimeout(5 * 60 * 1000);
		connection.setRequestProperty("keyId", API_KEY_ID);
		connection.setRequestProperty("keySecret", API_KEY_SECRET);

		// multipart form data
		String boundary = "---------------------------" + System.currentTimeMillis();
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

		try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
			// lang param
			String langPart = "--" + boundary + "\r\n"
					+ "Content-Disposition: form-data; name=\"lang\"\r\n\r\n"
					+ LANG + "\r\n";
			out.write(langPart.getBytes());

			// file param
			String fileHeader = "--" + boundary + "\r\n"
					+ "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
					+ "Content-Type: application/octet-stream\r\n\r\n";
			out.write(fileHeader.getBytes());

			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
			}

			out.write("\r\n".getBytes());
			out.write(("--" + boundary + "--\r\n").getBytes());
			out.flush();
		}

		String response = readResponse(connection);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode json = mapper.readTree(response);

		if (json.get("code").asInt() == 10000) {
			return json.get("taskId").asText();
		} else {
			String msg = json.has("msg") ? json.get("msg").asText() : "Unknown error";
			throw new RuntimeException("Create task failed: " + msg);
		}

	}

	private String queryResult(String taskId) throws Exception {
		String queryUrl = "https://api.speechflow.io/asr/file/v1/query?taskId=" + taskId + "&resultType=" + RESULT_TYPE;

		while (true) {
			HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("keyId", API_KEY_ID);
			conn.setRequestProperty("keySecret", API_KEY_SECRET);

			String response = readResponse(conn);

			ObjectMapper mapper = new ObjectMapper();
			JsonNode json = mapper.readTree(response);
			int code = json.get("code").asInt();

			if (code == 11000) {
				return json.has("result") ? json.get("result").asText() : "";
			} else if (code == 11001) {
				Thread.sleep(3000);
			} else {
				String msg = json.has("msg") ? json.get("msg").asText() : "Unknown error";
				throw new RuntimeException("Transcription failed: " + msg);
			}
		}
	}

	public String transcribeChunk(byte[] audioBytes) {
		try {
			// 1️⃣ Write bytes to a temporary file (SpeechFlow needs a file)
			File tempFile = File.createTempFile("chunk_", ".webm");
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				fos.write(audioBytes);
			}

			// 2️⃣ Call your existing method but skip the MultipartFile wrapper
			String taskId = createTranscription(tempFile);

			// 3️⃣ Query SpeechFlow for result
			return queryResult(taskId);

		} catch (Exception e) {
			throw new RuntimeException("SpeechFlow chunk transcription failed: " + e.getMessage(), e);
		}
	}


	private String readResponse(HttpURLConnection conn) throws IOException {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		}
	}

}
