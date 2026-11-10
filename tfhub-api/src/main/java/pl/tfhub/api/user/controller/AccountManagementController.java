package pl.tfhub.api.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.tfhub.api.user.dto.request.ChangeAccountStatusRequest;
import pl.tfhub.api.user.dto.request.CreateAccountRequest;
import pl.tfhub.api.user.dto.request.ResetAccountPasswordRequest;
import pl.tfhub.api.user.dto.request.UpdateAccountRequest;
import pl.tfhub.api.user.dto.response.AccountResponse;
import pl.tfhub.api.user.enums.UserRole;
import pl.tfhub.api.user.service.AuthenticatedUser;
import pl.tfhub.api.user.service.AccountManagementService;

@RestController
@RequestMapping("/api/accounts")
public class AccountManagementController {

	private final AccountManagementService accountManagementService;

	public AccountManagementController(AccountManagementService accountManagementService) {
		this.accountManagementService = accountManagementService;
	}

	@GetMapping
	public List<AccountResponse> listAccounts(
			@RequestParam(required = false) String username,
			@RequestParam(required = false) UserRole role,
			@RequestParam(required = false) Boolean active) {
		return accountManagementService.listAccounts(username, role, active);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
		return accountManagementService.createAccount(request);
	}

	@PutMapping("/{id}")
	public AccountResponse updateAccount(
			@PathVariable long id,
			@Valid @RequestBody UpdateAccountRequest request) {
		return accountManagementService.updateAccount(id, request);
	}

	@PatchMapping("/{id}/status")
	public AccountResponse changeStatus(
			@PathVariable long id,
			@Valid @RequestBody ChangeAccountStatusRequest request) {
		return accountManagementService.changeStatus(id, request.active());
	}

	@PostMapping("/{id}/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(
			@PathVariable long id,
			@Valid @RequestBody ResetAccountPasswordRequest request,
			Authentication authentication,
			HttpServletRequest servletRequest) {
		accountManagementService.resetPassword(id, request);
		if (authentication.getPrincipal() instanceof AuthenticatedUser user && user.id() == id) {
			HttpSession session = servletRequest.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
		}
	}
}
