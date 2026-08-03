package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.contact.api.ContactLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class ContactLookupImpl implements ContactLookup {

    private final ContactRepository contacts;

    ContactLookupImpl(ContactRepository contacts) {
        this.contacts = contacts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, UUID contactId) {
        return contactId != null
                && contacts.existsByIdAndTenantIdAndDeletedAtIsNull(contactId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContactReference> findReference(UUID tenantId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return contacts.findByIdAndTenantIdAndDeletedAtIsNull(contactId, tenantId)
                .map(contact -> new ContactReference(contact.getOwnerUserId()));
    }
}
