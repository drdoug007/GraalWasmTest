package one.dastech;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AsciiArtService asciiArtService;

    private static final List<String> VALID_FONTS = List.of("slant", "standard", "shadow");

    public ApiController(AsciiArtService asciiArtService) {
        this.asciiArtService = asciiArtService;
    }

    @GetMapping("asciiart")
    public ResponseEntity<?> getAsciiArt(@RequestParam(name = "text")String text, @RequestParam(name = "font", required = false) String font) {
        if (font == null || font.isEmpty()) {
            font = "standard";
        }

        if (!VALID_FONTS.contains(font)) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "Invalid Font Choice",
                    "message", "Supported options are: " + VALID_FONTS
            ));
        }

        String artResult = asciiArtService.generateAsciiArt(text, font);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(artResult);
    }
}
