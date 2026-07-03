package com.shelflife.backend.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(@RequestBody CreateLinkRequest request) {
        Link link = linkService.createLink(request.url());
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.from(link));
    }

    @GetMapping
    public ActiveLinksResponse listActiveLinks() {
        List<LinkResponse> links = linkService.listActiveLinks().stream()
                .map(LinkResponse::from)
                .toList();
        return new ActiveLinksResponse(links);
    }

    public record ActiveLinksResponse(List<LinkResponse> links) {
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_url", e.getMessage()));
    }

    public record ErrorResponse(String error, String message) {
    }
}
