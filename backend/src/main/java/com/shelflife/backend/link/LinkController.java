package com.shelflife.backend.link;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public LinksResponse listActiveLinks() {
        List<LinkResponse> links = linkService.listActiveLinks().stream()
                .map(LinkResponse::from)
                .toList();
        return new LinksResponse(links);
    }

    @GetMapping("/graveyard")
    public LinksResponse listGraveyardLinks() {
        List<LinkResponse> links = linkService.listGraveyardLinks().stream()
                .map(LinkResponse::forGraveyard)
                .toList();
        return new LinksResponse(links);
    }

    @GetMapping("/favorites")
    public LinksResponse listFavoriteLinks() {
        List<LinkResponse> links = linkService.listFavoriteLinks().stream()
                .map(LinkResponse::forFavorites)
                .toList();
        return new LinksResponse(links);
    }

    public record LinksResponse(List<LinkResponse> links) {
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLink(@PathVariable Long id) {
        linkService.deleteLink(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> pinLink(@PathVariable Long id) {
        linkService.pinLink(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unpin")
    public ResponseEntity<Void> unpinLink(@PathVariable Long id) {
        linkService.unpinLink(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_url", e.getMessage()));
    }

    public record ErrorResponse(String error, String message) {
    }
}
