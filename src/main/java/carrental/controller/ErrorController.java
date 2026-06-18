package carrental.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute("javax.servlet.error.status_code");
        Object message = request.getAttribute("javax.servlet.error.message");
        Object exception = request.getAttribute("javax.servlet.error.exception");

        model.addAttribute("status", status != null ? status.toString() : "500");
        model.addAttribute("message", exception != null
                ? ((Throwable) exception).getMessage()
                : (message != null ? message.toString() : "Something went wrong."));
        return "error";
    }
}
