package com.garret.dreammoa.domain.document;
//Elasticsearch에 저장될 문서 구조 정의

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.Document;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "board")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardDocument {

    @Id
    private Long id;
    private String title;
    private String content;
    private String category;
    private Long userId;
    private String userNickname;
    private long createdAt;  // ✅ LocalDateTime → long (epoch time)
    private long updatedAt;  // ✅ LocalDateTime → long (epoch time)
    private int viewCount;
    private List<Double> embedding;

}
