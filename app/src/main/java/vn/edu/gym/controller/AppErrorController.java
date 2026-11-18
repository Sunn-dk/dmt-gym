package vn.edu.gym.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.edu.gym.exception.ErrorCode;

/**
 * Replaces Spring Boot's default Whitelabel Error Page with themed Vietnamese error responses
 * without leaking stack traces or technical exception classes.
 */
@Controller
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        if (statusAttr != null) {
            try {
                statusCode = Integer.parseInt(statusAttr.toString());
            } catch (NumberFormatException ignored) {
                statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
        }
        response.setStatus(statusCode);

        if (statusCode == HttpStatus.FORBIDDEN.value()) {
            model.addAttribute("errorCode", ErrorCode.FORBIDDEN.name());
            model.addAttribute("errorMessage", ErrorCode.FORBIDDEN.getDefaultMessage());
            return "error/403";
        }
        if (statusCode == HttpStatus.BAD_REQUEST.value()) {
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", "Tham số hoặc dữ liệu gửi lên không đúng định dạng hợp lệ.");
        } else if (statusCode == HttpStatus.NOT_FOUND.value()) {
            model.addAttribute("errorCode", ErrorCode.NOT_FOUND.name());
            model.addAttribute("errorMessage", "Không tìm thấy trang hoặc tài nguyên bạn yêu cầu.");
        } else if (statusCode == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", "Phương thức truy cập không được hỗ trợ cho đường dẫn này.");
        } else {
            model.addAttribute("errorCode", ErrorCode.STORAGE_ERROR.name());
            model.addAttribute("errorMessage", "Hệ thống gặp sự cố khi xử lý yêu cầu. Vui lòng thử lại.");
        }
        return "error/business-error";
    }
}
