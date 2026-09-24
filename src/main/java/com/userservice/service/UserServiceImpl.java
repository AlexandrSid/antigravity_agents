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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final String PHONE_PATTERN = "\\+[1-9]\\d{6,14}";

    private static final String ADDRESS_KEY_SEPARATOR = "\u001f";

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final TransactionTemplate newAddressTransaction;

    public UserServiceImpl(
            UserRepository userRepository,
            AddressRepository addressRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        if (transactionManager == null) {
            this.newAddressTransaction = null;
        } else {
            this.newAddressTransaction = new TransactionTemplate(transactionManager);
            this.newAddressTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    @Override
    public UserResponse create(UserCreateRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phoneNumber());
        ensureCreateIdentityAvailable(email, phone);
        if (sameParent(request.fatherId(), request.motherId())) {
            throw new InvalidParentRelationshipException("Father and mother must be different users");
        }
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setBirthDate(request.birthDate());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setDeleted(false);
        user.setAddress(resolveAddress(request.address()));
        user.setFather(resolveParent(request.fatherId()));
        user.setMother(resolveParent(request.motherId()));
        return toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getById(Long id) {
        return toResponse(requireActiveUser(id));
    }

    @Override
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = requireActiveUser(id);
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phoneNumber());
        ensureUpdateIdentityAvailable(id, email, phone);
        if (sameParent(request.fatherId(), request.motherId())) {
            throw new InvalidParentRelationshipException("Father and mother must be different users");
        }
        User father = resolveParent(request.fatherId());
        User mother = resolveParent(request.motherId());
        rejectSelfOrCycle(id, father);
        rejectSelfOrCycle(id, mother);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setBirthDate(request.birthDate());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setAddress(resolveAddress(request.address()));
        user.setFather(father);
        user.setMother(mother);
        return toResponse(userRepository.save(user));
    }

    @Override
    public void softDelete(Long id) {
        User user = requireActiveUser(id);
        user.setDeleted(true);
        userRepository.save(user);
    }

    @Override
    public UserResponse getByEmail(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmailAndIsDeletedFalse(normalized)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return toResponse(user);
    }

    @Override
    public List<UserResponse> getDirectFamily(Long id) {
        User anchor = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Map<Long, User> members = new LinkedHashMap<>();
        addActive(members, anchor);
        addActive(members, anchor.getFather());
        addActive(members, anchor.getMother());
        for (User child : userRepository.findActiveChildren(anchor.getId())) {
            addActive(members, child);
        }
        return members.values().stream().map(this::toResponse).toList();
    }

    private void ensureCreateIdentityAvailable(String email, String phone) {
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new DuplicateEmailException("Email already belongs to an active user");
        }
        if (phone != null && userRepository.existsByPhoneNumberAndIsDeletedFalse(phone)) {
            throw new DuplicatePhoneException("Phone number already belongs to an active user");
        }
    }

    private void ensureUpdateIdentityAvailable(Long id, String email, String phone) {
        if (userRepository.existsByEmailAndIsDeletedFalseAndIdNot(email, id)) {
            throw new DuplicateEmailException("Email already belongs to an active user");
        }
        if (phone != null && userRepository.existsByPhoneNumberAndIsDeletedFalseAndIdNot(phone, id)) {
            throw new DuplicatePhoneException("Phone number already belongs to an active user");
        }
    }

    private User requireActiveUser(Long id) {
        return userRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private User resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return userRepository.findByIdAndIsDeletedFalse(parentId)
                .orElseThrow(() -> new ParentNotFoundException("Parent user not found"));
    }

    private void rejectSelfOrCycle(Long targetId, User parent) {
        if (parent == null) {
            return;
        }
        Set<Long> visited = new java.util.HashSet<>();
        User current = parent;
        while (current != null) {
            if (targetId.equals(current.getId())) {
                throw new InvalidParentRelationshipException("Parent assignment creates an ancestry cycle");
            }
            if (!visited.add(current.getId())) {
                return;
            }
            User next = current.getFather() != null ? current.getFather() : current.getMother();
            if (current.getFather() != null && reaches(targetId, current.getMother(), visited)) {
                throw new InvalidParentRelationshipException("Parent assignment creates an ancestry cycle");
            }
            current = next;
        }
    }

    private boolean reaches(Long targetId, User parent, Set<Long> visited) {
        User current = parent;
        while (current != null) {
            if (targetId.equals(current.getId())) {
                return true;
            }
            if (!visited.add(current.getId())) {
                return false;
            }
            if (reaches(targetId, current.getMother(), visited)) {
                return true;
            }
            current = current.getFather();
        }
        return false;
    }

    private Address resolveAddress(AddressDto address) {
        if (address == null) {
            return null;
        }
        String country = normalizeText(address.country());
        String city = normalizeText(address.city());
        String street = normalizeText(address.street());
        String building = normalizeText(address.building());
        String apartment = normalizeText(address.apartment());
        String postalCode = normalizeText(address.postalCode());
        return addressRepository.findMatchingNormalized(country, city, street, building, apartment, postalCode)
                .orElseGet(() -> insertAddress(country, city, street, building, apartment, postalCode));
    }

    private Address insertAddress(
            String country,
            String city,
            String street,
            String building,
            String apartment,
            String postalCode
    ) {
        Address created = new Address();
        created.setCountry(country);
        created.setCity(city);
        created.setStreet(street);
        created.setBuilding(building);
        created.setApartment(apartment);
        created.setPostalCode(postalCode);
        try {
            if (newAddressTransaction == null) {
                return saveAddress(created);
            }
            return newAddressTransaction.execute(status -> saveAddress(created));
        } catch (DataIntegrityViolationException exception) {
            return addressRepository.findByNormalizedText(addressKey(country, city, street, building, apartment, postalCode))
                    .orElseThrow(() -> exception);
        }
    }

    private Address saveAddress(Address created) {
        Address saved = addressRepository.save(created);
        addressRepository.flush();
        return saved;
    }

    private static String addressKey(
            String country,
            String city,
            String street,
            String building,
            String apartment,
            String postalCode
    ) {
        return String.join(ADDRESS_KEY_SEPARATOR,
                country.toLowerCase(Locale.ROOT),
                city.toLowerCase(Locale.ROOT),
                street.toLowerCase(Locale.ROOT),
                building.toLowerCase(Locale.ROOT),
                apartment == null ? "" : apartment.toLowerCase(Locale.ROOT),
                postalCode == null ? "" : postalCode.toLowerCase(Locale.ROOT));
    }

    private UserResponse toResponse(User user) {
        List<Long> childrenIds = userRepository.findActiveChildren(user.getId()).stream()
                .map(User::getId)
                .toList();
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getBirthDate(),
                user.getEmail(),
                user.getPhoneNumber(),
                toAddress(user.getAddress()),
                activeId(user.getFather()),
                activeId(user.getMother()),
                childrenIds
        );
    }

    private AddressDto toAddress(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDto(
                address.getId(),
                address.getCountry(),
                address.getCity(),
                address.getStreet(),
                address.getBuilding(),
                address.getApartment(),
                address.getPostalCode()
        );
    }

    private static void addActive(Map<Long, User> members, User user) {
        if (user != null && !user.isDeleted()) {
            members.putIfAbsent(user.getId(), user);
        }
    }

    private static Long activeId(User user) {
        if (user == null || user.isDeleted()) {
            return null;
        }
        return user.getId();
    }

    private static boolean sameParent(Long fatherId, Long motherId) {
        return fatherId != null && fatherId.equals(motherId);
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String canonical = phone.replaceAll("[\\s().-]", "");
        if (!canonical.matches(PHONE_PATTERN)) {
            throw new InvalidPhoneException("Phone number cannot be normalized to E.164");
        }
        return canonical;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }
}
