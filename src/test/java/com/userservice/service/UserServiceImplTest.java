package com.userservice.service;

import com.userservice.domain.Address;
import com.userservice.domain.DuplicateEmailException;
import com.userservice.domain.DuplicatePhoneException;
import com.userservice.domain.InvalidParentRelationshipException;
import com.userservice.domain.InvalidPhoneException;
import com.userservice.domain.ParentNotFoundException;
import com.userservice.domain.User;
import com.userservice.domain.UserNotFoundException;
import com.userservice.dto.AddressDto;
import com.userservice.dto.UserCreateRequest;
import com.userservice.dto.UserResponse;
import com.userservice.dto.UserUpdateRequest;
import com.userservice.repository.AddressRepository;
import com.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private UserServiceImpl service;

    @BeforeEach
    void stubCollaborators() {
        when(userRepository.existsByEmailAndIsDeletedFalse(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIsDeletedFalse(nullable(String.class))).thenReturn(false);
        when(userRepository.existsByEmailAndIsDeletedFalseAndIdNot(anyString(), anyLong())).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIsDeletedFalseAndIdNot(nullable(String.class), anyLong())).thenReturn(false);
        when(userRepository.findByIdAndIsDeletedFalse(anyLong())).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(userRepository.findActiveChildren(anyLong())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(42L);
            }
            return user;
        });
        when(addressRepository.findMatchingNormalized(
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class)
        )).thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            if (address.getId() == null) {
                address.setId(77L);
            }
            return address;
        });
    }

    @Test
    void create_requiredFields_normalizesEmailAndReturnsPersistedUser() {
        UserResponse response = service.create(createRequest("Ada@Example.com", null, null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().isDeleted()).isFalse();
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("ada@example.com");
    }

    @Test
    void create_requiredFields_keepsAbsentOptionalsOmittedAndChildrenEmpty() {
        UserResponse response = service.create(createRequest("ada@example.com", null, null, null, null));

        assertThat(response.phoneNumber()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.fatherId()).isNull();
        assertThat(response.motherId()).isNull();
        assertThat(response.childrenIds()).isEmpty();
    }

    @Test
    void create_formattedPhone_persistsCanonicalE164() {
        UserResponse response = service.create(createRequest("ada@example.com", "+1 (555) 123-4567", null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("+15551234567");
        assertThat(response.phoneNumber()).isEqualTo("+15551234567");
    }

    @Test
    void create_nonNormalizablePhone_throwsValidationFailure() {
        String[] invalidPhones = {"call-me", "15551234567", "+05551234567", "+123", "+1234567890123456"};
        for (String phone : invalidPhones) {
            try {
                service.create(createRequest("ada@example.com", phone, null, null, null));
                fail("expected InvalidPhoneException for " + phone);
            } catch (InvalidPhoneException expected) {
                verify(userRepository, never()).save(any());
            }
        }
    }

    @Test
    void create_duplicateNormalizedEmail_throwsDuplicateEmail() {
        when(userRepository.existsByEmailAndIsDeletedFalse("ada@example.com")).thenReturn(true);

        try {
            service.create(createRequest("Ada@Example.com", null, null, null, null));
            fail("expected DuplicateEmailException");
        } catch (DuplicateEmailException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void create_duplicateNormalizedPhone_throwsDuplicatePhone() {
        when(userRepository.existsByPhoneNumberAndIsDeletedFalse("+15550000001")).thenReturn(true);

        try {
            service.create(createRequest("ada@example.com", "+1 (555) 000-0001", null, null, null));
            fail("expected DuplicatePhoneException");
        } catch (DuplicatePhoneException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void create_identityUsedOnlyByDeletedUser_isAllowed() {
        when(userRepository.existsByEmailAndIsDeletedFalse(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIsDeletedFalse(anyString())).thenReturn(false);

        UserResponse response = service.create(createRequest("Ada@Example.com", "+1 555 000 0001", null, null, null));

        verify(userRepository).save(any(User.class));
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+15550000001");
    }

    @Test
    void create_addressWhitespaceAndCaseVariant_reusesExistingAddress() {
        Address existing = address(5L, "united states", "new york", "main street", "10", null, "10001");
        when(addressRepository.findMatchingNormalized(
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class)
        )).thenReturn(Optional.of(existing));

        UserResponse response = service.create(createRequest(
                "ada@example.com",
                null,
                new AddressDto(null, "  UNITED    STATES ", " New   York ", " Main    Street ", " 10 ", null, " 10001 "),
                null,
                null
        ));

        verify(addressRepository, never()).save(any());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAddress().getId()).isEqualTo(5L);
        assertThat(response.address().id()).isEqualTo(5L);
    }

    @Test
    void create_anyAddressDetailDiffers_insertsNewAddress() {
        when(addressRepository.findMatchingNormalized(
                nullable(String.class), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), nullable(String.class)
        )).thenReturn(Optional.empty());
        Address created = address(99L, "Country", "City", "Street", "1", null, "ZZ-2");
        when(addressRepository.save(any(Address.class))).thenReturn(created);

        service.create(createRequest(
                "ada@example.com",
                null,
                new AddressDto(null, "Country", "City", "Street", "1", null, "ZZ-2"),
                null,
                null
        ));

        ArgumentCaptor<Address> savedAddress = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(savedAddress.capture());
        assertThat(savedAddress.getValue().getPostalCode()).isEqualTo("ZZ-2");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getAddress().getId()).isEqualTo(99L);
    }

    @Test
    void update_newAddress_relinksOnlyTargetAndNeverMutatesOldAddress() {
        Address oldAddress = address(1L, "Country", "City", "Street", "1", "2", "AAA");
        User target = user(8L, "ada@example.com");
        target.setAddress(oldAddress);
        when(userRepository.findByIdAndIsDeletedFalse(8L)).thenReturn(Optional.of(target));
        Address created = address(2L, "Country", "City", "Street", "1", "2", "BBB");
        when(addressRepository.save(any(Address.class))).thenReturn(created);

        service.update(8L, updateRequest(
                "ada@example.com",
                null,
                new AddressDto(null, "Country", "City", "Street", "1", "2", "BBB"),
                null,
                null
        ));

        assertThat(oldAddress.getPostalCode()).isEqualTo("AAA");
        verify(addressRepository, never()).delete(any());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(8L);
        assertThat(saved.getValue().getAddress().getId()).isEqualTo(2L);
        assertThat(saved.getValue().getAddress()).isNotSameAs(oldAddress);
    }

    @Test
    void update_nullAddress_unlinksTarget() {
        User target = user(8L, "ada@example.com");
        target.setAddress(address(1L, "Country", "City", "Street", "1", null, "AAA"));
        when(userRepository.findByIdAndIsDeletedFalse(8L)).thenReturn(Optional.of(target));

        UserResponse response = service.update(8L, updateRequest("ada@example.com", null, null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAddress()).isNull();
        verify(addressRepository, never()).save(any());
        verify(addressRepository, never()).delete(any());
        assertThat(response.address()).isNull();
    }

    @Test
    void create_activeParents_linksFatherAndMother() {
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(user(2L, "father@example.com")));
        when(userRepository.findByIdAndIsDeletedFalse(3L)).thenReturn(Optional.of(user(3L, "mother@example.com")));

        UserResponse response = service.create(createRequest("ada@example.com", null, null, 2L, 3L));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFather().getId()).isEqualTo(2L);
        assertThat(saved.getValue().getMother().getId()).isEqualTo(3L);
        assertThat(response.fatherId()).isEqualTo(2L);
        assertThat(response.motherId()).isEqualTo(3L);
    }

    @Test
    void create_missingOrDeletedParent_throwsParentNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());

        try {
            service.create(createRequest("ada@example.com", null, null, 99L, null));
            fail("expected ParentNotFoundException");
        } catch (ParentNotFoundException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void create_sameFatherAndMother_throwsInvalidParentRelationship() {
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(user(2L, "parent@example.com")));

        try {
            service.create(createRequest("ada@example.com", null, null, 2L, 2L));
            fail("expected InvalidParentRelationshipException");
        } catch (InvalidParentRelationshipException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_selfAsFatherOrMother_throwsInvalidParentRelationship() {
        when(userRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(user(5L, "ada@example.com")));

        try {
            service.update(5L, updateRequest("ada@example.com", null, null, 5L, null));
            fail("expected InvalidParentRelationshipException");
        } catch (InvalidParentRelationshipException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_nullParents_unlinksBothParents() {
        User target = user(5L, "ada@example.com");
        target.setFather(user(2L, "father@example.com"));
        target.setMother(user(3L, "mother@example.com"));
        when(userRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(target));

        UserResponse response = service.update(5L, updateRequest("ada@example.com", null, null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFather()).isNull();
        assertThat(saved.getValue().getMother()).isNull();
        assertThat(response.fatherId()).isNull();
        assertThat(response.motherId()).isNull();
    }

    @Test
    void update_childAsParent_rejectsDirectCycle() {
        User target = user(10L, "target@example.com");
        User child = user(11L, "child@example.com");
        child.setFather(target);
        when(userRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(11L)).thenReturn(Optional.of(child));

        try {
            service.update(10L, updateRequest("target@example.com", null, null, 11L, null));
            fail("expected InvalidParentRelationshipException");
        } catch (InvalidParentRelationshipException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_descendantAsParent_rejectsIndirectCycle() {
        User target = user(10L, "target@example.com");
        User middle = user(20L, "middle@example.com");
        middle.setFather(target);
        User descendant = user(30L, "descendant@example.com");
        descendant.setFather(middle);
        when(userRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(20L)).thenReturn(Optional.of(middle));
        when(userRepository.findByIdAndIsDeletedFalse(30L)).thenReturn(Optional.of(descendant));

        try {
            service.update(10L, updateRequest("target@example.com", null, null, 30L, null));
            fail("expected InvalidParentRelationshipException");
        } catch (InvalidParentRelationshipException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_unrelatedActiveParent_allowsAssignment() {
        User target = user(10L, "target@example.com");
        User unrelatedParent = user(40L, "other@example.com");
        unrelatedParent.setFather(user(50L, "elder@example.com"));
        when(userRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(40L)).thenReturn(Optional.of(unrelatedParent));
        when(userRepository.findByIdAndIsDeletedFalse(50L)).thenReturn(Optional.of(unrelatedParent.getFather()));

        UserResponse response = service.update(10L, updateRequest("target@example.com", null, null, 40L, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFather().getId()).isEqualTo(40L);
        assertThat(response.fatherId()).isEqualTo(40L);
    }

    @Test
    void response_activeParents_includesNumericParentIds() {
        User target = user(1L, "ada@example.com");
        target.setFather(user(2L, "father@example.com"));
        target.setMother(user(3L, "mother@example.com"));
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(target));

        UserResponse response = service.getById(1L);

        assertThat(response.fatherId()).isEqualTo(2L);
        assertThat(response.motherId()).isEqualTo(3L);
    }

    @Test
    void response_deletedParent_omitsCorrespondingParentId() {
        User father = user(2L, "father@example.com");
        father.setDeleted(true);
        User mother = user(3L, "mother@example.com");
        User target = user(1L, "ada@example.com");
        target.setFather(father);
        target.setMother(mother);
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(target));

        UserResponse response = service.getById(1L);

        assertThat(response.fatherId()).isNull();
        assertThat(response.motherId()).isEqualTo(3L);
    }

    @Test
    void response_children_containsOnlyActiveDirectChildIds() {
        User target = user(1L, "ada@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(target));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of(user(8L, "c1@example.com"), user(9L, "c2@example.com")));

        UserResponse response = service.getById(1L);

        assertThat(response.childrenIds()).containsExactlyInAnyOrder(8L, 9L);
    }

    @Test
    void response_nullOptionalsRemainNullForJsonOmission() {
        User target = user(1L, "ada@example.com");
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(target));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of());

        UserResponse response = service.getById(1L);

        assertThat(response.phoneNumber()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.fatherId()).isNull();
        assertThat(response.motherId()).isNull();
        assertThat(response.childrenIds()).isNotNull().isEmpty();
    }

    @Test
    void getById_activeUser_returnsMappedResponse() {
        User target = user(4L, "ada@example.com");
        target.setFirstName("Ada");
        target.setLastName("Lovelace");
        when(userRepository.findByIdAndIsDeletedFalse(4L)).thenReturn(Optional.of(target));
        when(userRepository.findActiveChildren(4L)).thenReturn(List.of());

        UserResponse response = service.getById(4L);

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
    }

    @Test
    void getById_missingOrDeletedUser_throwsUserNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(4L)).thenReturn(Optional.empty());

        try {
            service.getById(4L);
            fail("expected UserNotFoundException");
        } catch (UserNotFoundException expected) {
            verify(userRepository).findByIdAndIsDeletedFalse(4L);
        }
    }

    @Test
    void getByEmail_mixedCaseInput_usesLowercaseExactLookup() {
        User target = user(4L, "ada@example.com");
        when(userRepository.findByEmailAndIsDeletedFalse("ada@example.com")).thenReturn(Optional.of(target));
        when(userRepository.findActiveChildren(4L)).thenReturn(List.of());

        UserResponse response = service.getByEmail("Ada@Example.com");

        verify(userRepository).findByEmailAndIsDeletedFalse("ada@example.com");
        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.email()).isEqualTo("ada@example.com");
    }

    @Test
    void getByEmail_missingOrDeletedUser_throwsUserNotFound() {
        when(userRepository.findByEmailAndIsDeletedFalse("ada@example.com")).thenReturn(Optional.empty());

        try {
            service.getByEmail("Ada@Example.com");
            fail("expected UserNotFoundException");
        } catch (UserNotFoundException expected) {
            verify(userRepository).findByEmailAndIsDeletedFalse("ada@example.com");
        }
    }

    @Test
    void update_completeRequest_replacesRequiredFieldsAndNormalizesIdentity() {
        User target = user(7L, "old@example.com");
        target.setFirstName("Old");
        target.setPhoneNumber("+1999");
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(target));

        UserResponse response = service.update(7L, updateRequest("Ada@Example.com", "+1 (555) 222-3333", null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(saved.getValue().getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("+15552223333");
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+15552223333");
    }

    @Test
    void update_omittedOptionals_resetsPhoneAddressAndParentsToNull() {
        User target = user(7L, "ada@example.com");
        target.setPhoneNumber("+15550000000");
        target.setAddress(address(1L, "Country", "City", "Street", "1", null, "AAA"));
        target.setFather(user(2L, "father@example.com"));
        target.setMother(user(3L, "mother@example.com"));
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(target));

        UserResponse response = service.update(7L, updateRequest("ada@example.com", null, null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPhoneNumber()).isNull();
        assertThat(saved.getValue().getAddress()).isNull();
        assertThat(saved.getValue().getFather()).isNull();
        assertThat(saved.getValue().getMother()).isNull();
        assertThat(response.phoneNumber()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.fatherId()).isNull();
        assertThat(response.motherId()).isNull();
    }

    @Test
    void update_sameNormalizedIdentityOnCurrentUser_isAllowed() {
        User target = user(7L, "ada@example.com");
        target.setPhoneNumber("+15550000001");
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(target));
        when(userRepository.existsByEmailAndIsDeletedFalse(anyString())).thenReturn(true);
        when(userRepository.existsByPhoneNumberAndIsDeletedFalse(anyString())).thenReturn(true);
        when(userRepository.existsByEmailAndIsDeletedFalseAndIdNot("ada@example.com", 7L)).thenReturn(false);
        when(userRepository.existsByPhoneNumberAndIsDeletedFalseAndIdNot("+15550000001", 7L)).thenReturn(false);

        UserResponse response = service.update(7L, updateRequest("Ada@Example.com", "+1 (555) 000-0001", null, null, null));

        verify(userRepository).save(any(User.class));
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.phoneNumber()).isEqualTo("+15550000001");
    }

    @Test
    void update_emailUsedByAnotherActiveUser_throwsDuplicateEmail() {
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(user(7L, "old@example.com")));
        when(userRepository.existsByEmailAndIsDeletedFalseAndIdNot("ada@example.com", 7L)).thenReturn(true);

        try {
            service.update(7L, updateRequest("Ada@Example.com", null, null, null, null));
            fail("expected DuplicateEmailException");
        } catch (DuplicateEmailException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_phoneUsedByAnotherActiveUser_throwsDuplicatePhone() {
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(user(7L, "ada@example.com")));
        when(userRepository.existsByPhoneNumberAndIsDeletedFalseAndIdNot("+15550000001", 7L)).thenReturn(true);

        try {
            service.update(7L, updateRequest("ada@example.com", "+1 (555) 000-0001", null, null, null));
            fail("expected DuplicatePhoneException");
        } catch (DuplicatePhoneException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_missingOrDeletedTarget_throwsUserNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.empty());

        try {
            service.update(7L, updateRequest("ada@example.com", null, null, null, null));
            fail("expected UserNotFoundException");
        } catch (UserNotFoundException expected) {
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void softDelete_activeUser_setsDeletedAndSavesSameEntity() {
        User target = user(6L, "ada@example.com");
        target.setFather(user(2L, "father@example.com"));
        when(userRepository.findByIdAndIsDeletedFalse(6L)).thenReturn(Optional.of(target));

        service.softDelete(6L);

        assertThat(target.isDeleted()).isTrue();
        assertThat(target.getFather().getId()).isEqualTo(2L);
        verify(userRepository).save(target);
    }

    @Test
    void softDelete_doesNotDeleteEntityOrRewriteRelatives() {
        User father = user(2L, "father@example.com");
        User target = user(6L, "ada@example.com");
        target.setFather(father);
        when(userRepository.findByIdAndIsDeletedFalse(6L)).thenReturn(Optional.of(target));

        service.softDelete(6L);

        verify(userRepository).save(target);
        verify(userRepository, never()).delete(any());
        verify(userRepository, never()).deleteById(anyLong());
        verify(userRepository, never()).deleteAll();
        assertThat(target.getFather()).isSameAs(father);
        assertThat(father.isDeleted()).isFalse();
    }

    @Test
    void softDelete_missingOrAlreadyDeleted_throwsUserNotFound() {
        when(userRepository.findByIdAndIsDeletedFalse(6L)).thenReturn(Optional.empty());

        try {
            service.softDelete(6L);
            fail("expected UserNotFoundException");
        } catch (UserNotFoundException expected) {
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).delete(any());
        }
    }

    @Test
    void getDirectFamily_activeAnchor_returnsAnchorActiveParentsAndActiveChildren() {
        User father = user(2L, "father@example.com");
        User mother = user(3L, "mother@example.com");
        User anchor = user(1L, "ada@example.com");
        anchor.setFather(father);
        anchor.setMother(mother);
        User child = user(4L, "child@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of(child));

        List<UserResponse> family = service.getDirectFamily(1L);

        assertThat(family).extracting(UserResponse::id).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void getDirectFamily_activeAnchorWithoutRelatives_returnsAnchorOnly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ada@example.com")));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of());

        List<UserResponse> family = service.getDirectFamily(1L);

        assertThat(family).extracting(UserResponse::id).containsExactly(1L);
    }

    @Test
    void getDirectFamily_deletedAnchor_returnsOnlyActiveDirectRelatives() {
        User father = user(2L, "father@example.com");
        User anchor = user(1L, "ada@example.com");
        anchor.setDeleted(true);
        anchor.setFather(father);
        User child = user(4L, "child@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of(child));

        List<UserResponse> family = service.getDirectFamily(1L);

        assertThat(family).extracting(UserResponse::id).containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    void getDirectFamily_deletedAnchorWithoutActiveRelatives_returnsEmptyList() {
        User anchor = user(1L, "ada@example.com");
        anchor.setDeleted(true);
        User deletedFather = user(2L, "father@example.com");
        deletedFather.setDeleted(true);
        anchor.setFather(deletedFather);
        when(userRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of());

        List<UserResponse> family = service.getDirectFamily(1L);

        assertThat(family).isEmpty();
    }

    @Test
    void getDirectFamily_neverExistingAnchor_throwsUserNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        try {
            service.getDirectFamily(404L);
            fail("expected UserNotFoundException");
        } catch (UserNotFoundException expected) {
            verify(userRepository).findById(404L);
        }
    }

    @Test
    void getDirectFamily_excludesDeletedRelativesAndRecursiveAncestors() {
        User grandfather = user(10L, "grand@example.com");
        User father = user(2L, "father@example.com");
        father.setFather(grandfather);
        User mother = user(3L, "mother@example.com");
        mother.setDeleted(true);
        User anchor = user(1L, "ada@example.com");
        anchor.setFather(father);
        anchor.setMother(mother);
        User child = user(4L, "child@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(userRepository.findActiveChildren(1L)).thenReturn(List.of(child));

        List<UserResponse> family = service.getDirectFamily(1L);

        assertThat(family).extracting(UserResponse::id).containsExactlyInAnyOrder(1L, 2L, 4L);
    }

    private static UserCreateRequest createRequest(String email, String phone, AddressDto address, Long fatherId, Long motherId) {
        return new UserCreateRequest("Ada", "Lovelace", LocalDate.of(1990, 1, 1), email, phone, address, fatherId, motherId);
    }

    private static UserUpdateRequest updateRequest(String email, String phone, AddressDto address, Long fatherId, Long motherId) {
        return new UserUpdateRequest("Ada", "Lovelace", LocalDate.of(1990, 1, 1), email, phone, address, fatherId, motherId);
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setFirstName("First" + id);
        user.setLastName("Last" + id);
        user.setBirthDate(LocalDate.of(1980, 1, 1));
        user.setEmail(email);
        user.setDeleted(false);
        return user;
    }

    private static Address address(Long id, String country, String city, String street, String building, String apartment, String postalCode) {
        Address address = new Address();
        address.setId(id);
        address.setCountry(country);
        address.setCity(city);
        address.setStreet(street);
        address.setBuilding(building);
        address.setApartment(apartment);
        address.setPostalCode(postalCode);
        return address;
    }
}
