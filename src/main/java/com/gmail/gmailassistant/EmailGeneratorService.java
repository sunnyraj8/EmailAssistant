package com.gmail.gmailassistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apikey;
//    @Value(("${"))
//    private String urlex ;
    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apikey = geminiApiKey;
    }

    public String generateEmail(EmailRequest emailRequest) {
        //Build prompt
        String  prompt=buildPrompt(emailRequest);
        //prepare raw JSON Body
        String requestBody= String.format("""
                {
                "contents": [
                  {
                    "parts": [
                      {
                        "text": "%s"
                      }
                    ]
                  }
                ]
              }""",prompt);
        //send request
        String response=webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/gemini-2.5-flash:generateContent")
                        //we can send the api key through query param itself , but recommende is header.
                        //.queryParam("key", apikey)
                        .build())
                .header("x-goog-api-key",apikey)
                .header("Content-Type","application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        //Extract Response
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            return root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (JsonProcessingException e){
            throw new RuntimeException();
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt=new StringBuilder();
        prompt.append("Generate a proffessional email reply for the following email:");
        if (emailRequest.getTone()!=null && !emailRequest.getTone().isBlank()){
            prompt.append("Use a").append(emailRequest.getTone()).append("tone.");
        }
        prompt.append("Original Email: \n").append(emailRequest.getEmailContent());
        return prompt.toString();
    }
}
