package com.demo.cogbee.service;

import org.json.JSONObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Service
public class FaceVerificationService {


    private static final String API_URL = "https://api-us.faceplusplus.com/facepp/v3/compare";

    private static final String API_KEY = "hLM38cSz9H4GKi_nUrc_pVcWfk1Rkxqr";
    private static final String API_SECRET = "S_D3jfrUOxp9-tyXuYWM-Ou-smFUxzZI";

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean verify(String imagePath1, String imagePath2) {

        File file1 = new File(imagePath1);
        File file2 = new File(imagePath2);

        if (!file1.exists() || !file2.exists()) {
            throw new RuntimeException("Image file not found!");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("api_key", API_KEY);
        body.add("api_secret", API_SECRET);
        body.add("image_file1", new FileSystemResource(file1));
        body.add("image_file2", new FileSystemResource(file2));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(API_URL, HttpMethod.POST, requestEntity, String.class);

        String responseBody = response.getBody();
        System.out.println("Face++ Response: " + responseBody);

        JSONObject json = new JSONObject(responseBody);
        if (!json.has("confidence")) {
            return false;
        }

        double confidence = json.getDouble("confidence");
        System.out.println("Confidence = " + confidence);

        return confidence > 85;
    }
}

