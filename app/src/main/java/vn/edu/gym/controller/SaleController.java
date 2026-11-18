package vn.edu.gym.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.gym.exception.ErrorCode;
import vn.edu.gym.exception.GymException;
import vn.edu.gym.model.Plan;
import vn.edu.gym.model.PlanKind;
import vn.edu.gym.model.ReceiptView;
import vn.edu.gym.model.SaleCommand;
import vn.edu.gym.model.SaleQuote;
import vn.edu.gym.model.SaleType;
import vn.edu.gym.service.MemberService;
import vn.edu.gym.service.PlanService;
import vn.edu.gym.service.SaleService;

import java.util.List;

@Controller
public class SaleController {

    private final SaleService saleService;
    private final MemberService memberService;
    private final PlanService planService;

    public SaleController(SaleService saleService, MemberService memberService, PlanService planService) {
        this.saleService = saleService;
        this.memberService = memberService;
        this.planService = planService;
    }

    @GetMapping("/members/{id}/sales/new")
    public String newSaleForm(
            @PathVariable long id,
            @RequestParam(required = false) Long planId,
            @RequestParam(defaultValue = "PURCHASE") SaleType saleType,
            Model model
    ) {
        populateSaleModel(id, planId, saleType, null, null, model);
        return "sales/new";
    }

    @PostMapping("/sales")
    public String submitSale(
            @ModelAttribute("saleCommand") SaleCommand cmd,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            Long memberId = cmd.getMemberId();
            if (memberId == null || memberId <= 0) {
                throw new GymException(ErrorCode.INVALID_INPUT, ControllerParamUtils.describeBindingErrors(bindingResult));
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("errorCode", ErrorCode.INVALID_INPUT.name());
            model.addAttribute("errorMessage", ControllerParamUtils.describeBindingErrors(bindingResult));
            model.addAttribute("saleRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateSaleModel(
                    memberId,
                    cmd.getPlanId(),
                    cmd.getSaleType() != null ? cmd.getSaleType() : SaleType.PURCHASE,
                    cmd,
                    false,
                    model
            );
            return "sales/new";
        }
        try {
            ReceiptView receipt = saleService.sell(cmd);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ghi nhận thu tiền mặt và cấp hợp đồng thành công (Phiếu " + receipt.receiptCode() + ").");
            return "redirect:/receipts/" + receipt.paymentId();
        } catch (GymException ex) {
            if (ex.getCode() == ErrorCode.FORBIDDEN) {
                throw ex;
            }
            response.setStatus(ex.getCode().getHttpStatus().value());
            model.addAttribute("errorCode", ex.getCode().name());
            model.addAttribute("errorMessage", ex.getMessage());

            Long memberId = cmd.getMemberId();
            if (memberId == null || memberId <= 0) {
                throw ex;
            }
            boolean refreshQuote = ex.getCode() == ErrorCode.PRICE_CHANGED
                    || ex.getCode() == ErrorCode.COVERAGE_CHANGED;
            model.addAttribute("saleRawInputs", ControllerParamUtils.extractSafeInputs(request));
            populateSaleModel(
                    memberId,
                    cmd.getPlanId(),
                    cmd.getSaleType() != null ? cmd.getSaleType() : SaleType.PURCHASE,
                    refreshQuote ? null : cmd,
                    refreshQuote,
                    model
            );
            return "sales/new";
        }
    }

    @GetMapping("/receipts/{id}")
    public String viewReceipt(@PathVariable long id, Model model) {
        ReceiptView receipt = saleService.getReceipt(id);
        model.addAttribute("receipt", receipt);
        return "receipts/view";
    }

    private void populateSaleModel(
            long memberId,
            Long requestedPlanId,
            SaleType saleType,
            SaleCommand preservedCmd,
            Boolean forceRefreshQuote,
            Model model
    ) {
        var detail = memberService.getDetail(memberId);
        List<Plan> activePlans = planService.listActive();
        List<Plan> selectablePlans = saleType == SaleType.RENEWAL
                ? activePlans.stream().filter(p -> p.kind() == PlanKind.GYM).toList()
                : activePlans;

        Long chosenPlanId = requestedPlanId;
        if (chosenPlanId == null && !selectablePlans.isEmpty()) {
            chosenPlanId = selectablePlans.get(0).id();
        }

        model.addAttribute("detail", detail);
        model.addAttribute("saleType", saleType);
        model.addAttribute("saleTypes", SaleType.values());
        model.addAttribute("activePlans", activePlans);
        model.addAttribute("selectablePlans", selectablePlans);
        model.addAttribute("selectedPlanId", chosenPlanId);

        SaleQuote quote = null;
        if (chosenPlanId != null) {
            try {
                String existingKey = (preservedCmd != null && !Boolean.TRUE.equals(forceRefreshQuote))
                        ? preservedCmd.getRequestKey()
                        : null;
                quote = saleService.prepareQuote(memberId, chosenPlanId, saleType, existingKey);
            } catch (GymException ex) {
                if (!model.containsAttribute("errorMessage")) {
                    model.addAttribute("quoteWarningCode", ex.getCode().name());
                    model.addAttribute("quoteWarningMessage", ex.getMessage());
                }
            }
        }
        model.addAttribute("quote", quote);

        SaleCommand formCmd = preservedCmd;
        if (formCmd == null || Boolean.TRUE.equals(forceRefreshQuote)) {
            formCmd = new SaleCommand();
            formCmd.setMemberId(memberId);
            formCmd.setSaleType(saleType);
            if (quote != null) {
                formCmd.setPlanId(quote.planId());
                formCmd.setRequestKey(quote.requestKey());
                formCmd.setQuotedKind(quote.quotedKind());
                formCmd.setQuotedDurationDays(quote.quotedDurationDays());
                formCmd.setQuotedSessionsTotal(quote.quotedSessionsTotal());
                formCmd.setQuotedPriceVnd(quote.quotedPriceVnd());
                formCmd.setExpectedStart(quote.expectedStart());
                formCmd.setExpectedEnd(quote.expectedEnd());
                formCmd.setAmountReceivedVnd(quote.quotedPriceVnd());
            } else {
                formCmd.setPlanId(chosenPlanId);
            }
        }
        model.addAttribute("saleCommand", formCmd);
    }
}
