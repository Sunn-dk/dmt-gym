package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("errorCode", ErrorCode.FORBIDDEN.name());
        model.addAttribute("errorMessage", ErrorCode.FORBIDDEN.getDefaultMessage());
        return "error/403";
    }

    @ExceptionHandler(GymException.class)
    public String handleGymException(GymException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getCode().getHttpStatus().value());
        model.addAttribute("errorCode", ex.getCode().name());
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorDetails", ex.getDetails());
        if (ex.getCode() == ErrorCode.FORBIDDEN) {
            return "error/403";
        }
        return "error/business-error";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String label = ControllerParamUtils.labelFor(ex.getName());
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", label + " không đúng định dạng hoặc kiểu giá trị hợp lệ.");
        return "error/business-error";
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParam(MissingServletRequestParameterException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String label = ControllerParamUtils.labelFor(ex.getParameterName());
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", "Thiếu thông tin bắt buộc: " + label + ".");
        return "error/business-error";
    }

    @ExceptionHandler({BindException.class, TypeMismatchException.class})
    public String handleBindException(Exception ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        if (ex instanceof BindException bindEx) {
            model.addAttribute("errorMessage", ControllerParamUtils.describeBindingErrors(bindEx.getBindingResult()));
        } else {
            model.addAttribute("errorMessage", "Dữ liệu gửi lên không đúng định dạng hợp lệ.");
        }
        return "error/business-error";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public String handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
        model.addAttribute("errorMessage", "Phương thức truy cập không được hỗ trợ cho đường dẫn này.");
        return "error/business-error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNotFound(NoResourceFoundException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorCode", ErrorCode.NOT_FOUND.name());
        model.addAttribute("errorMessage", "Không tìm thấy trang hoặc tài nguyên bạn yêu cầu.");
        return "error/business-error";
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDatabaseError(DataAccessException ex, HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        model.addAttribute("errorCode", ErrorCode.STORAGE_ERROR.name());
        model.addAttribute("errorMessage", ErrorCode.STORAGE_ERROR.getDefaultMessage());
        return "error/business-error";
    }
}
