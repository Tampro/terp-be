package com.terp.erp.support.contact;

import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Contact messages (reference ContactController: view, delete; created from the portal). */
@Service
@RequiredArgsConstructor
public class ContactService {
    public record ContactDto(String id, String name, String email, String subject, String message,
                             Instant createdDate) {
        static ContactDto of(ContactMessage m) {
            return new ContactDto(String.valueOf(m.getId()), m.getName(), m.getEmail(), m.getSubject(),
                    m.getMessage(), m.getCreatedDate());
        }
    }

    public record ContactForm(
            @Schema(example = "Test Visitor", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(example = "visitor@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(example = "Question about pricing", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "Plain text", example = "Do you offer a yearly plan?",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 5000) String message) {
    }

    private final ContactMessageRepository contacts;

    @Transactional(readOnly = true)
    public Page<ContactDto> list(Long requestedCompanyId, String keyword, Pageable pageable) {
        return contacts.search(Texts.company(requestedCompanyId), Texts.kw(keyword), pageable).map(ContactDto::of);
    }

    @Transactional(readOnly = true)
    public ContactDto get(Long id) {
        return ContactDto.of(require(id));
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** From the public portal: companyId comes from the portal slug, never from the request. */
    @Transactional
    public void submit(Long companyId, ContactForm f) {
        ContactMessage m = new ContactMessage();
        m.setCompanyId(companyId);
        m.setName(f.name().trim());
        m.setEmail(Texts.email(f.email()));
        m.setSubject(f.subject().trim());
        m.setMessage(f.message().trim());
        contacts.save(m);
    }

    private ContactMessage require(Long id) {
        ContactMessage m = contacts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_025));
        TenantGuard.assertSameCompany(m.getCompanyId(), ESupportError.SUP_025);
        return m;
    }
}
