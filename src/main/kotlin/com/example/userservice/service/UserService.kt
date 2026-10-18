package com.example.userservice.service

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.api.dto.UserResponse
import com.example.userservice.api.exception.BadParentageException
import com.example.userservice.api.exception.ConflictException
import com.example.userservice.api.exception.UserNotFoundException
import com.example.userservice.domain.entity.User
import com.example.userservice.domain.repository.UserRepository
import com.example.userservice.util.NormalizationUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class UserService(
    private val userRepository: UserRepository,
    private val addressService: AddressService
) {

    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        val (normalizedEmail, normalizedPhone) = validateAndNormalizeCredentials(
            email = request.email,
            phone = request.phoneNumber
        )

        val (father, mother) = validateParentage(
            userId = null,
            fatherId = request.fatherId,
            motherId = request.motherId
        )

        val resolvedAddress = addressService.resolveAddress(request.address)

        val user = User(
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            birthDate = request.birthDate,
            email = normalizedEmail,
            phoneNumber = normalizedPhone,
            address = resolvedAddress,
            father = father,
            mother = mother,
            isDeleted = false
        )

        userRepository.persist(user)
        return toResponse(user)
    }

    fun getUserById(id: Long): UserResponse {
        val user = userRepository.findActiveById(id)
            ?: throw UserNotFoundException("User with ID $id not found")
        return toResponse(user)
    }

    fun getUserByEmail(email: String): UserResponse {
        val normalized = NormalizationUtils.normalizeEmail(email)
        val user = userRepository.findActiveByEmail(normalized)
            ?: throw UserNotFoundException("User with email $normalized not found")
        return toResponse(user)
    }

    @Transactional
    fun updateUser(id: Long, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findActiveById(id)
            ?: throw UserNotFoundException("User with ID $id not found")

        val (normalizedEmail, normalizedPhone) = validateAndNormalizeCredentials(
            email = request.email,
            phone = request.phoneNumber,
            excludeUserId = id
        )

        val (father, mother) = validateParentage(
            userId = id,
            fatherId = request.fatherId,
            motherId = request.motherId
        )

        val resolvedAddress = addressService.resolveAddress(request.address)

        user.firstName = request.firstName.trim()
        user.lastName = request.lastName.trim()
        user.birthDate = request.birthDate
        user.email = normalizedEmail
        user.phoneNumber = normalizedPhone
        user.address = resolvedAddress
        user.father = father
        user.mother = mother

        return toResponse(user)
    }

    @Transactional
    fun deleteUser(id: Long) {
        val user = userRepository.findActiveById(id)
            ?: throw UserNotFoundException("User with ID $id not found")
        user.isDeleted = true
    }

    fun getDirectFamily(id: Long): List<UserResponse> {
        val target = userRepository.findById(id)
            ?: throw UserNotFoundException("User with ID $id not found")

        val result = mutableListOf<UserResponse>()

        if (!target.isDeleted) {
            result.add(toResponse(target))
        }

        val father = target.father
        if (father != null && !father.isDeleted) {
            result.add(toResponse(father))
        }

        val mother = target.mother
        if (mother != null && !mother.isDeleted) {
            result.add(toResponse(mother))
        }

        val children = userRepository.findActiveChildren(id)
        for (child in children) {
            result.add(toResponse(child))
        }

        return result
    }

    fun validateAndNormalizeCredentials(
        email: String,
        phone: String?,
        excludeUserId: Long? = null
    ): Pair<String, String?> {
        val normalizedEmail = NormalizationUtils.normalizeEmail(email)
        val normalizedPhone = NormalizationUtils.normalizePhone(phone)

        val existingByEmail = userRepository.findActiveByEmail(normalizedEmail)
        if (existingByEmail != null && existingByEmail.id != excludeUserId) {
            throw ConflictException("User with email '$normalizedEmail' already exists")
        }

        if (normalizedPhone != null) {
            val existingByPhone = userRepository.findActiveByPhone(normalizedPhone)
            if (existingByPhone != null && existingByPhone.id != excludeUserId) {
                throw ConflictException("User with phone '$normalizedPhone' already exists")
            }
        }

        return Pair(normalizedEmail, normalizedPhone)
    }

    fun validateParentage(
        userId: Long?,
        fatherId: Long?,
        motherId: Long?
    ): Pair<User?, User?> {
        if (fatherId != null && motherId != null && fatherId == motherId) {
            throw BadParentageException("Father and mother cannot be the same user ($fatherId)")
        }

        if (userId != null) {
            if (fatherId == userId || motherId == userId) {
                throw BadParentageException("User cannot be their own parent (userId: $userId)")
            }
        }

        val father = if (fatherId != null) {
            val f = userRepository.findActiveById(fatherId)
                ?: throw UserNotFoundException("Father with ID $fatherId not found or deleted")
            if (userId != null && isAncestorOf(potentialAncestorId = userId, targetUser = f)) {
                throw BadParentageException("Assigning user $userId as child of $fatherId creates an ancestry cycle")
            }
            f
        } else null

        val mother = if (motherId != null) {
            val m = userRepository.findActiveById(motherId)
                ?: throw UserNotFoundException("Mother with ID $motherId not found or deleted")
            if (userId != null && isAncestorOf(potentialAncestorId = userId, targetUser = m)) {
                throw BadParentageException("Assigning user $userId as child of $motherId creates an ancestry cycle")
            }
            m
        } else null

        return Pair(father, mother)
    }

    private fun isAncestorOf(potentialAncestorId: Long, targetUser: User?): Boolean {
        if (targetUser == null) return false
        if (targetUser.id == potentialAncestorId) return true

        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<User>()
        queue.add(targetUser)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentId = current.id ?: continue
            if (currentId == potentialAncestorId) return true

            if (visited.add(currentId)) {
                current.father?.let { queue.add(it) }
                current.mother?.let { queue.add(it) }
            }
        }

        return false
    }

    fun toResponse(user: User): UserResponse {
        val addressDto = user.address?.let {
            AddressDto(
                id = it.id,
                country = it.country,
                city = it.city,
                street = it.street,
                building = it.building,
                apartment = it.apartment,
                postalCode = it.postalCode
            )
        }

        val fatherId = if (user.father != null && !user.father!!.isDeleted) user.father!!.id else null
        val motherId = if (user.mother != null && !user.mother!!.isDeleted) user.mother!!.id else null

        val activeChildrenIds = user.id?.let {
            userRepository.findActiveChildren(it).mapNotNull { c -> c.id }
        } ?: emptyList()

        return UserResponse(
            id = user.id ?: 0L,
            firstName = user.firstName,
            lastName = user.lastName,
            birthDate = user.birthDate,
            email = user.email,
            phoneNumber = user.phoneNumber,
            address = addressDto,
            fatherId = fatherId,
            motherId = motherId,
            childrenIds = activeChildrenIds
        )
    }
}
