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

        File webmFile = null;
        File mp4File = null;

        try {
            // 1️⃣ Save incoming WEBM to temp file
            webmFile = File.createTempFile("upload_", ".webm");
            video.transferTo(webmFile);

            // 2️⃣ Convert WEBM → MP4 (SpeechFlow friendly)
            mp4File = File.createTempFile("converted_", ".mp4");
            convertToMp4(webmFile, mp4File);

            // 3️⃣ Save MP4 permanently for debugging
            saveDebugCopy(mp4File);

            // 4️⃣ Create SpeechFlow task with MP4 file
            String taskId = createTranscription(mp4File);

            if (taskId == null)
                throw new RuntimeException("Failed to create transcription task");

            // 5️⃣ Fetch transcription
            return queryResult(taskId);

        } catch (Exception e) {
            throw new RuntimeException("SpeechFlow transcription failed: " + e.getMessage(), e);
        } finally {
            if (webmFile != null) webmFile.delete();
            if (mp4File != null) mp4File.delete();
        }
    }

    // -------------------------
    // Convert WebM → MP4
    // -------------------------
    private void convertToMp4(File input, File output) throws Exception {

        // H.264 + AAC → BEST compatibility with SpeechFlow
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", input.getAbsolutePath(),
                "-vcodec", "libx264",
                "-acodec", "aac",
                "-ar", "16000",
                "-ac", "1",
                output.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        Process p = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("FFmpeg: " + line);
        }

        int exitCode = p.waitFor();
        if (exitCode != 0)
            throw new RuntimeException("FFmpeg MP4 conversion failed, exitCode=" + exitCode);
    }

    // -------------------------
    // Save MP4 for debugging
    // -------------------------
    private void saveDebugCopy(File mp4File) throws IOException {
        File debugDir = new File("debug_videos");
        if (!debugDir.exists()) debugDir.mkdir();

        File savedFile = new File(debugDir, "debug_" + System.currentTimeMillis() + ".mp4");

        try (InputStream in = new FileInputStream(mp4File);
             FileOutputStream out = new FileOutputStream(savedFile)) {

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }

        System.out.println("📁 Saved debug MP4: " + savedFile.getAbsolutePath());
    }

    // -------------------------
    // SpeechFlow create task
    // -------------------------
    private String createTranscription(File file) throws Exception {
        String createUrl = "https://api.speechflow.io/asr/file/v1/create";
        HttpURLConnection connection = (HttpURLConnection) new URL(createUrl).openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("keyId", API_KEY_ID);
        connection.setRequestProperty("keySecret", API_KEY_SECRET);

        String boundary = "----" + System.currentTimeMillis();
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {

            // lang field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"lang\"\r\n\r\n");
            out.writeBytes(LANG + "\r\n");

            // file field
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp4\"\r\n");
            out.writeBytes("Content-Type: video/mp4\r\n\r\n");

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }

            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        String response = readResponse(connection);
        JsonNode json = new ObjectMapper().readTree(response);

        if (json.get("code").asInt() == 10000)
            return json.get("taskId").asText();

        throw new RuntimeException("Create failed: " + json.get("msg").asText());
    }

    // -------------------------
    // SpeechFlow poll task
    // -------------------------
    private String queryResult(String taskId) throws Exception {
        String queryUrl =
                "https://api.speechflow.io/asr/file/v1/query?taskId=" + taskId + "&resultType=" + RESULT_TYPE;

        while (true) {
            HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("keyId", API_KEY_ID);
            conn.setRequestProperty("keySecret", API_KEY_SECRET);

            String response = readResponse(conn);
            JsonNode json = new ObjectMapper().readTree(response);

            int code = json.get("code").asInt();

            if (code == 11000) {
                return json.get("result").asText();
            }
            if (code == 11001) {
                Thread.sleep(2000);
                continue;
            }

            throw new RuntimeException("Query failed: " + json.get("msg").asText());
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null)
            sb.append(line);

        return sb.toString();
    }
}
