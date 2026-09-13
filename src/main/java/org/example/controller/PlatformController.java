package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.domain.Business;
import org.example.domain.Role;
import org.example.service.PlatformService;
import org.example.session.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlatformController {
    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/platform")
    public String dashboard(HttpSession session, Model model) {
        model.addAttribute("businesses", platformService.businesses());
        model.addAttribute("me", session.getAttribute("currentUser"));
        return "platform/dashboard";
    }

    @GetMapping("/platform/businesses/new")
    public String newBusiness(HttpSession session, Model model) {
        model.addAttribute("business", new Business());
        model.addAttribute("owners", java.util.List.of());
        model.addAttribute("me", session.getAttribute("currentUser"));
        return "platform/business-form";
    }

    @GetMapping("/platform/businesses/{id}")
    public String editBusiness(@PathVariable Long id, HttpSession session, Model model) {
        Business business = platformService.business(id);
        model.addAttribute("business", business);
        model.addAttribute("owners", platformService.owners(business));
        model.addAttribute("me", session.getAttribute("currentUser"));
        return "platform/business-form";
    }

    @PostMapping("/platform/businesses")
    public String saveBusiness(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String slug,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) String paymentInstructions,
            @RequestParam(required = false) MultipartFile logo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Business business = platformService.saveBusiness(id, name, slug, contactPhone, paymentInstructions, logo);
            redirectAttributes.addFlashAttribute("message", "Business saved.");
            return "redirect:/platform/businesses/" + business.getId();
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return id == null ? "redirect:/platform/businesses/new" : "redirect:/platform/businesses/" + id;
        }
    }

    @PostMapping("/platform/businesses/{id}/owners")
    public String saveOwner(
            @PathVariable Long id,
            @RequestParam(required = false) Long ownerId,
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam(required = false) String pin,
            @RequestParam(required = false, defaultValue = "BUSINESS_ADMIN") Role role,
            @RequestParam(required = false, defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes
    ) {
        platformService.saveOwner(platformService.business(id), ownerId, name, phone, pin, role, active);
        redirectAttributes.addFlashAttribute("message", role == Role.STAFF ? "Staff user saved." : "Owner saved.");
        return "redirect:/platform/businesses/" + id;
    }

    @PostMapping("/platform/businesses/{id}/switch")
    public String switchBusiness(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Business business = platformService.business(id);
        platformService.switchBusiness(session, id);
        redirectAttributes.addFlashAttribute("message", "Now managing " + business.getName() + ".");
        return "redirect:/b/" + business.getSlug() + "/admin";
    }
}
