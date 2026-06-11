package one.dastech;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AsciiArtService asciiArtService;

    @Value("${app.asciiart.valid-fonts}")
    private List<String> validFonts;

    public ApiController(AsciiArtService asciiArtService) {
        this.asciiArtService = asciiArtService;
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
}
