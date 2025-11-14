package com.demo.cogbee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class SpeechToTextService {

    private static final String API_KEY_ID = "cToXCAwEH8yIUKxo";
    private static final String API_KEY_SECRET = "V6sTL9ix794v9eKt";
    private static final String LANG = "en";
    private static final int RESULT_TYPE = 4;

    /**
     * MAIN ENTRY:
     * Converts ANY byte[] chunk (audio/video) into transcribed text
     */
    public String transcribeChunk(byte[] chunkBytes) {
        File tempFile = null;
        try {
            // 1️⃣ Write the chunk bytes to a temp .webm file
            tempFile = File.createTempFile("chunk_", ".webm");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(chunkBytes);
            }

            // 2️⃣ Create SpeechFlow task
            String taskId = createTranscription(tempFile);
            if (taskId == null) {
                throw new RuntimeException("Failed to create SpeechFlow task");
            }

            // 3️⃣ Wait for result
            return queryResult(taskId);

        } catch (Exception e) {
            throw new RuntimeException("Error transcribing chunk: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }



    // -------------------------------
    // SPEECHFLOW API HELPERS
    // -------------------------------
    private String createTranscription(File file) throws Exception {
        String createUrl = "https://api.speechflow.io/asr/file/v1/create";

        HttpURLConnection connection = (HttpURLConnection) new URL(createUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setConnectTimeout(5 * 60 * 1000);
        connection.setReadTimeout(5 * 60 * 1000);
        connection.setRequestProperty("keyId", API_KEY_ID);
        connection.setRequestProperty("keySecret", API_KEY_SECRET);

        String boundary = "-----" + System.currentTimeMillis();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {

            // lang field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"lang\"\r\n\r\n");
            out.writeBytes(LANG + "\r\n");

            // file field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
            out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
        }

        String response = readResponse(connection);
        JsonNode json = new ObjectMapper().readTree(response);

        if (json.get("code").asInt() == 10000)
            return json.get("taskId").asText();

        throw new RuntimeException("Create error: " + json.get("msg").asText());
    }


    private String queryResult(String taskId) throws Exception {
        String queryUrl = "https://api.speechflow.io/asr/file/v1/query?taskId=" + taskId + "&resultType=" + RESULT_TYPE;

        while (true) {
            HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("keyId", API_KEY_ID);
            conn.setRequestProperty("keySecret", API_KEY_SECRET);

            String response = readResponse(conn);
            JsonNode json = new ObjectMapper().readTree(response);

            int code = json.get("code").asInt();

            if (code == 11000) {
                return json.has("result") ? json.get("result").asText() : "";
            } else if (code == 11001) {
                Thread.sleep(2000);
            } else {
                throw new RuntimeException("Query failed: " + json.get("msg").asText());
            }
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
