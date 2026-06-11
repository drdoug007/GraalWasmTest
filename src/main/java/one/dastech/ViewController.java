package one.dastech;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ViewController {

    @Value("${app.asciiart.valid-fonts:slant,standard,shadow,doom}")
    private List<String> validFonts;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("fonts", validFonts);
        return "index";
    }
}
