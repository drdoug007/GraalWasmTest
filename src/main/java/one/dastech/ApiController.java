package one.dastech;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AsciiArtService asciiArtService;
    private final MarkdownService markdownService;

    @Value("${app.asciiart.valid-fonts}")
    private List<String> validFonts;

    public ApiController(AsciiArtService asciiArtService, MarkdownService markdownService) {
        this.asciiArtService = asciiArtService;
        this.markdownService = markdownService;
    }

    @GetMapping(path="asciiart")
    public String getAsciiArt(
            @RequestParam(name = "text") String text,
            @RequestParam(name = "font", required = false) String font) {

        if (text == null || text.trim().isEmpty()) {
            return ""; // Keeps the display block completely clean if text is deleted
        }

        if (font == null || font.isEmpty()) {
            font = "standard";
        }

        if (!validFonts.contains(font)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Font is invalid.");
        }

        return asciiArtService.generateAsciiArt(text, font);
    }

    @PostMapping(value = "/markdown", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderMarkdown(@RequestBody String markdownBody) {
        if (markdownBody == null || markdownBody.trim().isEmpty()) {
            return ResponseEntity.ok("<!-- Empty Markdown Content Provided -->");
        }

        String htmlResult = markdownService.toHtml(markdownBody);
        return ResponseEntity.ok(htmlResult);
    }
}
