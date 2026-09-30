package com.jinsu.newspipeline.api;

import com.jinsu.newspipeline.domain.Category;
import com.jinsu.newspipeline.repository.ArticleSummaryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 요약·분류가 끝난 기사 조회 API (v1).
 *  - GET /api/articles           : 목록 (category 필터, 페이지네이션)
 *  - GET /api/articles/{id}      : 단건
 * category 파라미터는 한국어 라벨("AI","개발"...) 또는 enum 이름 모두 허용.
 */
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleSummaryRepository repository;

    public ArticleController(ArticleSummaryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ArticleResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Category filter = (category == null || category.isBlank()) ? null : Category.fromLabel(category);
        return repository.findViews(filter, PageRequest.of(page, size))
                .stream()
                .map(ArticleResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ArticleResponse detail(@PathVariable Long id) {
        return repository.findViewById(id)
                .map(ArticleResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "summary not found: " + id));
    }
}
